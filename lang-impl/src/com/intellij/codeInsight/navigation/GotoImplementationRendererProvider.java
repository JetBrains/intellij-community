package com.intellij.codeInsight.navigation;

import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface GotoImplementationRendererProvider {
  ExtensionPointName<GotoImplementationRendererProvider> EP_NAME = ExtensionPointName.create("com.intellij.gotoImplementationRendererProvider");

  @Nullable
  PsiElementListCellRenderer getRenderer(PsiElement[] elements);

  String getChooserTitle(String name, PsiElement[] elements);
}
