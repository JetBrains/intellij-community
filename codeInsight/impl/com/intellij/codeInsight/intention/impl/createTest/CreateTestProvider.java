package com.intellij.codeInsight.intention.impl.createTest;

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
}
