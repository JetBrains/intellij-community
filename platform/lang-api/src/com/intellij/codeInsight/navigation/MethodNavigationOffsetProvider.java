package com.intellij.codeInsight.navigation;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface MethodNavigationOffsetProvider {
  ExtensionPointName<MethodNavigationOffsetProvider> EP_NAME = ExtensionPointName.create("com.intellij.methodNavigationOffsetProvider");

  @Nullable
  int[] getMethodNavigationOffsets(PsiFile file, int caretOffset);
}
