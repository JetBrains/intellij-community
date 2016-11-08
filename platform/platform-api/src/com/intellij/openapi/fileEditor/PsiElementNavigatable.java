/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;

public class PsiElementNavigatable implements Navigatable {
  private final SmartPsiElementPointer<PsiElement> myPointer;

  public PsiElementNavigatable(@NotNull PsiElement element) {
    myPointer = SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);
  }

  @Override
  public final void navigate(boolean requestFocus) {
    PsiElement element = myPointer.getElement();
    if (element != null && element.isValid()) {
      VirtualFile file = element.getContainingFile().getVirtualFile();
      if (file != null) {
        new Task.Backgroundable(element.getProject(), EditorBundle.message("editor.open.file.progress", file.getName())) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            if (element.isValid() && !myProject.isDisposed()) {
              int offset = ReadAction.compute(() -> element.getTextOffset());  // may trigger decompilation
              indicator.checkCanceled();
              OpenFileDescriptor descriptor = new OpenFileDescriptor(myProject, file, offset);
              ApplicationManager.getApplication().invokeLater(() -> descriptor.navigate(requestFocus), myProject.getDisposed());
            }
          }
        }.queue();
      }
    }
  }

  @Override
  public boolean canNavigate() {
    PsiElement element = myPointer.getElement();
    return element != null && element.isValid() && element.getContainingFile().getVirtualFile() != null;
  }

  @Override
  public boolean canNavigateToSource() {
    return canNavigate();
  }
}