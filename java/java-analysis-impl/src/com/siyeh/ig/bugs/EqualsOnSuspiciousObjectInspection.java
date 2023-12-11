// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public final class EqualsOnSuspiciousObjectInspection extends BaseInspection {
  private final Map<String, ReplaceInfo> myClasses =
    Map.ofEntries(
      Map.entry(CommonClassNames.JAVA_LANG_STRING_BUILDER, ReplaceInfo.stringBuilders()),
      Map.entry(CommonClassNames.JAVA_LANG_STRING_BUFFER, ReplaceInfo.stringBuilders()),
      Map.entry("java.util.concurrent.atomic.AtomicBoolean", ReplaceInfo.available(false, "get")),
      Map.entry("java.util.concurrent.atomic.AtomicInteger", ReplaceInfo.available(false, "get")),
      Map.entry("java.util.concurrent.atomic.AtomicIntegerArray", ReplaceInfo.notAvailable()),
      Map.entry("java.util.concurrent.atomic.AtomicLong", ReplaceInfo.available(false, "get")),
      Map.entry("java.util.concurrent.atomic.AtomicLongArray", ReplaceInfo.notAvailable()),
      Map.entry("java.util.concurrent.atomic.AtomicReference", ReplaceInfo.notAvailable()),
      Map.entry("java.util.concurrent.atomic.DoubleAccumulator", ReplaceInfo.notAvailable()),
      Map.entry("java.util.concurrent.atomic.DoubleAdder", ReplaceInfo.notAvailable()),
      Map.entry("java.util.concurrent.atomic.LongAccumulator", ReplaceInfo.notAvailable()),
      Map.entry("java.util.concurrent.atomic.LongAdder", ReplaceInfo.notAvailable()));

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    String typeName = (String)infos[0];
    return InspectionGadgetsBundle.message("equals.called.on.suspicious.object.problem.descriptor", StringUtil.getShortName(typeName));
  }

  @Override
  protected @Nullable LocalQuickFix buildFix(Object... infos) {
    PsiReferenceExpression expression = (PsiReferenceExpression)infos[1];
    PsiExpression qualifierExpression = expression.getQualifierExpression();
    if (qualifierExpression == null) {
      return null;
    }
    PsiType qualifierType = qualifierExpression.getType();
    PsiElement parent = expression.getParent();
    if (!(parent instanceof PsiMethodCallExpression callExpression)) {
      return null;
    }
    if (!BaseEqualsVisitor.OBJECT_EQUALS.matches(callExpression)) {
      return null;
    }
    PsiExpression[] arguments = callExpression.getArgumentList().getExpressions();
    if (arguments.length != 1) {
      return null;
    }
    PsiExpression argument = arguments[0];
    if (argument == null) {
      return null;
    }
    PsiType argumentType = argument.getType();
    if (argumentType == null || !argumentType.equals(qualifierType)) {
      return null;
    }
    PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(argumentType);
    if (psiClass == null) {
      return null;
    }
    String qualifiedName = psiClass.getQualifiedName();
    if (qualifiedName == null) {
      return null;
    }
    ReplaceInfo info = myClasses.get(qualifiedName);
    if (info == null || info instanceof ReplaceInfo.NotAvailableReplaceInfo) {
      return null;
    }

    return new EqualsOnSuspiciousObjectFix(info);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BaseEqualsVisitor() {
      @Override
      boolean checkTypes(@NotNull PsiReferenceExpression expression, @NotNull PsiType type1, @NotNull PsiType type2) {
        if (checkType(expression, type1)) return true;
        if (checkType(expression, type2)) return true;
        return false;
      }

      private boolean checkType(PsiReferenceExpression expression, PsiType type) {
        PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(type);
        if (psiClass != null) {
          String qualifiedName = psiClass.getQualifiedName();
          if (qualifiedName != null && myClasses.containsKey(qualifiedName)) {
            PsiElement name = expression.getReferenceNameElement();
            registerError(name == null ? expression : name, qualifiedName, expression);
            return true;
          }
        }
        return false;
      }
    };
  }

  private sealed interface ReplaceInfo {
    ReplaceInfo NOT_AVAILABLE = new NotAvailableReplaceInfo();

    String valueMethod();

    private static ReplaceInfo notAvailable() {
      return NOT_AVAILABLE;
    }

    private static SimpleReplaceInfo available(boolean isObjects, @NotNull String valueMethod) {
      return new SimpleReplaceInfo(isObjects, valueMethod);
    }

    static ReplaceInfo stringBuilders() {
      return new ComplexReplaceInfo() {
        private final static String COMPARE_TO = "compareTo";

        private final static SimpleReplaceInfo fallback = ReplaceInfo.available(true, "toString");

        @Override
        public String valueMethod() {
          return fallback.valueMethod();
        }

        private static boolean isApplicable(PsiElement psiElement) {
          return PsiUtil.isLanguageLevel11OrHigher(psiElement);
        }

        @Override
        public void fill(@NotNull PsiExpression argument,
                         @NotNull PsiExpression qualifier,
                         @NotNull StringBuilder builder,
                         @NotNull CommentTracker ct) {
          if (!isApplicable(argument)) {
            EqualsOnSuspiciousObjectFix.fillSimple(fallback, argument, qualifier, builder, ct);
            return;
          }
          builder.append(ct.text(qualifier, ParenthesesUtils.METHOD_CALL_PRECEDENCE))
            .append(".")
            .append(COMPARE_TO)
            .append("(")
            .append(ct.text(argument))
            .append(")")
            .append(" == 0");
        }
      };
    }

    final class NotAvailableReplaceInfo implements ReplaceInfo {
      @Override
      public String valueMethod() {
        return "";
      }
    }

    record SimpleReplaceInfo(boolean isObjects, @NotNull String valueMethod) implements ReplaceInfo {

    }

    non-sealed interface ComplexReplaceInfo extends ReplaceInfo {
      void fill(@NotNull PsiExpression argument, @NotNull PsiExpression qualifier,
                @NotNull StringBuilder builder, @NotNull CommentTracker ct);
    }
  }


  private static class EqualsOnSuspiciousObjectFix extends PsiUpdateModCommandQuickFix {
    @SafeFieldForPreview
    private final ReplaceInfo myInfo;

    private EqualsOnSuspiciousObjectFix(ReplaceInfo info) {
      myInfo = info;
    }

    @Override
    public @NotNull String getName() {
      return InspectionGadgetsBundle.message("equals.called.on.suspicious.object.fix.name", myInfo.valueMethod());
    }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("equals.called.on.suspicious.object.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement psiElement, @NotNull ModPsiUpdater updater) {
      if (myInfo instanceof ReplaceInfo.NotAvailableReplaceInfo) {
        return;
      }
      if (!(psiElement.getParent() instanceof PsiReferenceExpression referenceExpression)) {
        return;
      }
      PsiElement parent = referenceExpression.getParent();
      if (!(parent instanceof PsiMethodCallExpression callExpression)) {
        return;
      }
      PsiExpression qualifierExpression = referenceExpression.getQualifierExpression();
      if (qualifierExpression == null) {
        return;
      }
      PsiExpression[] expressions = callExpression.getArgumentList().getExpressions();
      if (expressions.length != 1) {
        return;
      }
      PsiExpression argument = expressions[0];
      if (argument == null) {
        return;
      }

      StringBuilder builder = new StringBuilder();
      CommentTracker ct = new CommentTracker();

      if (myInfo instanceof ReplaceInfo.SimpleReplaceInfo simpleReplaceInfo) {
        fillSimple(simpleReplaceInfo, argument, qualifierExpression, builder, ct);
      }
      if (myInfo instanceof ReplaceInfo.ComplexReplaceInfo complexReplaceInfo) {
        complexReplaceInfo.fill(argument, qualifierExpression, builder, ct);
      }

      if (builder.isEmpty()) {
        return;
      }

      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      PsiExpression newExpression = factory.createExpressionFromText(builder.toString(), callExpression);
      PsiElement callParent = callExpression.getParent();
      if (callParent instanceof PsiExpression &&
          ParenthesesUtils.areParenthesesNeeded(newExpression, (PsiExpression)callParent, true)) {
        newExpression = factory.createExpressionFromText("(" + newExpression.getText() + ")", callExpression);
      }
      PsiElement replaced = ct.replaceAndRestoreComments(callExpression, newExpression);
      final CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
      styleManager.reformat(replaced);
    }

    private static void fillSimple(@NotNull ReplaceInfo.SimpleReplaceInfo replaceInfo,
                                   @NotNull PsiExpression argument,
                                   @NotNull PsiExpression qualifierExpression,
                                   @NotNull StringBuilder builder,
                                   @NotNull CommentTracker ct) {
      boolean argumentIsNullable = isNullable(argument);

      if (argumentIsNullable) {
        builder.append(ct.text(argument, ParenthesesUtils.EQUALITY_PRECEDENCE))
          .append("!=null &&");
      }

      String qualifierText = ct.text(qualifierExpression, ParenthesesUtils.METHOD_CALL_PRECEDENCE) + "." + replaceInfo.valueMethod + "()";
      String argumentText = ct.text(argument, ParenthesesUtils.METHOD_CALL_PRECEDENCE) + "." + replaceInfo.valueMethod + "()";
      if (replaceInfo.isObjects) {
        builder.append(qualifierText).append(".equals(").append(argumentText).append(")");
      }
      else {
        builder.append(qualifierText).append("==").append(argumentText);
      }
    }

    private static boolean isNullable(@NotNull PsiExpression argument) {
      final Nullability nullability = NullabilityUtil.getExpressionNullability(argument, true);
      if (nullability == Nullability.NULLABLE) return true;
      if (argument instanceof PsiReferenceExpression referenceExpression) {
        final PsiElement target = referenceExpression.resolve();
        if (target instanceof PsiModifierListOwner) {
          if (NullableNotNullManager.isNullable((PsiModifierListOwner)target)) {
            return true;
          }
        }
      }
      return false;
    }
  }
}
