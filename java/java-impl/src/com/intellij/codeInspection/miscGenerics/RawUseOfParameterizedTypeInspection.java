// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.miscGenerics;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeMigration.TypeMigrationProcessor;
import com.intellij.refactoring.typeMigration.TypeMigrationRules;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 *  @author dsl
 */
public class RawUseOfParameterizedTypeInspection extends BaseInspection {

  @SuppressWarnings("PublicField") public boolean ignoreObjectConstruction = false;

  @SuppressWarnings("PublicField") public boolean ignoreTypeCasts = false;

  @SuppressWarnings("PublicField") public boolean ignoreUncompilable = true;

  @SuppressWarnings("PublicField") public boolean ignoreParametersOfOverridingMethods = false;

  @SuppressWarnings("PublicField") public boolean ignoreWhenQuickFixNotAvailable = false;

  @Pattern(VALID_ID_PATTERN)
  @NotNull
  @Override
  public String getID() {
    return "rawtypes";
  }

  @Nullable
  @Override
  public String getAlternativeID() {
    return "RawUseOfParameterized";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return JavaBundle.message("inspection.raw.use.of.parameterized.type.problem.descriptor");
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(JavaBundle.message("raw.use.of.parameterized.type.ignore.new.objects.option"),
                             "ignoreObjectConstruction");
    optionsPanel.addCheckbox(JavaBundle.message("raw.use.of.parameterized.type.ignore.type.casts.option"),
                             "ignoreTypeCasts");
    optionsPanel.addCheckbox(JavaBundle.message("raw.use.of.parameterized.type.ignore.uncompilable.option"),
                             "ignoreUncompilable");
    optionsPanel.addCheckbox(JavaBundle.message("raw.use.of.parameterized.type.ignore.overridden.parameter.option"),
                             "ignoreParametersOfOverridingMethods");
    optionsPanel.addCheckbox(JavaBundle.message("raw.use.of.parameterized.type.ignore.quickfix.not.available.option"),
                             "ignoreWhenQuickFixNotAvailable");
    return optionsPanel;
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    if (Boolean.FALSE.equals(infos[0])) {
      return null;
    }
    return new RawTypeCanBeGenericFix((String)infos[1]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new RawUseOfParameterizedTypeVisitor();
  }

  private class RawUseOfParameterizedTypeVisitor extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      if (ignoreObjectConstruction) {
        return;
      }
      if (ignoreWhenQuickFixNotAvailable) {
        return;
      }
      if (ignoreUncompilable && expression.isArrayCreation()) {
        //array creation can (almost) never be generic
        return;
      }
      final PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
      checkReferenceElement(classReference);
    }

    @Override
    public void visitTypeElement(@NotNull PsiTypeElement typeElement) {
      if (typeElement.getParent() instanceof PsiVariable) {
        PsiVariable variable = (PsiVariable)typeElement.getParent();
        final PsiType type = getSuggestedType(variable);
        if (type != null) {
          final String typeText = GenericsUtil.getVariableTypeByExpressionType(type).getPresentableText();
          final String message =
            JavaBundle.message("raw.variable.type.can.be.generic.quickfix", variable.getName(), typeText);
          final boolean isQuickFixAvailable = true;
          registerError(typeElement, isQuickFixAvailable, message);
          return;
        }
      }
      if (ignoreWhenQuickFixNotAvailable) {
        return;
      }
      final PsiType type = typeElement.getType();
      if (!(type instanceof PsiClassType)) {
        return;
      }
      super.visitTypeElement(typeElement);
      PsiElement directParent = typeElement.getParent();
      if (ignoreUncompilable && directParent instanceof PsiTypeElement) {
        PsiType parentType = ((PsiTypeElement)directParent).getType();
        if (parentType instanceof PsiArrayType) {
          if (PsiTreeUtil.skipParentsOfType(directParent, PsiTypeElement.class) instanceof PsiMethodReferenceExpression) {
            return;
          }
        }
      }
      final PsiElement parent = PsiTreeUtil.skipParentsOfType(
        typeElement, PsiTypeElement.class, PsiReferenceParameterList.class, PsiJavaCodeReferenceElement.class);
      if (parent instanceof PsiTypeTestPattern || parent instanceof PsiClassObjectAccessExpression) {
        return;
      }
      if (ignoreTypeCasts && parent instanceof PsiTypeCastExpression) {
        return;
      }
      if (PsiTreeUtil.getParentOfType(typeElement, PsiComment.class) != null) {
        return;
      }
      if (ignoreUncompilable && parent instanceof PsiAnnotationMethod) {
        // type of class type parameter cannot be parameterized if annotation method has default value
        final PsiAnnotationMemberValue defaultValue = ((PsiAnnotationMethod)parent).getDefaultValue();
        if (defaultValue != null && directParent instanceof PsiTypeElement) {
          return;
        }
      }
      if (parent instanceof PsiParameter) {
        final PsiParameter parameter = (PsiParameter)parent;
        final PsiElement declarationScope = parameter.getDeclarationScope();
        if (declarationScope instanceof PsiMethod) {
          final PsiMethod method = (PsiMethod)declarationScope;
          if (ignoreParametersOfOverridingMethods) {
            if (MethodUtils.getSuper(method) != null || MethodCallUtils.isUsedAsSuperConstructorCallArgument(parameter, false)) {
              return;
            }
          }
          else if (ignoreUncompilable &&
                   (LibraryUtil.isOverrideOfLibraryMethod(method) || MethodCallUtils.isUsedAsSuperConstructorCallArgument(parameter, true))) {
            return;
          }
        }
      }
      final PsiJavaCodeReferenceElement referenceElement = typeElement.getInnermostComponentReferenceElement();
      checkReferenceElement(referenceElement);
    }

    @Override
    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      if (ignoreWhenQuickFixNotAvailable) {
        return;
      }
      final PsiElement referenceParent = reference.getParent();
      if (!(referenceParent instanceof PsiReferenceList)) {
        return;
      }
      final PsiReferenceList referenceList = (PsiReferenceList)referenceParent;
      final PsiElement listParent = referenceList.getParent();
      if (!(listParent instanceof PsiClass)) {
        return;
      }
      if (referenceList.equals(((PsiClass)listParent).getPermitsList())) {
        return;
      }
      checkReferenceElement(reference);
    }

    private void checkReferenceElement(PsiJavaCodeReferenceElement reference) {
      if (reference == null) {
        return;
      }
      final PsiType[] typeParameters = reference.getTypeParameters();
      if (typeParameters.length > 0) {
        return;
      }
      final PsiElement element = reference.resolve();
      if (!(element instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass)element;
      final PsiElement qualifier = reference.getQualifier();
      if (qualifier instanceof PsiJavaCodeReferenceElement) {
        final PsiJavaCodeReferenceElement qualifierReference = (PsiJavaCodeReferenceElement)qualifier;
        if (!aClass.hasModifierProperty(PsiModifier.STATIC) && !aClass.isInterface() && !aClass.isEnum()) {
          checkReferenceElement(qualifierReference);
        }
      }
      if (!aClass.hasTypeParameters()) {
        return;
      }
      registerError(reference, false, null);
    }
  }

  @Nullable
  private static PsiType getSuggestedType(@NotNull PsiVariable variable) {
    final PsiExpression initializer = variable.getInitializer();
    if (initializer == null) return null;
    final PsiType variableType = variable.getType();
    final PsiType initializerType = initializer.getType();
    if (!(variableType instanceof PsiClassType)) return null;
    final PsiClassType variableClassType = (PsiClassType) variableType;
    if (!variableClassType.isRaw()) return null;
    if (!(initializerType instanceof PsiClassType)) return null;
    final PsiClassType initializerClassType = (PsiClassType) initializerType;
    if (initializerClassType.isRaw()) return null;
    final PsiClassType.ClassResolveResult variableResolveResult = variableClassType.resolveGenerics();
    final PsiClassType.ClassResolveResult initializerResolveResult = initializerClassType.resolveGenerics();
    if (initializerResolveResult.getElement() == null) return null;
    PsiClass variableResolved = variableResolveResult.getElement();
    if (variableResolved == null) return null;
    PsiSubstitutor targetSubstitutor = TypeConversionUtil
      .getClassSubstitutor(variableResolved, initializerResolveResult.getElement(), initializerResolveResult.getSubstitutor());
    if (targetSubstitutor == null) return null;
    PsiType type = JavaPsiFacade.getElementFactory(variable.getProject()).createType(variableResolved, targetSubstitutor);
    if (variableType.equals(type)) return null;
    return type;
  }

  private static class RawTypeCanBeGenericFix extends InspectionGadgetsFix {
    private final @IntentionName String myName;

    RawTypeCanBeGenericFix(@NotNull @IntentionName String name) {
      myName = name;
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("raw.variable.type.can.be.generic.family.quickfix");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getStartElement().getParent();
      if (element instanceof PsiVariable) {
        final PsiVariable variable = (PsiVariable)element;
        final PsiType type = getSuggestedType(variable);
        if (type != null) {
          final TypeMigrationRules rules = new TypeMigrationRules(project);
          rules.setBoundScope(PsiSearchHelper.getInstance(project).getUseScope(variable));
          TypeMigrationProcessor.runHighlightingTypeMigration(project, null, rules, variable, type, false, true);
        }
      }
    }
  }
}