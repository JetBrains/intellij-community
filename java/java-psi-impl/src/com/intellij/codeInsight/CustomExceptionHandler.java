package com.intellij.codeInsight;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CustomExceptionHandler {
  public static final ExtensionPointName<CustomExceptionHandler> KEY = ExtensionPointName.create("com.intellij.custom.exception.handler");

  public abstract boolean isHandled(@Nullable PsiElement element, @NotNull PsiClassType exceptionType, PsiElement topElement);
}
