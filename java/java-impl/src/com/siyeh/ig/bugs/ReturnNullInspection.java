// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.intention.AddAnnotationModCommandAction;
import com.intellij.codeInsight.options.JavaInspectionButtons;
import com.intellij.codeInsight.options.JavaInspectionControls;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtilRt;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CollectionUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class ReturnNullInspection extends BaseInspection {

  private static final CallMatcher.Simple MAP_COMPUTE =
    CallMatcher.instanceCall("java.util.Map", "compute", "computeIfPresent", "computeIfAbsent");

  @SuppressWarnings("PublicField")
  public boolean m_reportObjectMethods = true;
  @SuppressWarnings("PublicField")
  public boolean m_reportArrayMethods = true;
  @SuppressWarnings("PublicField")
  public boolean m_reportCollectionMethods = true;
  @SuppressWarnings("PublicField")
  public boolean m_ignorePrivateMethods = false;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("m_ignorePrivateMethods", InspectionGadgetsBundle.message("return.of.null.ignore.private.option")),
      checkbox("m_reportArrayMethods", InspectionGadgetsBundle.message("return.of.null.arrays.option")),
      checkbox("m_reportCollectionMethods", InspectionGadgetsBundle.message("return.of.null.collections.option")),
      checkbox("m_reportObjectMethods", InspectionGadgetsBundle.message("return.of.null.objects.option")),
      JavaInspectionControls.button(JavaInspectionButtons.ButtonKind.NULLABILITY_ANNOTATIONS));
  }

  @Override
  @Pattern("[a-zA-Z_0-9.-]+")
  public @NotNull String getID() {
    return "ReturnOfNull";
  }

  @Override
  public @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "return.of.null.problem.descriptor");
  }

  @Override
  protected @Nullable LocalQuickFix buildFix(Object... infos) {
    final PsiElement elt = (PsiElement)infos[0];
    if (!AnnotationUtil.isAnnotatingApplicable(elt)) {
      return null;
    }

    final PsiMethod method = PsiTreeUtil.getParentOfType(elt, PsiMethod.class, false, PsiLambdaExpression.class);
    if (method == null) return null;
    final PsiType type = method.getReturnType();
    if (TypeUtils.isOptional(type)) {
      // don't suggest to annotate Optional methods as Nullable
      return new ReplaceWithEmptyOptionalFix(((PsiClassType)type).rawType().getCanonicalText());
    }

    final NullableNotNullManager manager = NullableNotNullManager.getInstance(elt.getProject());
    return LocalQuickFix.from(new AddAnnotationModCommandAction(manager.getDefaultNullable(), method,
                                                                ArrayUtilRt.toStringArray(manager.getNotNulls())));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ReturnNullVisitor();
  }

  private static class ReplaceWithEmptyOptionalFix extends PsiUpdateModCommandQuickFix {

    private final String myTypeText;

    ReplaceWithEmptyOptionalFix(String typeText) {
      myTypeText = typeText;
    }

    @Override
    public @Nls @NotNull String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", getReplacementText());
    }

    @Override
    public @Nls @NotNull String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "Optional.empty()");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiLiteralExpression literalExpression)) {
        return;
      }
      PsiReplacementUtil.replaceExpression(literalExpression, getReplacementText());
    }

    private @NonNls @NotNull String getReplacementText() {
      return myTypeText + "." + (OptionalUtil.GUAVA_OPTIONAL.equals(myTypeText) ? "absent" : "empty") + "()";
    }
  }

  private class ReturnNullVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLiteralExpression(@NotNull PsiLiteralExpression value) {
      super.visitLiteralExpression(value);
      final String text = value.getText();
      if (!JavaKeywords.NULL.equals(text)) {
        return;
      }
      final PsiElement parent = ExpressionUtils.getPassThroughParent(value);
      if (!(parent instanceof PsiReturnStatement) && !(parent instanceof PsiLambdaExpression)) {
        return;
      }
      final PsiElement element = PsiTreeUtil.getParentOfType(value, PsiMethod.class, PsiLambdaExpression.class);
      final PsiMethod method;
      final PsiType returnType;
      final boolean lambda;
      if (element instanceof PsiMethod) {
        method = (PsiMethod)element;
        returnType = method.getReturnType();
        lambda = false;
      }
      else if (element instanceof PsiLambdaExpression) {
        final PsiType functionalInterfaceType = ((PsiLambdaExpression)element).getFunctionalInterfaceType();
        method = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
        returnType = LambdaUtil.getFunctionalInterfaceReturnType(functionalInterfaceType);
        lambda = true;
      }
      else {
        return;
      }
      if (method == null || returnType == null) {
        return;
      }

      if (TypeUtils.isOptional(returnType)) {
        registerError(value, value);
        return;
      }
      if (lambda) {
        if (m_ignorePrivateMethods || isInNullableContext(element)) {
          return;
        }
      }
      else {
        if (m_ignorePrivateMethods && method.hasModifierProperty(PsiModifier.PRIVATE)) {
          return;
        }
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass instanceof PsiAnonymousClass) {
          if (m_ignorePrivateMethods || isInNullableContext(containingClass.getParent())) {
            return;
          }
        }
      }
      final Project project = method.getProject();
      final NullabilityAnnotationInfo info = NullableNotNullManager.getInstance(project).findEffectiveNullabilityInfo(method);
      if (info != null && info.getNullability() == Nullability.NULLABLE && !info.isInferred()) {
        return;
      }
      if (DfaPsiUtil.getTypeNullability(returnType) == Nullability.NULLABLE) {
        return;
      }

      if (CollectionUtils.isCollectionClassOrInterface(returnType)) {
        if (m_reportCollectionMethods) {
          registerError(value, value);
        }
      }
      else if (returnType.getArrayDimensions() > 0) {
        if (m_reportArrayMethods) {
          registerError(value, value);
        }
      }
      else if (!returnType.equalsToText("java.lang.Void")){
        if (m_reportObjectMethods) {
          registerError(value, value);
        }
      }
    }

    private boolean isInNullableContext(PsiElement element) {
      final PsiElement parent = element instanceof PsiExpression ? ExpressionUtils.getPassThroughParent((PsiExpression)element) : element;
      if (parent instanceof PsiVariable variable) {
        final PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
        if (codeBlock == null) {
          return false;
        }
        final PsiElement[] refs = DefUseUtil.getRefs(codeBlock, variable, element);
        return Arrays.stream(refs).anyMatch(this::isInNullableContext);
      }
      else if (parent instanceof PsiExpressionList) {
        final PsiElement grandParent = parent.getParent();
        if (grandParent instanceof PsiMethodCallExpression methodCallExpression) {
          return MAP_COMPUTE.test(methodCallExpression);
        }
      }
      return false;
    }
  }
}
