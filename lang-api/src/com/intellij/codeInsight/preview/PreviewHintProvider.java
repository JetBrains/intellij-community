package com.intellij.codeInsight.preview;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author yole
 */
public interface PreviewHintProvider {
  ExtensionPointName<PreviewHintProvider> EP_NAME = ExtensionPointName.create("com.intellij.previewHintProvider");

  boolean isSupportedFile(PsiFile file);

  @Nullable
  JComponent getPreviewComponent(@NotNull PsiElement element);
}
