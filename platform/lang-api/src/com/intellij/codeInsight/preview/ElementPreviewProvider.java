package com.intellij.codeInsight.preview;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public interface ElementPreviewProvider {
  ExtensionPointName<ElementPreviewProvider> EP_NAME = ExtensionPointName.create("com.intellij.elementPreviewProvider");

  boolean isSupportedFile(@NotNull PsiFile psiFile);

  void show(@NotNull PsiElement element, @NotNull Editor editor, @NotNull Point point);

  void hide(@Nullable("if disposed") PsiElement element, @NotNull Editor editor);
}