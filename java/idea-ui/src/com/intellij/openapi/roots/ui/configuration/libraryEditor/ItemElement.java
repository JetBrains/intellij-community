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
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.roots.OrderRootType;


class ItemElement extends LibraryTableTreeContentElement {
  private final LibraryTableTreeContentElement myParent;
  private final String myUrl;
  private final OrderRootType myRootType;
  private final boolean myIsJarDirectory;
  private final boolean myValid;

  public ItemElement(LibraryTableTreeContentElement parent, String url, OrderRootType rootType, final boolean isJarDirectory,
                     boolean isValid) {
    myParent = parent;
    myUrl = url;
    myRootType = rootType;
    myIsJarDirectory = isJarDirectory;
    myValid = isValid;
  }

  public LibraryTableTreeContentElement getParent() {
    return myParent;
  }

  public OrderRootType getOrderRootType() {
    return null;
  }

  public NodeDescriptor createDescriptor(final NodeDescriptor parentDescriptor, final LibraryRootsComponent parentEditor) {
    return new ItemElementDescriptor(parentDescriptor, this);
  }

  public String getUrl() {
    return myUrl;
  }
    
  public boolean isJarDirectory() {
    return myIsJarDirectory;
  }
  
  public boolean isValid() {
    return myValid;
  }

  public OrderRootType getRootType() {
    return myRootType;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ItemElement)) return false;

    final ItemElement itemElement = (ItemElement)o;

    if (!myParent.equals(itemElement.myParent)) return false;
    if (!myRootType.equals(itemElement.myRootType)) return false;
    if (!myUrl.equals(itemElement.myUrl)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myParent.hashCode();
    result = 29 * result + myUrl.hashCode();
    result = 29 * result + myRootType.hashCode();
    return result;
  }
}
