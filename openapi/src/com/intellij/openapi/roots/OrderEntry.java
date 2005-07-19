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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;

/**
 *  @author dsl
 */
public interface OrderEntry extends Synthetic, Comparable<OrderEntry> {
  OrderEntry[] EMPTY_ARRAY = new OrderEntry[0];

  /**
   * Returns list of root <code>VirtualFile</code>s of given type for this entry.
   * Those files should be traversed in order they are returned in.
   * Note that actual OrderEntry (as seen in UI) may contain also contain invalid roots.
   * If you want to get list of all roots, use <code>getURLs</code> method. <br>
   *
   * Note that list of roots is project dependent.
   *
   * @param type  required root type.
   * @return list of virtual files.
   * @see #getUrls(OrderRootType)
   */
  VirtualFile[] getFiles(OrderRootType type);

  /**
   * Returns list of roots of given type for this entry. To validate returned roots,
   * use <code>{@link com.intellij.openapi.vfs.VirtualFileManager#findFileByUrl(java.lang.String)}</code> <br>
   *
   * Note that list of roots is project-dependent.
   *
   * @return
   */
  String[] getUrls(OrderRootType rootType);

  /**
   * @return name of this OrderEntry to be shown to user.
   */
  String getPresentableName();

  /**
   * Checks whether this order entry is invalid for some reason. Note that entry being valid
   * does not necessarily mean that all its roots are valid.
   * @return true if entry is valid, false otherwise.
   */
  boolean isValid();

  Module getOwnerModule();

  <R> R accept(RootPolicy<R> policy, R initialValue);
}
