package com.intellij.testIntegration;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.Nullable;

public interface CreateTestProvider {
  ExtensionPointName<CreateTestProvider> EXTENSION_NAME = ExtensionPointName.create("com.intellij.codeInsight.createTestProvider");

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
}
