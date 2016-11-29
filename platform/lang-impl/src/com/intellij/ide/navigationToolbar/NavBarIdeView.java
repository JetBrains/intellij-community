/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.navigationToolbar;

import com.intellij.ide.IdeView;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
* @author Konstantin Bulenkov
*/
public final class NavBarIdeView implements IdeView {
  private final NavBarPanel myPanel;

  public NavBarIdeView(NavBarPanel panel) {
    myPanel = panel;
  }

  @Override
  public void selectElement(PsiElement element) {
    myPanel.getModel().updateModel(element);

    if (element instanceof Navigatable) {
      final Navigatable navigatable = (Navigatable)element;
      if (navigatable.canNavigate()) {
        ((Navigatable)element).navigate(true);
      }
    }
    myPanel.hideHint();
  }

  @NotNull
  @Override
  public PsiDirectory[] getDirectories() {
    final PsiDirectory dir = myPanel.getSelectedElement(PsiDirectory.class);
    if (dir != null && dir.isValid()) {
      return new PsiDirectory[]{dir};
    }
    final PsiElement element = myPanel.getSelectedElement(PsiElement.class);
    if (element != null && element.isValid()) {
      final PsiFile file = element.getContainingFile();
      if (file != null) {
        final PsiDirectory psiDirectory = file.getContainingDirectory();
        return psiDirectory != null ? new PsiDirectory[]{psiDirectory} : PsiDirectory.EMPTY_ARRAY;
      }
    }
    final PsiDirectoryContainer directoryContainer = myPanel.getSelectedElement(PsiDirectoryContainer.class);
    if (directoryContainer != null) {
      return directoryContainer.getDirectories();
    }
    final Module module = myPanel.getSelectedElement(Module.class);
    if (module != null && !module.isDisposed()) {
      ArrayList<PsiDirectory> dirs = new ArrayList<>();
      final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
      final PsiManager psiManager = PsiManager.getInstance(myPanel.getProject());
      for (VirtualFile virtualFile : sourceRoots) {
        final PsiDirectory directory = psiManager.findDirectory(virtualFile);
        if (directory != null && directory.isValid()) {
          dirs.add(directory);
        }
      }
      return dirs.toArray(new PsiDirectory[dirs.size()]);
    }
    return PsiDirectory.EMPTY_ARRAY;
  }

  @Override
  public PsiDirectory getOrChooseDirectory() {
    return DirectoryChooserUtil.getOrChooseDirectory(this);
  }
}
