/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.options;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Base interface to export a scheme.
 *
 * @author Rustam Vishnyakov
 */
public interface SchemeExporter <T extends Scheme> {
  /**
   * Writes a scheme to existing <code>targetFile</code>. An implementor may use <code>targetFile.getOutputStream()</code> method to
   * obtain a stream to write to.
   *
   * @param scheme     The scheme to export.
   * @param targetFile The target virtual file.
   * @throws SchemeExporterException
   */
  void exportScheme(@NotNull T scheme, @NotNull VirtualFile targetFile) throws SchemeExporterException;

  /**
   * @return Target file extension without a dot, for example "xml".
   */
  String getExtension();
}
