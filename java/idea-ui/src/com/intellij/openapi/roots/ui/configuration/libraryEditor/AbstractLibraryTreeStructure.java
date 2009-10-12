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

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author nik
 */
public abstract class AbstractLibraryTreeStructure extends AbstractTreeStructure {
  protected NodeDescriptor myRootElementDescriptor;
  protected final LibraryTableEditor myParentEditor;

  public AbstractLibraryTreeStructure(LibraryTableEditor parentElement) {
    myParentEditor = parentElement;
  }

  public void commit() {
  }

  public boolean hasSomethingToCommit() {
    return false;
  }

  protected Object[] buildItems(LibraryTableTreeContentElement parent, Library library, OrderRootType orderRootType) {
    ArrayList<ItemElement> items = new ArrayList<ItemElement>();
    final LibraryEditor libraryEditor = myParentEditor.getLibraryEditor(library);
    final String[] urls = libraryEditor.getUrls(orderRootType).clone();
    Arrays.sort(urls, LibraryTableEditor.ourUrlComparator);
    for (String url : urls) {
      items.add(new ItemElement(parent, library, url, orderRootType, libraryEditor.isJarDirectory(url), libraryEditor.isValid(url, orderRootType)));
    }
    return items.toArray();
  }

  public Object getParentElement(Object element) {
    Object rootElement = getRootElement();
    if (element == rootElement) {
      return null;
    }
    if (element instanceof LibraryTableTreeContentElement) {
      return ((LibraryTableTreeContentElement)element).getParent();
    }
    return rootElement;
  }

  @NotNull
  public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
    if (element == getRootElement()) {
      return myRootElementDescriptor;
    }
    if (element instanceof LibraryTableTreeContentElement) {
      return ((LibraryTableTreeContentElement)element).createDescriptor(parentDescriptor, myParentEditor);
    }
    return null;
  }
}
