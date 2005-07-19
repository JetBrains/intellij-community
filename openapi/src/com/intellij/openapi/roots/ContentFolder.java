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
package com.intellij.openapi.roots;

import com.intellij.openapi.vfs.VirtualFile;

/**
 *  @author dsl
 */
public interface ContentFolder extends Synthetic {
  /**
   * Returns virtual file for this source path's root.
   * @return null if source path is invalid
   */
  VirtualFile getFile();

  /**
   * @return this <code>ContentFolder</code>s {@link com.intellij.openapi.roots.ContentEntry}.
   */
  ContentEntry getContentEntry();

  String getUrl();
}
