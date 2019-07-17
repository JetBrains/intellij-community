// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.nullable;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.AddVariableInitializerFix;
import com.intellij.codeInsight.daemon.impl.quickfix.DeleteElementFix;
import com.intellij.codeInsight.daemon.impl.quickfix.InitializeFinalFieldInConstructorFix;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class NotNullFieldNotInitializedInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final String IGNORE_IMPLICITLY_WRITTEN_FIELDS_NAME = "IGNORE_IMPLICITLY_WRITTEN_FIELDS";
  public boolean IGNORE_IMPLICITLY_WRITTEN_FIELDS = true;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionsBundle.message("inspection.notnull.field.not.initialized.option"),
                                          this, IGNORE_IMPLICITLY_WRITTEN_FIELDS_NAME);
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
        if (IGNORE_IMPLICITLY_WRITTEN_FIELDS && implicitWrite) {
          return;
        }

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
                                               InspectionsBundle.message("inspection.notnull.field.not.initialized.option"), true));
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
}
