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
import com.intellij.pom.Navigatable;

/**
 * Describes a single compiler message that is shown in compiler message view
 */
public interface CompilerMessage {
  CompilerMessage[] EMPTY_ARRAY = new CompilerMessage[0];

  /**
   * @return a category this message belongs to (error, warning, information)
   */
  CompilerMessageCategory getCategory();

  /**
   * @return message text
   */
  String getMessage();

  /**
   *
   * @return Navigatable object allowing to navigate to the message source
   */
  Navigatable getNavigatable();

  /**
   * @return the file to which the message applies
   */
  VirtualFile getVirtualFile();

  /**
   * @return location prefix prepended to message while exporting compilation results to text
   */
  String getExportTextPrefix();

  /**
   * @return location prefix prepended to message while rendering compilation results in UI
   */
  String getRenderTextPrefix();
}
