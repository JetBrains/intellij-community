// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.nullable;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.AddVariableInitializerFix;
import com.intellij.codeInsight.daemon.impl.quickfix.DeleteElementFix;
import com.intellij.codeInsight.daemon.impl.quickfix.InitializeFinalFieldInConstructorFix;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class NotNullFieldNotInitializedInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final String IGNORE_IMPLICITLY_WRITTEN_FIELDS_NAME = "IGNORE_IMPLICITLY_WRITTEN_FIELDS";
  private static final String IGNORE_FIELDS_WRITTEN_IN_SETUP_NAME = "IGNORE_FIELDS_WRITTEN_IN_SETUP";
  public boolean IGNORE_IMPLICITLY_WRITTEN_FIELDS = true;
  public boolean IGNORE_FIELDS_WRITTEN_IN_SETUP = true;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionsBundle.message("inspection.notnull.field.not.initialized.option.implicit"), IGNORE_IMPLICITLY_WRITTEN_FIELDS_NAME);
    panel.addCheckbox(InspectionsBundle.message("inspection.notnull.field.not.initialized.option.setup"), IGNORE_FIELDS_WRITTEN_IN_SETUP_NAME);
    return panel;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitField(PsiField field) {
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
        String message = (byDefault && name != null ? "@" + name.getReferenceName() : "Not-null") + " fields must be initialized";

        List<LocalQuickFix> fixes = new ArrayList<>();
        if (implicitWrite) {
          fixes.add(new SetInspectionOptionFix(NotNullFieldNotInitializedInspection.this,
                                               IGNORE_IMPLICITLY_WRITTEN_FIELDS_NAME,
                                               InspectionsBundle.message("inspection.notnull.field.not.initialized.option.implicit"), true));
        }
        if (writtenInSetup) {
          fixes.add(new SetInspectionOptionFix(NotNullFieldNotInitializedInspection.this,
                                               IGNORE_FIELDS_WRITTEN_IN_SETUP_NAME,
                                               InspectionsBundle.message("inspection.notnull.field.not.initialized.option.setup"), true));
        }
        if (ownAnnotation) {
          fixes.add(new DeleteElementFix(annotation, "Remove not-null annotation"));
        }
        if (isOnTheFly) {
          fixes.add(new InitializeFinalFieldInConstructorFix(field));
          fixes.add(new AddVariableInitializerFix(field));
        }

        holder.registerProblem(anchor, message, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
      }
    };
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
