package com.intellij.testIntegration;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public interface TestFrameworkDescriptor {
  ExtensionPointName<TestFrameworkDescriptor> EXTENSION_NAME = ExtensionPointName.create("com.intellij.testFrameworkDescriptor");

  String getName();

  boolean isLibraryAttached(Module m);

  String getLibraryPath();

  @Nullable
  String getDefaultSuperClass();

  @Nullable
  String getSetUpAnnotation();

  @Nullable
  String getTearDownAnnotation();

  @Nullable
  String getTestAnnotation();

  boolean isTestClass(PsiElement element);
}
