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
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.OrderRootTypeUIFactory;
import com.intellij.util.ArrayUtil;

import java.util.ArrayList;

public class LibraryTreeStructure extends AbstractLibraryTreeStructure {
  private final LibraryElement myRootElement;

  public LibraryTreeStructure(LibraryTableEditor parentElement, Library library) {
    super(parentElement);
    myRootElement = new LibraryElement(library, myParentEditor, false);
    myRootElementDescriptor = new NodeDescriptor(null, null) {
      public boolean update() {
        myName = ProjectBundle.message("library.root.node");
        return false;
      }
      public Object getElement() {
        return myRootElement;
      }
    };
  }

  public Object getRootElement() {
    return myRootElement;
  }

  public Object[] getChildElements(Object element) {
    if (element == myRootElement) {
      ArrayList<LibraryTableTreeContentElement> elements = new ArrayList<LibraryTableTreeContentElement>(3);
      final Library library = myRootElement.getLibrary();
      final LibraryEditor parentEditor = myParentEditor.getLibraryEditor(library);
      for (OrderRootType type : OrderRootType.getAllTypes()) {
        final String[] urls = parentEditor.getUrls(type);
        if (urls.length > 0) {
          elements.add(OrderRootTypeUIFactory.FACTORY.getByKey(type).createElement(myRootElement));
        }
      }
      return elements.toArray();
    }

    if (element instanceof LibraryTableTreeContentElement) {
      final LibraryTableTreeContentElement contentElement = (LibraryTableTreeContentElement)element;
      final LibraryTableTreeContentElement parentElement = contentElement.getParent();
      if (parentElement instanceof LibraryElement) {
        return buildItems(contentElement, ((LibraryElement)parentElement).getLibrary(), contentElement.getOrderRootType());
      }
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public Library getLibrary() {
    return myRootElement.getLibrary();
  }
}
