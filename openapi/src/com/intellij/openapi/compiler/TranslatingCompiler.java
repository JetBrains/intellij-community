/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
