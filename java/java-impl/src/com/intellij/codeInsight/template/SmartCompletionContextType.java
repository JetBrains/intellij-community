// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template;

import com.intellij.java.JavaBundle;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;


public final class SmartCompletionContextType extends TemplateContextType {
  public SmartCompletionContextType() {
    super("COMPLETION", JavaBundle.message("dialog.edit.template.checkbox.smart.type.completion"), JavaCodeContextType.Generic.class);
  }

  @Override
  public boolean isInContext(@NotNull PsiFile file, int offset) {
    return false;
  }

  @Override
  public boolean isExpandableFromEditor() {
    return false;
  }

}
