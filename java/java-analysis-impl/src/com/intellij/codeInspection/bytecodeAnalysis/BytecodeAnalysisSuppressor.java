// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * An extension point interface to allow suppressing the bytecode analysis for specific class files.
 * It's typically used when the class file is known to be a stub, which is replaced with an actual 
 * implementation at runtime. Analyzing such stubs may produce incorrect results
 * (e.g., incorrect purity contract may be inferred for impure methods).
 */
public interface BytecodeAnalysisSuppressor {
  ExtensionPointName<BytecodeAnalysisSuppressor> EP_NAME = ExtensionPointName.create("com.intellij.lang.jvm.bytecodeAnalysisSuppressor");

  /**
   * @return suppressor version. Override and increase the number every time the suppression algorithm changes.
   */
  default int getVersion() {
    return 1;
  }
  
  /**
   * @param file file to check. It points to a class file, usually inside a jar
   * @return true if the analysis for a given class file should be suppressed.
   */
  boolean shouldSuppress(@NotNull VirtualFile file);
}
