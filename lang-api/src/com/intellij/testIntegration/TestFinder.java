package com.intellij.testIntegration;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;

import java.util.Collection;

public interface TestFinder {
  ExtensionPointName<TestFinder> EP_NAME = ExtensionPointName.create("com.intellij.testFinder");

  Collection<PsiElement> findTestsForClass(PsiElement element);
  Collection<PsiElement> findClassesForTest(PsiElement element);

  boolean isTest(PsiElement element);
}
