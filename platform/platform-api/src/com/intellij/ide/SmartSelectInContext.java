// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.ide;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey Malenkov
 */
public class SmartSelectInContext extends FileSelectInContext {
  private final SmartPsiElementPointer<PsiElement> pointer;

  public SmartSelectInContext(@NotNull PsiFile file, @NotNull PsiElement element) {
    super(file.getProject(), file.getViewProvider().getVirtualFile());
    pointer = SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);
  }

  public SmartSelectInContext(@NotNull PsiFile file, @NotNull PsiElement element, FileEditorProvider provider) {
    super(file.getProject(), file.getViewProvider().getVirtualFile(), provider);
    pointer = SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);
  }

  @Override
  public Object getSelectorInFile() {
    return pointer.getElement();
  }

  public PsiFile getPsiFile() {
    Object selector = pointer.getElement();
    return selector instanceof PsiFile ? (PsiFile)selector : null;
  }
}
