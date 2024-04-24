// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.slicer;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.usages.TextChunk;
import com.intellij.usages.UsagePresentation;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

class JavaSliceDereferenceUsage extends JavaSliceUsage {
  JavaSliceDereferenceUsage(@NotNull PsiElement element, @NotNull SliceUsage parent, @NotNull PsiSubstitutor substitutor) {
    super(element, parent, substitutor);
  }

  @Override
  public void processChildren(@NotNull Processor<? super SliceUsage> processor) {
    // no children
  }

  @Override
  protected boolean isForcedLeaf() {
    return true;
  }

  @Override
  public @NotNull UsagePresentation getPresentation() {
    final UsagePresentation presentation = super.getPresentation();

    return new UsagePresentation() {
      @Override
      public TextChunk @NotNull [] getText() {
        return presentation.getText();
      }

      @Override
      public @NotNull String getPlainText() {
        return presentation.getPlainText();
      }

      @Override
      public Icon getIcon() {
        return presentation.getIcon();
      }

      @Override
      public String getTooltipText() {
        return JavaRefactoringBundle.message("dataflow.to.here.variable.dereferenced.tooltip");
      }
    };
  }
}
