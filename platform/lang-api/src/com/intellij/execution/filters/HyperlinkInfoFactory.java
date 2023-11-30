// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public abstract class HyperlinkInfoFactory {
  public static @NotNull HyperlinkInfoFactory getInstance() {
    return ApplicationManager.getApplication().getService(HyperlinkInfoFactory.class);
  }

  public abstract @NotNull HyperlinkInfo createMultipleFilesHyperlinkInfo(@NotNull List<? extends VirtualFile> files,
                                                                          int line, @NotNull Project project);

  /**
   * Creates a hyperlink which points to several files with ability to calculate a position inside line
   * @param files list of files to navigate to (will be suggested to user)
   * @param line line number to navigate to
   * @param project a project
   * @param action an action to be performed once editor is opened
   * @return newly created HyperlinkInfo which navigates to given line and column
   */
  public abstract @NotNull HyperlinkInfo createMultipleFilesHyperlinkInfo(@NotNull List<? extends VirtualFile> files,
                                                                 int line,
                                                                 @NotNull Project project,
                                                                 @Nullable HyperlinkHandler action);

  /**
   * Creates a hyperlink that points to elements with ability to navigate to specific element within the file
   *
   * @param elements elements list
   * @return newly create HyperlinkInfo that navigates to given psi elements
   */
  public abstract @NotNull HyperlinkInfo createMultiplePsiElementHyperlinkInfo(@NotNull Collection<? extends PsiElement> elements);

  public interface HyperlinkHandler {
    void onLinkFollowed(@NotNull Project project, @NotNull VirtualFile psiFile, @NotNull Editor targetEditor, @Nullable Editor originalEditor);
  }
}
