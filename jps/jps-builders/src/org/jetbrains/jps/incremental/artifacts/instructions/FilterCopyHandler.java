/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental.artifacts.instructions;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.CompileContext;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author Sergey Evdokimov
 */
public class FilterCopyHandler extends FileCopyingHandler {

  private final FileFilter myFilter;

  public FilterCopyHandler(@NotNull FileFilter filter) {
    myFilter = filter;
  }

  @Override
  public void copyFile(@NotNull File from, @NotNull File to, @NotNull CompileContext context) throws IOException {
    FileUtil.copyContent(from, to);
  }

  @Override
  public void writeConfiguration(@NotNull PrintWriter out) {

  }

  @NotNull
  @Override
  public FileFilter createFileFilter() {
    return myFilter;
  }
}
