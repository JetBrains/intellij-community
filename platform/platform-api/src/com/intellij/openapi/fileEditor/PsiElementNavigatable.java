/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.util.Conditions.or;

public class PsiElementNavigatable implements Navigatable {
  private final SmartPsiElementPointer<PsiElement> myPointer;

  public PsiElementNavigatable(@NotNull PsiElement element) {
    myPointer = SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);
  }

  @Override
  public final void navigate(boolean requestFocus) {
    PsiElement element = getElement();
    if (element != null) {
      VirtualFile file = element.getContainingFile().getVirtualFile();
      if (file != null) {
        new Task.Modal(element.getProject(), EditorBundle.message("editor.open.file.progress", file.getName()), true) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            int offset = ReadAction.compute(() -> element.isValid() ? element.getTextOffset() : -1);  // may trigger decompilation
            indicator.checkCanceled();
            if (offset >= 0) {
              Navigatable descriptor = PsiNavigationSupport.getInstance().createNavigatable(myProject, file, offset);
              Condition<?> expired = or(myProject.getDisposed(), o -> !file.isValid());
              ApplicationManager.getApplication().invokeLater(() -> descriptor.navigate(requestFocus), expired);
            }
          }
        }.queue();
      }
    }
  }

  @Override
  public boolean canNavigate() {
    PsiElement element = getElement();
    return element != null && element.getContainingFile().getVirtualFile() != null;
  }

  private PsiElement getElement() {
    PsiElement element = myPointer.getElement();
    if (element != null && element.isValid()) {
      PsiElement navigationElement = element.getNavigationElement();
      return navigationElement != null ? navigationElement : element;
    }

    return null;
  }

  @Override
  public boolean canNavigateToSource() {
    return canNavigate();
  }
}