/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.CommonBundle;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GenerateConstructorHandler extends GenerateMembersHandlerBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.GenerateConstructorHandler");
  private boolean myCopyJavadoc;

  public GenerateConstructorHandler() {
    super(CodeInsightBundle.message("generate.constructor.fields.chooser.title"));
  }

  @Override
  protected ClassMember[] getAllOriginalMembers(PsiClass aClass) {
    PsiField[] fields = aClass.getFields();
    ArrayList<ClassMember> array = new ArrayList<ClassMember>();
    ImplicitUsageProvider[] implicitUsageProviders = Extensions.getExtensions(ImplicitUsageProvider.EP_NAME);
    fieldLoop: for (PsiField field : fields) {
      if (field.hasModifierProperty(PsiModifier.STATIC)) continue;

      if (field.hasModifierProperty(PsiModifier.FINAL) && field.getInitializer() != null) continue;

      for(ImplicitUsageProvider provider: implicitUsageProviders) {
        if (provider.isImplicitWrite(field)) continue fieldLoop;
      }
      array.add(new PsiFieldMember(field));
    }
    return array.toArray(new ClassMember[array.size()]);
  }

  @Override
  @Nullable
  protected ClassMember[] chooseOriginalMembers(PsiClass aClass, Project project) {
    if (aClass instanceof PsiAnonymousClass){
      Messages.showMessageDialog(project,
                                 CodeInsightBundle.message("error.attempt.to.generate.constructor.for.anonymous.class"),
                                 CommonBundle.getErrorTitle(),
                                 Messages.getErrorIcon());
      return null;
    }

    myCopyJavadoc = false;
    PsiMethod[] baseConstructors = null;
    PsiClass baseClass = aClass.getSuperClass();
    if (baseClass != null){
      ArrayList<PsiMethod> array = new ArrayList<PsiMethod>();
      for (PsiMethod method : baseClass.getConstructors()) {
        if (JavaPsiFacade.getInstance(method.getProject()).getResolveHelper().isAccessible(method, aClass, null)) {
          array.add(method);
        }
      }
      if (!array.isEmpty()){
        if (array.size() == 1){
          baseConstructors = new PsiMethod[]{array.get(0)};
        }
        else{
          final PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(baseClass, aClass, PsiSubstitutor.EMPTY);
          PsiMethodMember[] constructors = ContainerUtil.map2Array(array, PsiMethodMember.class, new Function<PsiMethod, PsiMethodMember>() {
            @Override
            public PsiMethodMember fun(final PsiMethod s) {
              return new PsiMethodMember(s, substitutor);
            }
          });
          MemberChooser<PsiMethodMember> chooser = new MemberChooser<PsiMethodMember>(constructors, false, true, project);
          chooser.setTitle(CodeInsightBundle.message("generate.constructor.super.constructor.chooser.title"));
          chooser.show();
          List<PsiMethodMember> elements = chooser.getSelectedElements();
          if (elements == null || elements.isEmpty()) return null;
          baseConstructors = new PsiMethod[elements.size()];
          for(int i = 0; i < elements.size(); i++){
            final ClassMember member = elements.get(i);
            baseConstructors[i] = ((PsiMethodMember)member).getElement();
          }
          myCopyJavadoc = chooser.isCopyJavadoc();
        }
      }
    }

    ClassMember[] allMembers = getAllOriginalMembers(aClass);
    ClassMember[] members;
    if (allMembers.length == 0) {
      members = ClassMember.EMPTY_ARRAY;
    }
    else{
      members = chooseMembers(allMembers, true, false, project, null);
      if (members == null) return null;
    }
    if (baseConstructors != null) {
      ArrayList<ClassMember> array = new ArrayList<ClassMember>();
      for (PsiMethod baseConstructor : baseConstructors) {
        array.add(new PsiMethodMember(baseConstructor));
      }
      ContainerUtil.addAll(array, members);
      members = array.toArray(new ClassMember[array.size()]);
    }

    return members;
  }

  @Override
  protected MemberChooser<ClassMember> createMembersChooser(ClassMember[] members,
                                                            boolean allowEmptySelection,
                                                            boolean copyJavadocCheckbox,
                                                            Project project) {
    final MemberChooser<ClassMember> chooser = super.createMembersChooser(members, allowEmptySelection, copyJavadocCheckbox, project);
    final List<ClassMember> preselection = preselect(members);
    if (!preselection.isEmpty()) {
      chooser.selectElements(preselection.toArray(new ClassMember[preselection.size()]));
    }
    return chooser;
  }

  protected static List<ClassMember> preselect(ClassMember[] members) {
    final List<ClassMember> preselection = new ArrayList<ClassMember>();
    for (ClassMember member : members) {
      if (member instanceof PsiFieldMember) {
        final PsiField psiField = ((PsiFieldMember)member).getElement();
        if (psiField != null && psiField.hasModifierProperty(PsiModifier.FINAL)) {
          preselection.add(member);
        }
      }
    }
    return preselection;
  }

  @Override
  @NotNull
  protected List<? extends GenerationInfo> generateMemberPrototypes(PsiClass aClass, ClassMember[] members) throws IncorrectOperationException {
    List<PsiMethod> baseConstructors = new ArrayList<PsiMethod>();
    List<PsiField> fieldsVector = new ArrayList<PsiField>();
    for (ClassMember member1 : members) {
      PsiElement member = ((PsiElementClassMember)member1).getElement();
      if (member instanceof PsiMethod) {
        baseConstructors.add((PsiMethod)member);
      }
      else {
        fieldsVector.add((PsiField)member);
      }
    }
    PsiField[] fields = fieldsVector.toArray(new PsiField[fieldsVector.size()]);

    if (!baseConstructors.isEmpty()) {
      List<GenerationInfo> constructors = new ArrayList<GenerationInfo>(baseConstructors.size());
      final PsiClass superClass = aClass.getSuperClass();
      assert superClass != null;
      PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY);
      for (PsiMethod baseConstructor : baseConstructors) {
        baseConstructor = GenerateMembersUtil.substituteGenericMethod(baseConstructor, substitutor, aClass);
        constructors.add(new PsiGenerationInfo(generateConstructorPrototype(aClass, baseConstructor, myCopyJavadoc, fields)));
      }
      return filterOutAlreadyInsertedConstructors(aClass, constructors);
    }
    final List<GenerationInfo> constructors =
      Collections.<GenerationInfo>singletonList(new PsiGenerationInfo(generateConstructorPrototype(aClass, null, false, fields)));
    return filterOutAlreadyInsertedConstructors(aClass, constructors);
  }

  private static List<? extends GenerationInfo> filterOutAlreadyInsertedConstructors(PsiClass aClass, List<? extends GenerationInfo> constructors) {
    boolean alreadyExist = true;
    for (GenerationInfo constructor : constructors) {
      alreadyExist &= aClass.findMethodBySignature((PsiMethod)constructor.getPsiMember(), false) != null;
    }
    if (alreadyExist) {
      return Collections.emptyList();
    }
    return constructors;
  }

  @Override
  protected String getNothingFoundMessage() {
    return "Constructor already exist";
  }

  public static PsiMethod generateConstructorPrototype(PsiClass aClass, PsiMethod baseConstructor, boolean copyJavaDoc, PsiField[] fields) throws IncorrectOperationException {
    PsiManager manager = aClass.getManager();
    JVMElementFactory factory = JVMElementFactories.requireFactory(aClass.getLanguage(), aClass.getProject());
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(manager.getProject());

    PsiMethod constructor = factory.createConstructor(aClass.getName(), aClass);
    String modifier = PsiUtil.getMaximumModifierForMember(aClass, false);

    if (modifier != null) {
      PsiUtil.setModifierProperty(constructor, modifier, true);
    }

    if (baseConstructor != null){
      PsiJavaCodeReferenceElement[] throwRefs = baseConstructor.getThrowsList().getReferenceElements();
      for (PsiJavaCodeReferenceElement ref : throwRefs) {
        constructor.getThrowsList().add(ref);
      }

      if(copyJavaDoc) {
        final PsiDocComment docComment = ((PsiMethod)baseConstructor.getNavigationElement()).getDocComment();
        if(docComment != null) {
          constructor.addAfter(docComment, null);
        }
      }
    }

    boolean isNotEnum = false;
    if (baseConstructor != null){
      PsiClass superClass = aClass.getSuperClass();
      LOG.assertTrue(superClass != null);
      if (!CommonClassNames.JAVA_LANG_ENUM.equals(superClass.getQualifiedName())) {
        isNotEnum = true;
        if (baseConstructor instanceof PsiCompiledElement){ // to get some parameter names
          PsiClass dummyClass = JVMElementFactories.requireFactory(baseConstructor.getLanguage(), baseConstructor.getProject()).createClass("Dummy");
          baseConstructor = (PsiMethod)dummyClass.add(baseConstructor);
        }
        PsiParameter[] params = baseConstructor.getParameterList().getParameters();
        for (PsiParameter param : params) {
          PsiParameter newParam = factory.createParameter(param.getName(), param.getType(), aClass);
          GenerateMembersUtil.copyOrReplaceModifierList(param, newParam);
          constructor.getParameterList().add(newParam);
        }
      }
    }

    JavaCodeStyleManager javaStyle = JavaCodeStyleManager.getInstance(aClass.getProject());

    final PsiMethod dummyConstructor = factory.createConstructor(aClass.getName());
    dummyConstructor.getParameterList().replace(constructor.getParameterList().copy());
    List<PsiParameter> fieldParams = new ArrayList<PsiParameter>();
    for (PsiField field : fields) {
      String fieldName = field.getName();
      String name = javaStyle.variableNameToPropertyName(fieldName, VariableKind.FIELD);
      String parmName = javaStyle.propertyNameToVariableName(name, VariableKind.PARAMETER);
      parmName = javaStyle.suggestUniqueVariableName(parmName, dummyConstructor, true);
      PsiParameter parm = factory.createParameter(parmName, field.getType(), aClass);

      final NullableNotNullManager nullableManager = NullableNotNullManager.getInstance(field.getProject());
      final String notNull = nullableManager.getNotNull(field);
      if (notNull != null) {
        parm.getModifierList().addAfter(factory.createAnnotationFromText("@" + notNull, field), null);
      }

      constructor.getParameterList().add(parm);
      dummyConstructor.getParameterList().add(parm.copy());
      fieldParams.add(parm);
    }

    ConstructorBodyGenerator generator = ConstructorBodyGenerator.INSTANCE.forLanguage(aClass.getLanguage());
    if (generator != null) {
      @NonNls StringBuilder buffer = new StringBuilder();
      generator.start(buffer, constructor.getName(), PsiParameter.EMPTY_ARRAY);
      if (isNotEnum) {
        generator.generateSuperCallIfNeeded(buffer, baseConstructor.getParameterList().getParameters());
      }
      generator.generateFieldInitialization(buffer, fields, fieldParams.toArray(new PsiParameter[fieldParams.size()]));
      generator.finish(buffer);
      PsiMethod stub = factory.createMethodFromText(buffer.toString(), aClass);
      constructor.getBody().replace(stub.getBody());
    }

    constructor = (PsiMethod)codeStyleManager.reformat(constructor);
    return constructor;
  }

  @Override
  protected GenerationInfo[] generateMemberPrototypes(PsiClass aClass, ClassMember originalMember) {
    LOG.assertTrue(false);
    return null;
  }
}
