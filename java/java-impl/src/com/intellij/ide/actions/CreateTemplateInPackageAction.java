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

package com.intellij.ide.actions;

import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
 */
public abstract class CreateTemplateInPackageAction<T extends PsiElement> extends CreateTemplateAction<T> {
  private final boolean myinSourceOnly;

  protected CreateTemplateInPackageAction(String text, String description, Icon icon, boolean inSourceOnly) {
    super(text, description, icon);
    myinSourceOnly = inSourceOnly;
  }

  @Override
  @Nullable
  protected T createFile(String name, String templateName, PsiDirectory dir) {
    return checkOrCreate(name, dir, templateName, false);
  }

  @Override
  protected void checkBeforeCreate(String name, String templateName, PsiDirectory dir) {
    checkOrCreate(name, dir, templateName, true);
  }

  @Nullable
  protected abstract PsiElement getNavigationElement(@NotNull T createdElement);

  @Override
  protected boolean isAvailable(final DataContext dataContext) {
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (project == null || view == null || view.getDirectories().length == 0) {
      return false;
    }

    if (!myinSourceOnly) {
      return true;
    }

    ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    for (PsiDirectory dir : view.getDirectories()) {
      if (projectFileIndex.isInSourceContent(dir.getVirtualFile()) && JavaDirectoryService.getInstance().getPackage(dir) != null) {
        return true;
      }
    }

    return false;
  }

  @Nullable
   private T checkOrCreate(String newName, PsiDirectory directory, String templateName, boolean check) throws IncorrectOperationException {
     PsiDirectory dir = directory;
     String className = newName;

     if (newName.contains(".")) {
       String[] names = newName.split("\\.");

       for (int i = 0; i < names.length - 1; i++) {
         String name = names[i];
         PsiDirectory subDir = dir.findSubdirectory(name);

         if (subDir == null) {
           if (check) {
             dir.checkCreateSubdirectory(name);
             return null;
           }

           subDir = dir.createSubdirectory(name);
         }

         dir = subDir;
       }

       className = names[names.length - 1];
     }

    if (check) {
      doCheckCreate(dir, className, templateName);
      return null;
    }
     return doCreate(dir, className, templateName);
   }

  protected void doCheckCreate(PsiDirectory dir, String className, String templateName) throws IncorrectOperationException {
    JavaDirectoryService.getInstance().checkCreateClass(dir, className);
  }

  protected abstract T doCreate(final PsiDirectory dir, final String className, String templateName) throws IncorrectOperationException;

}
