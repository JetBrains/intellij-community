package com.intellij.codeInsight;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Any {@link com.intellij.psi.PsiElement} could be target target element.
 * Use this extension point to inject strategy that "knows" which elements are target.
 *
 * @author Ilya.Kazakevich
 */
public interface TargetElementExtensionPoint {
  ExtensionPointName<TargetElementExtensionPoint> EP_NAME = ExtensionPointName.create("com.intellij.targetElement");

  /**
   * Finds nearest parent that could be target.
   * @param element element to find nearest target
   * @return nearest target element or null if no element found.
   */
  @Nullable
  PsiElement getNearestTargetElement(@NotNull PsiElement element);
}
