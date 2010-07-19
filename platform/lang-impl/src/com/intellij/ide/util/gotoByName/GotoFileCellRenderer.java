/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.ide.util.gotoByName;

import com.intellij.ide.util.PlatformModuleRendererFactory;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.FilePathSplittingPolicy;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class GotoFileCellRenderer extends PsiElementListCellRenderer<PsiFile>{
  private final int myMaxWidth;

  public GotoFileCellRenderer(int maxSize) {
    myMaxWidth = maxSize;
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    Font editorFont = new Font(scheme.getEditorFontName(), Font.PLAIN, scheme.getEditorFontSize());
    setFont(editorFont);
  }

  public String getElementText(PsiFile element) {
    return element.getName();
  }

  protected String getContainerText(PsiFile element, String name) {
    final PsiDirectory psiDirectory = element.getContainingDirectory();
    if (psiDirectory == null) return null; 
    final VirtualFile virtualFile = psiDirectory.getVirtualFile();
    final String relativePath = getRelativePath(virtualFile, element.getProject());
    if (relativePath == null) return "( " + File.separator + " )";
    String path = FilePathSplittingPolicy.SPLIT_BY_SEPARATOR.getOptimalTextForComponent(name + "          ", new File(relativePath), this, myMaxWidth);
    return "(" + path + ")";
  }

  @Nullable
  private static String getRelativePath(final VirtualFile virtualFile, final Project project) {
    String url = virtualFile.getPresentableUrl();
    if (project == null) {
      return url;
    }
    final VirtualFile baseDir = project.getBaseDir();
    if (baseDir != null) {
      //noinspection ConstantConditions
      final String projectHomeUrl = baseDir.getPresentableUrl();
      if (url.startsWith(projectHomeUrl)) {
        final String cont = url.substring(projectHomeUrl.length());
        if (cont.length() == 0) return null;
        url = "..." + cont;
      }
    }
    return url;
  }

  @Override
  protected DefaultListCellRenderer getRightCellRenderer() {
    final DefaultListCellRenderer rightRenderer = super.getRightCellRenderer();
    if (rightRenderer instanceof PlatformModuleRendererFactory.PlatformModuleRenderer) {
      // that renderer will display file path, but we're showing it ourselves - no need to show twice
      return null;
    }
    return rightRenderer;
  }

  protected int getIconFlags() {
    return 0;
  }
}