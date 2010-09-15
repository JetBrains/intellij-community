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

public class LibraryElement extends LibraryTableTreeContentElement {
  private final LibraryTableEditor myParentEditor;
  private final boolean myHasInvalidPaths;

  public LibraryElement(LibraryTableEditor parentEditor, final boolean hasInvalidPaths) {
    myParentEditor = parentEditor;
    myHasInvalidPaths = hasInvalidPaths;
  }

  public boolean isAnonymous() {
    final String name = myParentEditor.getLibraryEditor().getName();
    return name == null;
  }

  public boolean hasInvalidPaths() {
    return myHasInvalidPaths;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    return o instanceof LibraryElement;
  }

  public int hashCode() {
    return 0;
  }

  public LibraryTableTreeContentElement getParent() {
    return null;
  }

  public OrderRootType getOrderRootType() {
    return null;
  }

  public NodeDescriptor createDescriptor(final NodeDescriptor parentDescriptor, final LibraryTableEditor parentEditor) {
    return new LibraryElementDescriptor(parentDescriptor, this, parentEditor);
  }
}
