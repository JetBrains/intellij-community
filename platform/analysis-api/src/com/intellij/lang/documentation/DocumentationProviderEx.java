// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class DocumentationProviderEx implements DocumentationProvider {

  /**
   * @deprecated Use/override {@link #getCustomDocumentationElement(Editor, PsiFile, PsiElement, int)} instead.
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  @Nullable
  public PsiElement getCustomDocumentationElement(@NotNull final Editor editor,
                                                  @NotNull final PsiFile file,
                                                  @Nullable PsiElement contextElement) {
    return null;
  }

  @Nullable
  public Image getLocalImageForElement(@NotNull PsiElement element, @NotNull String imageSpec) {
    return null;
  }
}
