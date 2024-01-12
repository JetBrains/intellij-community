// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.ide.util.MemberChooser;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.light.LightRecordCanonicalConstructor;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GenerateConstructorHandler extends GenerateMembersHandlerBase {
  private static final Logger LOG = Logger.getInstance(GenerateConstructorHandler.class);

  private boolean myCopyJavadoc;

  public GenerateConstructorHandler() {
    super(JavaBundle.message("generate.constructor.fields.chooser.title"));
  }

  @Override
  protected ClassMember[] getAllOriginalMembers(PsiClass aClass) {
    PsiField[] fields = aClass.getFields();
    ArrayList<ClassMember> array = new ArrayList<>();
    for (PsiField field : fields) {
      if (field.hasModifierProperty(PsiModifier.STATIC)) continue;
      if (field.hasModifierProperty(PsiModifier.FINAL) && field.getInitializer() != null) continue;

      array.add(new PsiFieldMember(field));
    }
    return array.toArray(ClassMember.EMPTY_ARRAY);
  }

  @Override
  protected ClassMember @Nullable [] chooseOriginalMembers(PsiClass aClass, Project project) {
    if (aClass instanceof PsiAnonymousClass) {
      Messages.showMessageDialog(project,
                                 JavaBundle.message("error.attempt.to.generate.constructor.for.anonymous.class"),
                                 CommonBundle.getErrorTitle(),
                                 Messages.getErrorIcon());
      return null;
    }

    if (aClass instanceof PsiImplicitClass) {
      Messages.showMessageDialog(project,
                                 JavaBundle.message("error.attempt.to.generate.constructor.for.anonymous.class"),
                                 CommonBundle.getErrorTitle(),
                                 Messages.getErrorIcon());
      return null;
    }

    if (aClass.isRecord()) {
      PsiMethod constructor = JavaPsiRecordUtil.findCanonicalConstructor(aClass);
      if (constructor instanceof LightRecordCanonicalConstructor) {
        RecordConstructorChooserDialog dialog = new RecordConstructorChooserDialog(aClass);
        if (!dialog.showAndGet()) return null;
        ClassMember member = dialog.getClassMember();
        if (member != null) {
          return new ClassMember[]{member};
        }
      }
    }

    myCopyJavadoc = false;
    PsiMethod[] baseConstructors = DumbService.getInstance(project).computeWithAlternativeResolveEnabled(
      () -> {
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
              return new PsiMethod[]{array.get(0)};
            }
            else {
              final PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(baseClass, aClass, PsiSubstitutor.EMPTY);
              PsiMethodMember[] constructors =
                ContainerUtil.map2Array(array, PsiMethodMember.class, s -> new PsiMethodMember(s, substitutor));
              MemberChooser<PsiMethodMember> chooser = new MemberChooser<>(constructors, false, true, project);
              chooser.setTitle(JavaBundle.message("generate.constructor.super.constructor.chooser.title"));
              chooser.show();
              List<PsiMethodMember> elements = chooser.getSelectedElements();
              if (elements == null || elements.isEmpty()) return null;
              PsiMethod[] members = new PsiMethod[elements.size()];
              for (int i = 0; i < elements.size(); i++) {
                final PsiMethodMember member = elements.get(i);
                members[i] = member.getElement();
              }
              myCopyJavadoc = chooser.isCopyJavadoc();
              return members;
            }
          }
        }
        return null;
      });
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
      members = array.toArray(ClassMember.EMPTY_ARRAY);
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
      chooser.selectElements(preselection.toArray(ClassMember.EMPTY_ARRAY));
    }
    return chooser;
  }

  protected static List<ClassMember> preselect(ClassMember[] members) {
    List<ImplicitUsageProvider> implicitUsageProviders = ImplicitUsageProvider.EP_NAME.getExtensionList();
    final List<ClassMember> preselection = new ArrayList<>();

    fieldLoop:
    for (ClassMember member : members) {
      if (member instanceof PsiFieldMember) {
        final PsiField psiField = ((PsiFieldMember)member).getElement();
        if (!psiField.hasModifierProperty(PsiModifier.FINAL)) {
          continue fieldLoop;
        }
        for (ImplicitUsageProvider provider : implicitUsageProviders) {
          if (provider.isImplicitWrite(psiField)) continue fieldLoop;
        }
        preselection.add(member);
      }
    }
    return preselection;
  }

  @Override
  @NotNull
  protected List<? extends GenerationInfo> generateMemberPrototypes(PsiClass aClass, ClassMember[] members) throws IncorrectOperationException {
    if (members.length == 1 && members[0] instanceof RecordConstructorMember) {
      return Collections.singletonList(new PsiGenerationInfo<>(((RecordConstructorMember)members[0]).generateRecordConstructor()));
    }

    List<PsiMethod> baseConstructors = new ArrayList<>();
    List<PsiField> fieldsVector = new ArrayList<>();
    for (ClassMember member1 : members) {
      PsiElement member = ((PsiElementClassMember<?>)member1).getElement();
      if (member instanceof PsiMethod) {
        baseConstructors.add((PsiMethod)member);
      }
      else {
        fieldsVector.add((PsiField)member);
      }
    }
    PsiField[] fields = fieldsVector.toArray(PsiField.EMPTY_ARRAY);

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
    return JavaBundle.message("generate.constructor.already.exists");
  }

  public static PsiMethod generateConstructorPrototype(@NotNull PsiClass aClass,
                                                       PsiMethod baseConstructor,
                                                       boolean copyJavaDoc,
                                                       PsiField[] fields) {
    if (aClass.isRecord()) {
      PsiField[] classFields = aClass.getFields();
      if (classFields.length == fields.length) {
        fields = classFields; // keep original order
      }
    }
    Project project = aClass.getProject();
    JVMElementFactory factory = JVMElementFactories.requireFactory(aClass.getLanguage(), project);
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

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
    DumbService dumbService = DumbService.getInstance(project);
    for (PsiField field : fields) {
      String fieldName = field.getName();
      String name = javaStyle.variableNameToPropertyName(fieldName, VariableKind.FIELD);
      String parmName = javaStyle.propertyNameToVariableName(name, VariableKind.PARAMETER);
      parmName = javaStyle.suggestUniqueVariableName(parmName, dummyConstructor, true);
      PsiType type = dumbService.computeWithAlternativeResolveEnabled(
        () -> AnnotationTargetUtil.keepStrictlyTypeUseAnnotations(field.getModifierList(), field.getType()));
      PsiParameter parm = factory.createParameter(parmName, type, aClass);
      if (!DumbService.isDumb(project)) {
        NullableNotNullManager.getInstance(project).copyNullableOrNotNullAnnotation(field, parm);
      }

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
      final PsiParameter[] parameters = fieldParams.toArray(PsiParameter.EMPTY_ARRAY);
      final List<String> existingNames = ContainerUtil.map(dummyConstructor.getParameterList().getParameters(), parameter -> parameter.getName());
      generator.generateFieldInitialization(buffer, fields, parameters, existingNames);
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
    throw new UnsupportedOperationException();
  }
}