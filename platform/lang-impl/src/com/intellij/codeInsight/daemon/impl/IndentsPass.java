// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.impl.indentGuide.IndentGuidePass;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.IndentGuideDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.List;


// TODO: remove me replacing with IndentGuidePass
@TestOnly
@ApiStatus.Internal
public final class IndentsPass {

  private final IndentGuidePass pass;

  public IndentsPass(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    this.pass = new IndentGuidePass(project, editor, psiFile);
  }

  public void doCollectInformation(@NotNull ProgressIndicator progress) {
    pass.doCollectInformation(progress);
  }

  public @NotNull List<IndentGuideDescriptor> getDescriptors() {
    return pass.getDescriptors();
  }
}
