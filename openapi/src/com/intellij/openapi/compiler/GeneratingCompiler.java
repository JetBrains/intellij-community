/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.compiler;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * A base interface for all compilers that generate new files. The generated files may be processed by other compilers.
 * Actual implementation should implement one of its subinterfaces. Currently only SourceGeneratingCompiler is available.
 * @see SourceGeneratingCompiler
 */
public interface GeneratingCompiler extends Compiler, ValidityStateFactory {
  interface GenerationItem {
    /**
     * @return relative to output directory path of a generated file
     */
    String getPath();

    /**
     * @return a serializable object describing dependencies of the generated file
     */
    ValidityState getValidityState();

    Module getModule();
  }

  /**
   * @return items describing all the files this compiler can generate
   */
  GenerationItem[] getGenerationItems(CompileContext context);

  /**
   * @param items what items to generate
   * @return successfully generated items
   */
  GenerationItem[] generate(CompileContext context, GenerationItem[] items, VirtualFile outputRootDirectory);
}
