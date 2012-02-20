package com.intellij.compiler.impl.resourceCompiler;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

public abstract class ResourceCompilerExtension {
  public static final ExtensionPointName<ResourceCompilerExtension> EP_NAME =
    ExtensionPointName.create("com.intellij.compiler.resourceCompilerExtension");

  public boolean skipStandardResourceCompiler(final @NotNull Module module) {
    return false;
  }
}
