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
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
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
    ArrayList<ClassMember> array = new ArrayList<>();
    ImplicitUsageProvider[] implicitUsageProviders = Extensions.getExtensions(ImplicitUsageProvider.EP_NAME);
    fieldLoop:
    for (PsiField field : fields) {
      if (field.hasModifierProperty(PsiModifier.STATIC)) continue;

      if (field.hasModifierProperty(PsiModifier.FINAL) && field.getInitializer() != null) continue;

      for (ImplicitUsageProvider provider : implicitUsageProviders) {
        if (provider.isImplicitWrite(field)) continue fieldLoop;
      }
      array.add(new PsiFieldMember(field));
    }
    return array.toArray(new ClassMember[array.size()]);
  }

  @Override
  @Nullable
  protected ClassMember[] chooseOriginalMembers(PsiClass aClass, Project project) {
    if (aClass instanceof PsiAnonymousClass) {
      Messages.showMessageDialog(project,
                                 CodeInsightBundle.message("error.attempt.to.generate.constructor.for.anonymous.class"),
                                 CommonBundle.getErrorTitle(),
                                 Messages.getErrorIcon());
      return null;
    }

    myCopyJavadoc = false;
    PsiMethod[] baseConstructors = null;
    PsiClass baseClass = aClass.getSuperClass();
    if (baseClass != null) {
      List<PsiMethod> array = new ArrayList<>();
      for (PsiMethod method : baseClass.getConstructors()) {
        if (JavaPsiFacade.getInstance(method.getProject()).getResolveHelper().isAccessible(method, aClass, null)) {
          array.add(method);
        }
      }
      if (!array.isEmpty()) {
        if (array.size() == 1) {
          baseConstructors = new PsiMethod[]{array.get(0)};
        }
        else {
          final PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(baseClass, aClass, PsiSubstitutor.EMPTY);
          PsiMethodMember[] constructors = ContainerUtil.map2Array(array, PsiMethodMember.class, s -> new PsiMethodMember(s, substitutor));
          MemberChooser<PsiMethodMember> chooser = new MemberChooser<>(constructors, false, true, project);
          chooser.setTitle(CodeInsightBundle.message("generate.constructor.super.constructor.chooser.title"));
          chooser.show();
          List<PsiMethodMember> elements = chooser.getSelectedElements();
          if (elements == null || elements.isEmpty()) return null;
          baseConstructors = new PsiMethod[elements.size()];
          for (int i = 0; i < elements.size(); i++) {
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
    else {
      members = chooseMembers(allMembers, true, false, project, null);
      if (members == null) return null;
    }

    if (baseConstructors != null) {
      List<ClassMember> array = new ArrayList<>();
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
    final List<ClassMember> preselection = new ArrayList<>();
    for (ClassMember member : members) {
      if (member instanceof PsiFieldMember) {
        final PsiField psiField = ((PsiFieldMember)member).getElement();
        if (psiField.hasModifierProperty(PsiModifier.FINAL)) {
          preselection.add(member);
        }
      }
    }
    return preselection;
  }

  @Override
  @NotNull
  protected List<? extends GenerationInfo> generateMemberPrototypes(PsiClass aClass, ClassMember[] members) throws IncorrectOperationException {
    List<PsiMethod> baseConstructors = new ArrayList<>();
    List<PsiField> fieldsVector = new ArrayList<>();
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
      List<GenerationInfo> constructors = new ArrayList<>(baseConstructors.size());
      final PsiClass superClass = aClass.getSuperClass();
      assert superClass != null;
      PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY);
      for (PsiMethod baseConstructor : baseConstructors) {
        baseConstructor = GenerateMembersUtil.substituteGenericMethod(baseConstructor, substitutor, aClass);
        constructors.add(new PsiGenerationInfo<>(generateConstructorPrototype(aClass, baseConstructor, myCopyJavadoc, fields)));
      }
      List<? extends GenerationInfo> constructorsToCreate = filterOutAlreadyInsertedConstructors(aClass, constructors);
      if (!constructorsToCreate.isEmpty()) {
        //allow to create constructor not matching super
        return constructorsToCreate;
      }
    }
    final List<GenerationInfo> constructors =
      Collections.singletonList(new PsiGenerationInfo<>(generateConstructorPrototype(aClass, null, false, fields)));
    return filterOutAlreadyInsertedConstructors(aClass, constructors);
  }

  private static List<? extends GenerationInfo> filterOutAlreadyInsertedConstructors(PsiClass aClass, List<? extends GenerationInfo> constructors) {
    boolean alreadyExist = true;
    for (GenerationInfo constructor : constructors) {
      PsiMethod existingMethod = aClass.findMethodBySignature((PsiMethod)constructor.getPsiMember(), false);

      alreadyExist &= existingMethod != null && existingMethod.isPhysical();
    }
    if (alreadyExist) {
      return Collections.emptyList();
    }
    return constructors;
  }

  @Override
  protected String getNothingFoundMessage() {
    return "Constructor already exists";
  }

  public static PsiMethod generateConstructorPrototype(PsiClass aClass,
                                                       PsiMethod baseConstructor,
                                                       boolean copyJavaDoc,
                                                       PsiField[] fields) throws IncorrectOperationException {
    PsiManager manager = aClass.getManager();
    Project project = aClass.getProject();
    JVMElementFactory factory = JVMElementFactories.requireFactory(aClass.getLanguage(), project);
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(manager.getProject());

    String className = aClass.getName();
    assert className != null : aClass;
    PsiMethod constructor = factory.createConstructor(className, aClass);

    GenerateMembersUtil.setVisibility(aClass, constructor);

    if (baseConstructor != null) {
      PsiJavaCodeReferenceElement[] throwRefs = baseConstructor.getThrowsList().getReferenceElements();
      for (PsiJavaCodeReferenceElement ref : throwRefs) {
        constructor.getThrowsList().add(ref);
      }

      if (copyJavaDoc) {
        final PsiDocComment docComment = ((PsiMethod)baseConstructor.getNavigationElement()).getDocComment();
        if (docComment != null) {
          constructor.addAfter(docComment, null);
        }
      }
    }

    boolean isNotEnum = false;
    if (baseConstructor != null) {
      PsiClass superClass = aClass.getSuperClass();
      LOG.assertTrue(superClass != null);
      if (!CommonClassNames.JAVA_LANG_ENUM.equals(superClass.getQualifiedName())) {
        isNotEnum = true;
        if (baseConstructor instanceof PsiCompiledElement) { // to get some parameter names
          PsiClass dummyClass = JVMElementFactories.requireFactory(baseConstructor.getLanguage(), project).createClass("Dummy");
          baseConstructor = (PsiMethod)dummyClass.add(baseConstructor);
        }
        PsiParameter[] params = baseConstructor.getParameterList().getParameters();
        for (PsiParameter param : params) {
          String name = param.getName();
          assert name != null : param;
          PsiParameter newParam = factory.createParameter(name, param.getType(), aClass);
          GenerateMembersUtil.copyOrReplaceModifierList(param, aClass, newParam);
          constructor.getParameterList().add(newParam);
        }
      }
    }

    JavaCodeStyleManager javaStyle = JavaCodeStyleManager.getInstance(project);

    final PsiMethod dummyConstructor = factory.createConstructor(className);
    dummyConstructor.getParameterList().replace(constructor.getParameterList().copy());
    List<PsiParameter> fieldParams = new ArrayList<>();
    for (PsiField field : fields) {
      String fieldName = field.getName();
      assert fieldName != null : field;
      String name = javaStyle.variableNameToPropertyName(fieldName, VariableKind.FIELD);
      String parmName = javaStyle.propertyNameToVariableName(name, VariableKind.PARAMETER);
      parmName = javaStyle.suggestUniqueVariableName(parmName, dummyConstructor, true);
      PsiParameter parm = factory.createParameter(parmName, field.getType(), aClass);

      NullableNotNullManager.getInstance(project).copyNullableOrNotNullAnnotation(field, parm);

      if (constructor.isVarArgs()) {
        final PsiParameterList parameterList = constructor.getParameterList();
        parameterList.addBefore(parm, parameterList.getParameters()[parameterList.getParametersCount() - 1]);
        final PsiParameterList dummyParameterList = dummyConstructor.getParameterList();
        dummyParameterList.addBefore(parm.copy(), dummyParameterList.getParameters()[dummyParameterList.getParametersCount() - 1]);
      }
      else {
        constructor.getParameterList().add(parm);
        dummyConstructor.getParameterList().add(parm.copy());
      }

      fieldParams.add(parm);
    }

    ConstructorBodyGenerator generator = ConstructorBodyGenerator.INSTANCE.forLanguage(aClass.getLanguage());
    if (generator != null) {
      StringBuilder buffer = new StringBuilder();
      generator.start(buffer, constructor.getName(), PsiParameter.EMPTY_ARRAY);
      if (isNotEnum) {
        generator.generateSuperCallIfNeeded(buffer, baseConstructor.getParameterList().getParameters());
      }
      final PsiParameter[] parameters = fieldParams.toArray(new PsiParameter[fieldParams.size()]);
      final List<String> existingNames = ContainerUtil.map(dummyConstructor.getParameterList().getParameters(), parameter -> parameter.getName());
      if (generator instanceof ConstructorBodyGeneratorEx) {
        ((ConstructorBodyGeneratorEx)generator).generateFieldInitialization(buffer, fields, parameters, existingNames);
      }
      else {
        generator.generateFieldInitialization(buffer, fields, parameters);
      }
      generator.finish(buffer);
      PsiMethod stub = factory.createMethodFromText(buffer.toString(), aClass);
      PsiCodeBlock original = constructor.getBody(), replacement = stub.getBody();
      assert original != null : constructor;
      assert replacement != null : stub;
      original.replace(replacement);
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