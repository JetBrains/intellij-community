// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.slicer;

import com.intellij.lang.LangBundle;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;
import com.intellij.ui.JBColor;
import com.intellij.usages.TextChunk;
import com.intellij.usages.UsagePresentation;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public final class SliceTooComplexDFAUsage extends SliceUsage {
  public SliceTooComplexDFAUsage(@NotNull PsiElement element, @NotNull SliceUsage parent) {
    super(element, parent);
  }

  @Override
  public void processChildren(@NotNull Processor<? super SliceUsage> processor) {
    // no children
  }

  @Override
  protected void processUsagesFlownFromThe(PsiElement element, Processor<? super SliceUsage> uniqueProcessor) {
    // no children
  }

  @Override
  protected void processUsagesFlownDownTo(PsiElement element, Processor<? super SliceUsage> uniqueProcessor) {
    // no children
  }

  @Override
  protected @NotNull SliceUsage copy() {
    return new SliceTooComplexDFAUsage(getUsageInfo().getElement(), getParent());
  }

  @Override
  public @NotNull UsagePresentation getPresentation() {
    final UsagePresentation presentation = super.getPresentation();
    return new UsagePresentation() {
      @Override
      public TextChunk @NotNull [] getText() {
        return new TextChunk[]{
          new TextChunk(new TextAttributes(JBColor.RED, null, null, EffectType.WAVE_UNDERSCORE, Font.PLAIN), getTooltipText())
        };
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
      public @NlsContexts.Tooltip String getTooltipText() {
        return LangBundle.message("tooltip.too.complex.to.analyze.analysis.stopped.here");
      }
    };
  }
}
