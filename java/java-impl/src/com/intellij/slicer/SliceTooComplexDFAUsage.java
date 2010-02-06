/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.slicer;

import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.usages.TextChunk;
import com.intellij.usages.UsagePresentation;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * User: cdr
 */
public class SliceTooComplexDFAUsage extends SliceUsage {
  public SliceTooComplexDFAUsage(@NotNull PsiElement element, @NotNull SliceUsage parent, @NotNull PsiSubstitutor substitutor) {
    super(element, parent, substitutor);
  }

  @Override
  public void processChildren(Processor<SliceUsage> processor) {
    // no children
  }

  @NotNull
  @Override
  public UsagePresentation getPresentation() {
    final UsagePresentation presentation = super.getPresentation();
    return new UsagePresentation() {
      @NotNull
      public TextChunk[] getText() {
        return new TextChunk[]{
          new TextChunk(new TextAttributes(Color.RED, null, null, EffectType.WAVE_UNDERSCORE, Font.PLAIN), getTooltipText())
        };
      }

      @NotNull
      public String getPlainText() {
        return presentation.getPlainText();
      }

      public Icon getIcon() {
        return presentation.getIcon();
      }

      public String getTooltipText() {
        return "Too complex to analyze, analysis stoppped here";
      }
    };
  }
}
