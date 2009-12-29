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
package com.intellij.javadoc.actions;

import com.intellij.javadoc.JavadocGenerationManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;

public final class GenerateJavadocAction extends AnAction{

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    final PsiDirectory dir = getDirectoryFromContext(dataContext);
    JavadocGenerationManager.getInstance(project).generateJavadoc(dir, dataContext);
  }

  public void update(AnActionEvent event){
    final Presentation presentation = event.getPresentation();
    presentation.setEnabled(PlatformDataKeys.PROJECT.getData(event.getDataContext()) != null);
  }

  private static PsiDirectory getDirectoryFromContext(final DataContext dataContext) {
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) return null;
    final Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    if (editor != null){
      PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (psiFile != null) return psiFile.getContainingDirectory();
    } else {
      PsiElement element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
      if (element != null) {
        if (element instanceof PsiDirectory) return (PsiDirectory)element;
        else{
          PsiFile psiFile = element.getContainingFile();
          if (psiFile != null) return psiFile.getContainingDirectory();
        }
      } else {
        //This is the case with GUI designer
        VirtualFile virtualFile = PlatformDataKeys.VIRTUAL_FILE.getData(dataContext);
        if (virtualFile != null && virtualFile.isValid()) {
          PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
          if (psiFile != null) return psiFile.getContainingDirectory();
        }
      }
    }
    return null;
  }

}
