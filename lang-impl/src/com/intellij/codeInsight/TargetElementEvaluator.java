package com.intellij.codeInsight;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author maxim
 */
public interface TargetElementEvaluator {
  ExtensionPointName<TargetElementEvaluator> EP_NAME = ExtensionPointName.create("com.intellij.targetElementEvaluator");

  boolean includeSelfInGotoImplementation(@NotNull PsiElement element);
}