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
package com.intellij.execution.filters;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public abstract class HyperlinkInfoFactory {
  @NotNull
  public static HyperlinkInfoFactory getInstance() {
    return ServiceManager.getService(HyperlinkInfoFactory.class);
  }

  @NotNull
  public abstract HyperlinkInfo createMultipleFilesHyperlinkInfo(@NotNull List<? extends VirtualFile> files,
                                                                 int line, @NotNull Project project);

  /**
   * Creates a hyperlink which points to several files with ability to calculate a position inside line
   * @param files list of files to navigate to (will be suggested to user)
   * @param line line number to navigate to
   * @param project a project
   * @param action an action to be performed once editor is opened
   * @return newly created HyperlinkInfo which navigates to given line and column
   */
  @NotNull
  public abstract HyperlinkInfo createMultipleFilesHyperlinkInfo(@NotNull List<? extends VirtualFile> files,
                                                                 int line,
                                                                 @NotNull Project project,
                                                                 @Nullable HyperlinkHandler action);

  /**
   * Creates a hyperlink that points to elements with ability to navigate to specific element within the file
   * 
   * @param elements elements list
   * @return newly create HyperlinkInfo that navigates to given psi elements
   */
  @NotNull
  public abstract HyperlinkInfo createMultiplePsiElementHyperlinkInfo(@NotNull Collection<? extends PsiElement> elements);
  
  public interface HyperlinkHandler {
    void onLinkFollowed(@NotNull PsiFile psiFile, @NotNull Editor targetEditor, @Nullable Editor originalEditor);
  }
}
