/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

  @NotNull
  @Override
  public UsagePresentation getPresentation() {
    final UsagePresentation presentation = super.getPresentation();

    return new UsagePresentation() {
      @Override
      public TextChunk @NotNull [] getText() {
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
        return JavaRefactoringBundle.message("dataflow.to.here.variable.dereferenced.tooltip");
      }
    };
  }
}
