// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util.proximity;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.ProximityLocation;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class OpenedInEditorWeigher extends ProximityWeigher {
  private static final NotNullLazyKey<VirtualFile[], ProximityLocation> OPENED_EDITORS = NotNullLazyKey.createLazyKey("openedEditors",
                                                                                                               location -> FileEditorManager.getInstance(location.getProject()).getOpenFiles());

  @Override
  public Comparable weigh(final @NotNull PsiElement element, final @NotNull ProximityLocation location) {
    Project project = location.getProject();
    if (project == null || project.isDefault()) {
      return null;
    }
    final PsiFile psiFile = element.getContainingFile();
    if (psiFile == null) return false;

    final VirtualFile virtualFile = psiFile.getOriginalFile().getVirtualFile();
    return virtualFile != null && ArrayUtil.find(OPENED_EDITORS.getValue(location), virtualFile) != -1;
  }
}
