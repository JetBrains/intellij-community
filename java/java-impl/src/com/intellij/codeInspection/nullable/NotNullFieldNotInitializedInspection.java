// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.nullable;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public class NotNullFieldNotInitializedInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final String IGNORE_IMPLICITLY_WRITTEN_FIELDS_NAME = "IGNORE_IMPLICITLY_WRITTEN_FIELDS";
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

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitField(@NotNull PsiField field) {
        NullableNotNullManager manager = NullableNotNullManager.getInstance(holder.getProject());
        NullabilityAnnotationInfo info = manager.findEffectiveNullabilityInfo(field);
        if (info == null ||
            info.getNullability() != Nullability.NOT_NULL ||
            HighlightControlFlowUtil.isFieldInitializedAfterObjectConstruction(field)) {
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
      if (body != null && HighlightControlFlowUtil.variableDefinitelyAssignedIn(field, body)) {
        return true;
      }
    }
    return false;
  }
}
