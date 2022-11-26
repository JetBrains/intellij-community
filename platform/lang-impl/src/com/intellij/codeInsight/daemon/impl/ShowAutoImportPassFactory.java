// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

final class ShowAutoImportPassFactory implements TextEditorHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar {
  @Override
  public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar, @NotNull Project project) {
    registrar.registerTextEditorHighlightingPass(this, new int[]{Pass.UPDATE_ALL,Pass.LOCAL_INSPECTIONS}, null, false, -1);
  }

  @Override
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
    HighlightingSessionImpl session = (HighlightingSessionImpl)HighlightingSessionImpl.getFromCurrentIndicator(file);
    VirtualFile virtualFile = file.getVirtualFile();
    boolean isInContent = virtualFile != null && ModuleUtilCore.projectContainsFile(file.getProject(), virtualFile, false);
    boolean canChangeFileSilently = session.canChangeFileSilently(isInContent);
    return canChangeFileSilently ? new ShowAutoImportPass(file, editor, session.getVisibleRange()) : null;
  }
}
