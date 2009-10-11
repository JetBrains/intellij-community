/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author: Eugene Zhuravlev
 * Date: Apr 1, 2003
 * Time: 5:58:41 PM
 */
package com.intellij.compiler.impl.javaCompiler;

import com.intellij.openapi.compiler.TranslatingCompiler;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public class OutputItemImpl implements TranslatingCompiler.OutputItem{

  private final String myOutputPath;
  private final VirtualFile mySourceFile;

  public OutputItemImpl(VirtualFile packageInfoFile) {
    this(null, packageInfoFile);
  }

  /**
   * @param outputPath absolute path of the output file ('/' slashes used)
   * @param sourceFile corresponding source file
   */
  public OutputItemImpl(@Nullable String outputPath, VirtualFile sourceFile) {
    myOutputPath = outputPath;
    mySourceFile = sourceFile;
  }

  public String getOutputPath() {
    return myOutputPath;
  }

  public VirtualFile getSourceFile() {
    return mySourceFile;
  }
}
