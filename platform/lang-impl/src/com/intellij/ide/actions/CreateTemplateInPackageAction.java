/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package com.intellij.ide.actions;

import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import javax.swing.*;
import java.util.Set;

/**
 * @author peter
 */
public abstract class CreateTemplateInPackageAction<T extends PsiElement> extends CreateFromTemplateAction<T> {
  private Set<? extends JpsModuleSourceRootType<?>> mySourceRootTypes;

  protected CreateTemplateInPackageAction(String text, String description, Icon icon,
                                          final Set<? extends JpsModuleSourceRootType<?>> rootTypes) {
    super(text, description, icon);
    mySourceRootTypes = rootTypes;
  }

  @Override
  @Nullable
  protected T createFile(String name, String templateName, PsiDirectory dir) {
    return checkOrCreate(name, dir, templateName);
  }

  @Nullable
  protected abstract PsiElement getNavigationElement(@NotNull T createdElement);

  @Override
  protected boolean isAvailable(final DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (project == null || view == null || view.getDirectories().length == 0) {
      return false;
    }

    if (mySourceRootTypes == null) {
      return true;
    }

    ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    for (PsiDirectory dir : view.getDirectories()) {
      if (projectFileIndex.isUnderSourceRootOfType(dir.getVirtualFile(), mySourceRootTypes) && checkPackageExists(dir)) {
        return true;
      }
    }

    return false;
  }

  protected abstract boolean checkPackageExists(PsiDirectory directory);

  @Nullable
  private T checkOrCreate(String newName, PsiDirectory directory, String templateName) throws IncorrectOperationException {
    PsiDirectory dir = directory;
    String className = newName;

    final String extension = StringUtil.getShortName(templateName);
    if (StringUtil.isNotEmpty(extension)) {
      className = StringUtil.trimEnd(className, "." + extension);
    }

    if (className.contains(".")) {
      String[] names = className.split("\\.");

      for (int i = 0; i < names.length - 1; i++) {
        String name = names[i];
        PsiDirectory subDir = dir.findSubdirectory(name);

        if (subDir == null) {
          subDir = dir.createSubdirectory(name);
        }

        dir = subDir;
      }

      className = names[names.length - 1];
    }

    return doCreate(dir, className, templateName);
  }

  @Nullable
  protected abstract T doCreate(final PsiDirectory dir, final String className, String templateName) throws IncorrectOperationException;

}
