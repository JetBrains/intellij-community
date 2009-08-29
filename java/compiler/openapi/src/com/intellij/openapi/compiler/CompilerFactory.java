package com.intellij.openapi.compiler;

import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * @author yole
 */
public interface CompilerFactory {
  ExtensionPointName<CompilerFactory> EP_NAME = ExtensionPointName.create("com.intellij.compilerFactory");
  
  Compiler[] createCompilers(final CompilerManager compilerManager);
}
