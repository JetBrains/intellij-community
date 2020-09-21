// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.java.JavaBundle;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.convertToRecord.ConvertToRecordHandler;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RefactoringInspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClassCanBeRecordInspection extends BaseInspection {

  @Override
  public boolean shouldInspect(PsiFile file) {
    return HighlightingFeature.RECORDS.isAvailable(file);
  }

  @Override
  protected @NotNull @InspectionMessage String buildErrorString(Object... infos) {
    return JavaBundle.message("class.can.be.record.display.name");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassCanBeRecordVisitor();
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  protected @Nullable InspectionGadgetsFix buildFix(Object... infos) {
    return new ClassCanBeRecordFix();
  }

  private static class ClassCanBeRecordVisitor extends BaseInspectionVisitor {
    @Override
    public void visitClass(PsiClass aClass) {
      super.visitClass(aClass);
      PsiIdentifier classIdentifier = aClass.getNameIdentifier();
      if (classIdentifier != null && ConvertToRecordHandler.isAppropriatePsiClass(aClass)) {
        registerError(classIdentifier);
      }
    }
  }

  private static class ClassCanBeRecordFix extends RefactoringInspectionGadgetsFix {
    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return JavaBundle.message("class.can.be.record.quick.fix");
    }

    @Override
    public @NotNull RefactoringActionHandler getHandler() {
      return new ConvertToRecordHandler();
    }
  }
}
