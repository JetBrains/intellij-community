/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.compiler;

import com.intellij.openapi.vfs.VirtualFile;

/**
 * Describes compilers that translate one type of files into another (e.g. .java -> .class)
 */
public interface TranslatingCompiler extends Compiler {
  OutputItem[] EMPTY_OUTPUT_ITEM_ARRAY = new OutputItem[0];

  interface OutputItem {
    /**
     * @return absolute path of the output file ('/' slashes used)
     */
    String getOutputPath();

    /**
     * @return the source file to be compiled
     */
    VirtualFile getSourceFile();
    /**
     * @return the output directory for this output item
     */
    String getOutputRootDirectory();
  }

  interface ExitStatus {
    /**
     * @return all output items that were successfully compiled
     */
    OutputItem[] getSuccessfullyCompiled();
    /**
     * @return a list of virtual files that should be considered as "modified" next time compilation is invoked
     */
    VirtualFile[] getFilesToRecompile();
  }

  /**
   * @return true if can compile the file, false otherwise
   */
  boolean isCompilableFile(VirtualFile file, CompileContext context);

  /**
   * @param files source files to compile
   * @return successfully compiled sources
   */
  ExitStatus compile(CompileContext context, VirtualFile[] files);
}
