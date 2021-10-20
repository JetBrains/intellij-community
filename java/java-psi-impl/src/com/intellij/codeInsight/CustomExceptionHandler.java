package com.intellij.codeInsight;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allow to specify that a given element handles given exception.
 *
 * Such elements won't be highlighted as unhandled.
 */
public abstract class CustomExceptionHandler {
  public static final ExtensionPointName<CustomExceptionHandler> KEY = ExtensionPointName.create("com.intellij.custom.exception.handler");

  /**
   * Checks if the exception produced by element
   * @param element place which produces exception (for example {@link com.intellij.psi.PsiCall})
   * @param exceptionType type of produced exception
   * @param topElement element at which exception should be handled
   */
  public abstract boolean isHandled(@Nullable PsiElement element, @NotNull PsiClassType exceptionType, PsiElement topElement);
}
