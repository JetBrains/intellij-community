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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Icons;

import javax.swing.*;
import java.awt.*;
import java.io.File;

class LibraryElementDescriptor extends NodeDescriptor<LibraryElement> {
  private final LibraryElement myElement;
  private final LibraryRootsComponent myParentEditor;

  public LibraryElementDescriptor(NodeDescriptor parentDescriptor, LibraryElement element, LibraryRootsComponent parentEditor) {
    super(null, parentDescriptor);
    myElement = element;
    myParentEditor = parentEditor;
  }

  public boolean update() {
    final String name;
    final Icon icon;
    final LibraryEditor libraryEditor = myParentEditor.getLibraryEditor();
    if (myElement.isAnonymous()) {
      final VirtualFile[] files = libraryEditor.getFiles(OrderRootType.CLASSES);
      if (files.length > 0) {
        name = files[0].getPresentableUrl();
        final String url = files[0].getUrl();
        icon = LibraryRootsComponent.getIconForUrl(url, true, libraryEditor.isJarDirectory(url));
      }
      else {
        final String[] urls = libraryEditor.getUrls(OrderRootType.CLASSES);
        if (urls.length > 0) {
          final String url = urls[0];
          name = LibraryRootsComponent.getPresentablePath(url).replace('/', File.separatorChar);
          icon = LibraryRootsComponent.getIconForUrl(url, false, libraryEditor.isJarDirectory(url));
        }
        else {
          name = ProjectBundle.message("library.empty.item"); // the library is anonymous, library.getName() == null
          icon = Icons.LIBRARY_ICON;
        }
      }
    }
    else {
      name = libraryEditor.getName();
      icon = Icons.LIBRARY_ICON;
    }
    myColor = myElement.hasInvalidPaths()? Color.RED : null;
    final boolean changed = !name.equals(myName) || !icon.equals(myClosedIcon);
    if (changed) {
      myName = name;
      myClosedIcon = myOpenIcon = icon;
    }
    return changed;
  }

  public LibraryElement getElement() {
    return myElement;
  }
}

