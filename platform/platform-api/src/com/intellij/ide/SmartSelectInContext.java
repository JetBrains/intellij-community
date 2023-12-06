// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SmartSelectInContext extends FileSelectInContext {
  private final SmartPsiElementPointer<? extends PsiElement> pointer;

  public SmartSelectInContext(
    @NotNull Project project,
    @NotNull VirtualFile file,
    SmartPsiElementPointer<? extends PsiElement> pointer
  ) {
    super(project, file);
    this.pointer = pointer;
  }

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

  public @Nullable PsiFile getPsiFile() {
    Object selector = pointer.getElement();
    return selector instanceof PsiFile ? (PsiFile)selector : null;
  }

  @Override
  public String toString() {
    return "SmartSelectInContext{" +
           "pointer=" + pointer +
           "} " + super.toString();
  }
}
