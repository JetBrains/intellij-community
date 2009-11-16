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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.OrderedSet;
import gnu.trove.TObjectHashingStrategy;

import java.util.Collections;
import java.util.List;

public class DirectoryInfo {
  public Module module; // module to which content it belongs or null
  public boolean isInModuleSource; // true if files in this directory belongs to sources of the module (if field 'module' is not null)
  public boolean isTestSource; // (makes sense only if isInModuleSource is true)
  public boolean isInLibrarySource; // true if it's a directory with sources of some library
  public VirtualFile libraryClassRoot; // class root in library
  public VirtualFile contentRoot;
  public VirtualFile sourceRoot;

  /**
   *  orderEntry to (classes of) which a directory belongs
   */
  private List<OrderEntry> orderEntries = null;

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DirectoryInfo)) return false;

    final DirectoryInfo info = (DirectoryInfo)o;

    if (isInLibrarySource != info.isInLibrarySource) return false;
    if (isInModuleSource != info.isInModuleSource) return false;
    if (isTestSource != info.isTestSource) return false;
    if (module != null ? !module.equals(info.module) : info.module != null) return false;
    if (orderEntries != null ? !orderEntries.equals(info.orderEntries) : info.orderEntries != null) return false;
    if (!Comparing.equal(libraryClassRoot, info.libraryClassRoot)) return false;
    if (!Comparing.equal(contentRoot, info.contentRoot)) return false;
    if (!Comparing.equal(sourceRoot, info.sourceRoot)) return false;

    return true;
  }

  public int hashCode() {
    throw new UnsupportedOperationException("DirectoryInfo shall not be used as a key to HashMap");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "DirectoryInfo{" +
           "module=" + module +
           ", isInModuleSource=" + isInModuleSource +
           ", isTestSource=" + isTestSource +
           ", isInLibrarySource=" + isInLibrarySource +
           ", libraryClassRoot=" + libraryClassRoot +
           ", contentRoot=" + contentRoot +
           ", sourceRoot=" + sourceRoot +
           "}";
  }

  public List<OrderEntry> getOrderEntries() {
    return orderEntries == null ? Collections.<OrderEntry>emptyList() : orderEntries;
  }

  @SuppressWarnings({"unchecked"})
  public void addOrderEntries(final List<OrderEntry> orderEntries,
                              final DirectoryInfo parentInfo,
                              final List<OrderEntry> oldParentEntries) {
    if (this.orderEntries == null) {
      this.orderEntries = orderEntries;
    }
    else if (parentInfo != null && oldParentEntries == this.orderEntries) {
      this.orderEntries = parentInfo.getOrderEntries();
    }
    else {
      List<OrderEntry> tmp = new OrderedSet<OrderEntry>(TObjectHashingStrategy.CANONICAL);
      tmp.addAll(this.orderEntries);
      tmp.addAll(orderEntries);
      this.orderEntries = tmp;
    }
  }
}
