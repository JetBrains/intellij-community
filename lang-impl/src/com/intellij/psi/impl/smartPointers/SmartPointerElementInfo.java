package com.intellij.psi.impl.smartPointers;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

interface SmartPointerElementInfo {
  @Nullable
  Document getDocumentToSynchronize();

  void documentAndPsiInSync();

  @Nullable
  PsiElement restoreElement();
}
