package com.intellij.testIntegration;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface TestFinder {
  ExtensionPointName<TestFinder> EP_NAME = ExtensionPointName.create("com.intellij.testFinder");

  @Nullable
  PsiElement findSourceElement(PsiElement from);

  @NotNull
  Collection<PsiElement> findTestsForClass(PsiElement element);
  @NotNull
  Collection<PsiElement> findClassesForTest(PsiElement element);

  boolean isTest(PsiElement element);
}
