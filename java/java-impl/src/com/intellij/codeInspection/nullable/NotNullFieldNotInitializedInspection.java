// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.nullable;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.AddVariableInitializerFix;
import com.intellij.codeInsight.daemon.impl.quickfix.InitializeFinalFieldInConstructorFix;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.UpdateInspectionOptionFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public class NotNullFieldNotInitializedInspection extends AbstractBaseJavaLocalInspectionTool {
  @Language("jvm-field-name") 
  private static final String IGNORE_IMPLICITLY_WRITTEN_FIELDS_NAME = "IGNORE_IMPLICITLY_WRITTEN_FIELDS";
  @Language("jvm-field-name") 
  private static final String IGNORE_FIELDS_WRITTEN_IN_SETUP_NAME = "IGNORE_FIELDS_WRITTEN_IN_SETUP";
  public boolean IGNORE_IMPLICITLY_WRITTEN_FIELDS = true;
  public boolean IGNORE_FIELDS_WRITTEN_IN_SETUP = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox(IGNORE_IMPLICITLY_WRITTEN_FIELDS_NAME, JavaBundle.message("inspection.notnull.field.not.initialized.option.implicit"))
        .description(HtmlChunk.raw(JavaBundle.message("inspection.notnull.field.not.initialized.option.implicit.description"))),
      checkbox(IGNORE_FIELDS_WRITTEN_IN_SETUP_NAME, JavaBundle.message("inspection.notnull.field.not.initialized.option.setup"))
        .description(HtmlChunk.raw(JavaBundle.message("inspection.notnull.field.not.initialized.option.setup.description"))));
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitField(@NotNull PsiField field) {
        NullableNotNullManager manager = NullableNotNullManager.getInstance(holder.getProject());
        NullabilityAnnotationInfo info = manager.findEffectiveNullabilityInfo(field);
        if (info == null || info.getNullability() != Nullability.NOT_NULL) return;
        
        if (ControlFlowUtil.isFieldInitializedAfterObjectConstruction(field) ||
            isWrittenIndirectly(field)) {
          return;
        }

        boolean implicitWrite = (IGNORE_IMPLICITLY_WRITTEN_FIELDS || isOnTheFly) && UnusedSymbolUtil.isImplicitWrite(field);
        if (IGNORE_IMPLICITLY_WRITTEN_FIELDS && implicitWrite) return;

        boolean writtenInSetup = (IGNORE_FIELDS_WRITTEN_IN_SETUP || isOnTheFly) && isWrittenInSetup(field);
        if (IGNORE_FIELDS_WRITTEN_IN_SETUP && writtenInSetup) return;

        boolean byDefault = info.isContainer();
        PsiAnnotation annotation = info.getAnnotation();
        PsiJavaCodeReferenceElement name = annotation.getNameReferenceElement();
        boolean ownAnnotation = annotation.isPhysical() && !byDefault;
        PsiElement anchor = ownAnnotation ? annotation : field.getNameIdentifier();
        String message = JavaBundle.message("inspection.notnull.field.not.initialized.message",
                                            byDefault && name != null ? "@" + name.getReferenceName() : "Not-null");

        List<LocalQuickFix> fixes = new ArrayList<>();
        if (implicitWrite) {
          fixes.add(LocalQuickFix.from(new UpdateInspectionOptionFix(
            NotNullFieldNotInitializedInspection.this,
            IGNORE_IMPLICITLY_WRITTEN_FIELDS_NAME,
            JavaBundle.message("inspection.notnull.field.not.initialized.option.implicit"), true)));
        }
        if (writtenInSetup) {
          fixes.add(LocalQuickFix.from(new UpdateInspectionOptionFix(
            NotNullFieldNotInitializedInspection.this,
            IGNORE_FIELDS_WRITTEN_IN_SETUP_NAME,
            JavaBundle.message("inspection.notnull.field.not.initialized.option.setup"), true)));
        }
        if (ownAnnotation) {
          fixes.add(QuickFixFactory.getInstance().createDeleteFix(annotation, JavaBundle.message("quickfix.text.remove.not.null.annotation")));
        }
        if (isOnTheFly) {
          fixes.add(LocalQuickFix.from(new InitializeFinalFieldInConstructorFix(field)));
          fixes.add(LocalQuickFix.from(new AddVariableInitializerFix(field)));
        }

        reportProblem(holder, anchor, message, fixes);
      }
    };
  }

  private static boolean isWrittenIndirectly(@NotNull PsiField field) {
    PsiClass fieldClass = field.getContainingClass();
    if (fieldClass == null) return false;
    PsiMethod[] constructors = fieldClass.getConstructors();
    if (constructors.length == 0) return false;
    return ContainerUtil.all(constructors, constructor ->
      JavaPsiConstructorUtil.isChainedConstructorCall(JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(constructor)) ||
      isWrittenIndirectlyIn(field, constructor));
  }

  private static boolean isWrittenIndirectlyIn(@NotNull PsiField field, @NotNull PsiMethod constructor) {
    PsiCodeBlock body = constructor.getBody();
    if (body == null) return false;
    PsiStatement[] statements = body.getStatements();
    for (PsiStatement statement : statements) {
      if (statement instanceof PsiExpressionStatement expressionStatement &&
          expressionStatement.getExpression() instanceof PsiMethodCallExpression call) {
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (qualifier == null || qualifier instanceof PsiThisExpression thisExpression && thisExpression.getQualifier() == null) {
          PsiMethod target = call.resolveMethod();
          if (target != null && !target.hasModifierProperty(PsiModifier.STATIC) &&
              target.getContainingClass() == constructor.getContainingClass() && !target.isConstructor()) {
            PsiCodeBlock targetBody = target.getBody();
            if (targetBody != null && ControlFlowUtil.variableDefinitelyAssignedIn(field, targetBody)) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  protected void reportProblem(@NotNull ProblemsHolder holder,
                               PsiElement anchor,
                               @InspectionMessage String message,
                               List<LocalQuickFix> fixes) {
    holder.registerProblem(anchor, message, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
  }

  private static boolean isWrittenInSetup(PsiField field) {
    PsiMethod method = TestFrameworks.getInstance().findSetUpMethod(field.getContainingClass());
    if (method != null) {
      PsiCodeBlock body = method.getBody();
      if (body != null && ControlFlowUtil.variableDefinitelyAssignedIn(field, body)) {
        return true;
      }
    }
    return false;
  }
}
