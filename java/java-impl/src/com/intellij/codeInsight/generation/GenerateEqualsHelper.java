/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.generation;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.java.generate.GenerationUtil;
import org.jetbrains.java.generate.template.TemplateResource;

import java.util.*;

/**
 * @author dsl
 */
public class GenerateEqualsHelper implements Runnable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.GenerateEqualsHelper");

  @NonNls private static final String INSTANCE_NAME = "instanceBaseName";
  @NonNls private static final String BASE_PARAM_NAME = "baseParamName";
  @NonNls private static final String SUPER_PARAM_NAME = "superParamName";
  @NonNls private static final String SUPER_HAS_EQUALS = "superHasEquals";
  @NonNls private static final String CHECK_PARAMETER_WITH_INSTANCEOF = "checkParameterWithInstanceof";
  @NonNls private static final String SUPER_HAS_HASH_CODE = "superHasHashCode";

  private final PsiClass myClass;
  private final PsiField[] myEqualsFields;
  private final PsiField[] myHashCodeFields;
  private final HashSet<PsiField> myNonNullSet;
  private final PsiElementFactory myFactory;
  private final boolean mySuperHasHashCode;
  private final CodeStyleManager myCodeStyleManager;
  private final JavaCodeStyleManager myJavaCodeStyleManager;
  private final Project myProject;
  private final boolean myCheckParameterWithInstanceof;
  private final boolean myUseAccessors;

  public GenerateEqualsHelper(Project project,
                            PsiClass aClass,
                            PsiField[] equalsFields,
                            PsiField[] hashCodeFields,
                            PsiField[] nonNullFields,
                            boolean useInstanceofToCheckParameterType) {
    this(project, aClass, equalsFields, hashCodeFields, nonNullFields, useInstanceofToCheckParameterType, false);
  }

  public GenerateEqualsHelper(Project project,
                              PsiClass aClass,
                              PsiField[] equalsFields,
                              PsiField[] hashCodeFields,
                              PsiField[] nonNullFields,
                              boolean useInstanceofToCheckParameterType,
                              boolean useAccessors) {
    myClass = aClass;
    myEqualsFields = equalsFields;
    myHashCodeFields = hashCodeFields;
    myProject = project;
    myCheckParameterWithInstanceof = useInstanceofToCheckParameterType;
    myUseAccessors = useAccessors;

    myNonNullSet = new HashSet<>();
    ContainerUtil.addAll(myNonNullSet, nonNullFields);
    final PsiManager manager = PsiManager.getInstance(project);

    myFactory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();

    mySuperHasHashCode = superMethodExists(getHashCodeSignature());
    myCodeStyleManager = CodeStyleManager.getInstance(manager.getProject());
    myJavaCodeStyleManager = JavaCodeStyleManager.getInstance(manager.getProject());
  }

  private static boolean shouldAddOverrideAnnotation(PsiElement context) {
    JavaCodeStyleSettings style = CodeStyleSettingsManager.getSettings(context.getProject()).getCustomSettings(JavaCodeStyleSettings.class);

    return style.INSERT_OVERRIDE_ANNOTATION && PsiUtil.isLanguageLevel5OrHigher(context);
  }

  @Override
  public void run() {
    try {
      final Collection<PsiMethod> members = generateMembers();
      for (PsiElement member : members) {
        myClass.add(member);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public Collection<PsiMethod> generateMembers() throws IncorrectOperationException {
    PsiMethod equals = null;
    if (myEqualsFields != null && GenerateEqualsHandler.needToGenerateMethod(findMethod(myClass, getEqualsSignature(myProject, myClass.getResolveScope())))) {
      equals = createEquals();
    }

    PsiMethod hashCode = null;
    if (myHashCodeFields != null && GenerateEqualsHandler.needToGenerateMethod(findMethod(myClass, getHashCodeSignature()))) {
      if (myHashCodeFields.length > 0) {
        hashCode = createHashCode();
      }
      else {
        if (!mySuperHasHashCode) {
          @NonNls String text = "";
          if (shouldAddOverrideAnnotation(myClass)) {
            text += "@Override\n";
          }

          text += "public int hashCode() {\nreturn 0;\n}";
          final PsiMethod trivialHashCode = myFactory.createMethodFromText(text, null);
          hashCode = (PsiMethod)myCodeStyleManager.reformat(trivialHashCode);
        }
      }
    }
    if (hashCode != null && equals != null) {
      return Arrays.asList(equals, hashCode);
    }
    else if (equals != null) {
      return Collections.singletonList(equals);
    }
    else if (hashCode != null) {
      return Collections.singletonList(hashCode);
    }
    else {
      return Collections.emptyList();
    }
  }

  public static Map<String, PsiType> getEqualsImplicitVars(Project project) {
    final Map<String, PsiType> map = new LinkedHashMap<>();
    final PsiType stringType = project != null ? PsiType.getJavaLangString(PsiManager.getInstance(project), GlobalSearchScope.allScope(project))
                                               : PsiType.NULL;
    map.put(INSTANCE_NAME, stringType);
    map.put(BASE_PARAM_NAME, stringType);
    map.put(SUPER_PARAM_NAME, stringType);
    map.put(CHECK_PARAMETER_WITH_INSTANCEOF, PsiType.BOOLEAN);
    map.put(SUPER_HAS_EQUALS, PsiType.BOOLEAN);
    return map;
  }

  public static Map<String, PsiType> getHashCodeImplicitVars() {
    final Map<String, PsiType> map = new LinkedHashMap<>();
    map.put(SUPER_HAS_HASH_CODE, PsiType.BOOLEAN);
    return map;
  }
  
  private PsiMethod createEquals() throws IncorrectOperationException {
    @NonNls StringBuilder buffer = new StringBuilder();
    JavaCodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(myProject).getCustomSettings(JavaCodeStyleSettings.class);
    ArrayList<PsiField> equalsFields = new ArrayList<>();
    ContainerUtil.addAll(equalsFields, myEqualsFields);
    Collections.sort(equalsFields, EqualsFieldsComparator.INSTANCE);

    final HashMap<String, Object> contextMap = new HashMap<>();

    final PsiType classType = JavaPsiFacade.getElementFactory(myClass.getProject()).createType(myClass);
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(myClass.getProject());
    String[] nameSuggestions = codeStyleManager
      .suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, classType).names;
    String instanceBaseName = nameSuggestions.length > 0 && nameSuggestions[0].length() < 10 ? nameSuggestions[0] : "that";
    contextMap.put(INSTANCE_NAME, instanceBaseName);

    final PsiType objectType = PsiType.getJavaLangObject(myClass.getManager(), myClass.getResolveScope());
    nameSuggestions = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, null, objectType).names;
    final String objectBaseName = nameSuggestions.length > 0 ? nameSuggestions[0] : "object";
    contextMap.put(BASE_PARAM_NAME, objectBaseName);
    final MethodSignature equalsSignature = getEqualsSignature(myProject, myClass.getResolveScope());

    PsiMethod superEquals = MethodSignatureUtil.findMethodBySignature(myClass, equalsSignature, true);
    if (superEquals != null) {
      contextMap.put(SUPER_PARAM_NAME, superEquals.getParameterList().getParameters()[0].getName());
    }
    
    contextMap.put(SUPER_HAS_EQUALS, superMethodExists(equalsSignature));
    contextMap.put(CHECK_PARAMETER_WITH_INSTANCEOF, myCheckParameterWithInstanceof);

    final String methodText = GenerationUtil
      .velocityGenerateCode(myClass, equalsFields, myNonNullSet, new HashMap<>(), contextMap,
                            EqualsHashCodeTemplatesManager.getInstance().getDefaultEqualsTemplate().getTemplate(), 0, false, myUseAccessors);
    buffer.append(methodText);
    PsiMethod result;
    try {
      result = myFactory.createMethodFromText(buffer.toString(), myClass);
    }
    catch (IncorrectOperationException e) {
      return null;
    }
    final PsiParameter[] parameters = result.getParameterList().getParameters();
    if (parameters.length != 1) return null;
    final PsiParameter parameter = parameters[0];
    PsiUtil.setModifierProperty(parameter, PsiModifier.FINAL, styleSettings.GENERATE_FINAL_PARAMETERS);

    PsiMethod method = (PsiMethod)myCodeStyleManager.reformat(result);
    if (superEquals != null) {
      OverrideImplementUtil.annotateOnOverrideImplement(method, myClass, superEquals);
    }
    method = (PsiMethod)myJavaCodeStyleManager.shortenClassReferences(method);
    return method;
  }

  private boolean superMethodExists(MethodSignature methodSignature) {
    LOG.assertTrue(myClass.isValid());
    PsiMethod superEquals = MethodSignatureUtil.findMethodBySignature(myClass, methodSignature, true);
    if (superEquals == null) return true;
    if (superEquals.hasModifierProperty(PsiModifier.ABSTRACT)) return false;
    return !CommonClassNames.JAVA_LANG_OBJECT.equals(superEquals.getContainingClass().getQualifiedName());
  }

  private PsiMethod createHashCode() throws IncorrectOperationException {
    @NonNls StringBuilder buffer = new StringBuilder();

    final HashMap<String, Object> contextMap = new HashMap<>();
    contextMap.put(SUPER_HAS_HASH_CODE, mySuperHasHashCode);

    final String methodText = GenerationUtil
      .velocityGenerateCode(myClass, Arrays.asList(myHashCodeFields), myNonNullSet, new HashMap<>(), contextMap,
                            EqualsHashCodeTemplatesManager.getInstance().getDefaultHashcodeTemplate().getTemplate(), 0, false, myUseAccessors);
    buffer.append(methodText);
    PsiMethod hashCode;
    try {
      hashCode = myFactory.createMethodFromText(buffer.toString(), null);
    }
    catch (IncorrectOperationException e) {
      return null;
    }
    final PsiMethod superHashCode = MethodSignatureUtil.findMethodBySignature(myClass, getHashCodeSignature(), true);
    if (superHashCode != null) {
      OverrideImplementUtil.annotateOnOverrideImplement(hashCode, myClass, superHashCode);
    }
    hashCode = (PsiMethod)myJavaCodeStyleManager.shortenClassReferences(hashCode);
    return (PsiMethod)myCodeStyleManager.reformat(hashCode);
  }


  public void invoke() {
    ApplicationManager.getApplication().runWriteAction(this);
  }

  static PsiMethod findMethod(PsiClass aClass, MethodSignature signature) {
    return MethodSignatureUtil.findMethodBySignature(aClass, signature, false);
  }

  public void executeWithDefaultTemplateWhenNotApplicable() {
    EqualsHashCodeTemplatesManager manager = EqualsHashCodeTemplatesManager.getInstance();
    String baseName = manager.getDefaultTemplateBaseName();
    try {
      TemplateResource defaultTemplate = manager.getDefaultTemplate();
      String className = defaultTemplate.getClassName();
      if (className != null) {
        PsiClass usedClass = JavaPsiFacade.getInstance(myClass.getProject()).findClass(className, myClass.getResolveScope());
        if (usedClass == null || PsiUtil.getLanguageLevel(myClass).isLessThan(PsiUtil.getLanguageLevel(usedClass))) {
          manager.setDefaultTemplate(EqualsHashCodeTemplatesManager.INTELLI_J_DEFAULT);
        }
      }
      run();
    }
    finally {
      manager.setDefaultTemplate(baseName);
    }
  }

  static class EqualsFieldsComparator implements Comparator<PsiField> {
    public static final EqualsFieldsComparator INSTANCE = new EqualsFieldsComparator();

    @Override
    public int compare(PsiField f1, PsiField f2) {
      if (f1.getType() instanceof PsiPrimitiveType && !(f2.getType() instanceof PsiPrimitiveType)) return -1;
      if (!(f1.getType() instanceof PsiPrimitiveType) && f2.getType() instanceof PsiPrimitiveType) return 1;
      return PsiUtilCore.compareElementsByPosition(f1, f2);
    }
  }

  public static boolean isArrayOfObjects(PsiType aType) {
    if (!(aType instanceof PsiArrayType)) return false;
    final PsiType componentType = ((PsiArrayType)aType).getComponentType();
    final PsiClass psiClass = PsiUtil.resolveClassInType(componentType);
    if (psiClass == null) return false;
    final String qName = psiClass.getQualifiedName();
    return CommonClassNames.JAVA_LANG_OBJECT.equals(qName);
  }

  public static MethodSignature getHashCodeSignature() {
    return MethodSignatureUtil.createMethodSignature("hashCode", PsiType.EMPTY_ARRAY, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
  }

  public static MethodSignature getEqualsSignature(Project project, GlobalSearchScope scope) {
    final PsiClassType javaLangObject = PsiType.getJavaLangObject(PsiManager.getInstance(project), scope);
    return MethodSignatureUtil
      .createMethodSignature("equals", new PsiType[]{javaLangObject}, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
  }
}
