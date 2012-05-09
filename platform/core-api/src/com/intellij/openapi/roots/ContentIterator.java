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
package com.intellij.openapi.roots;

import com.intellij.openapi.vfs.VirtualFile;

/**
 * Interface which can be used to receive the contents of a project.
 *
 * @see FileIndex#iterateContent(ContentIterator)
 */
public interface ContentIterator {
  /**
   * Processes the specified file or directory.
   *
   * @param fileOrDir the file or directory to process.
   * @return false if files processing should be stopped, true if it should be continued.
   */
  boolean processFile(VirtualFile fileOrDir);
}
