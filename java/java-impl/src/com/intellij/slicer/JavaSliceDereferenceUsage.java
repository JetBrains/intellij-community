/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.usages.TextChunk;
import com.intellij.usages.UsagePresentation;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * User: cdr
 */
public class JavaSliceDereferenceUsage extends JavaSliceUsage {
  public JavaSliceDereferenceUsage(@NotNull PsiElement element, @NotNull SliceUsage parent, @NotNull PsiSubstitutor substitutor) {
    super(element, parent, substitutor,0,"");
  }

  @Override
  public void processChildren(@NotNull Processor<SliceUsage> processor) {
    // no children
  }

  @NotNull
  @Override
  public UsagePresentation getPresentation() {
    final UsagePresentation presentation = super.getPresentation();

    return new UsagePresentation() {
      @Override
      @NotNull
      public TextChunk[] getText() {
        return presentation.getText();
      }

      @Override
      @NotNull
      public String getPlainText() {
        return presentation.getPlainText();
      }

      @Override
      public Icon getIcon() {
        return presentation.getIcon();
      }

      @Override
      public String getTooltipText() {
        return "Variable dereferenced";
      }
    };
  }
}
