package com.intellij.psi;

import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * @author yole
 */
public interface ElementDescriptionProvider {
  ExtensionPointName<ElementDescriptionProvider> EP_NAME = ExtensionPointName.create("com.intellij.elementDescriptionProvider");
  
  @Nullable
  String getElementDescription(PsiElement element, @Nullable ElementDescriptionLocation location);
}
