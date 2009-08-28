package com.intellij.codeInsight.navigation;

import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface GotoTargetRendererProvider {
  ExtensionPointName<GotoTargetRendererProvider> EP_NAME = ExtensionPointName.create("com.intellij.gotoTargetRendererProvider");

  @Nullable
  PsiElementListCellRenderer getRenderer(PsiElement[] elements);
}
