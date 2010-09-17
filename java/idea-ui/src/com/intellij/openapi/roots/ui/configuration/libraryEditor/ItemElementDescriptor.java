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

import java.awt.*;
import java.io.File;

class ItemElementDescriptor extends NodeDescriptor<ItemElement> {
    private final ItemElement myElement;

    public ItemElementDescriptor(NodeDescriptor parentDescriptor, ItemElement element) {
      super(null, parentDescriptor);
      myElement = element;
      final String url = element.getUrl();
      myName = LibraryRootsComponent.getPresentablePath(url).replace('/', File.separatorChar);
      myOpenIcon = myClosedIcon = LibraryRootsComponent.getIconForUrl(url, element.isValid(), element.isJarDirectory());
    }

    public boolean update() {
      Color color = myElement.isValid()? Color.BLACK : Color.RED;
      final boolean changes = !color.equals(myColor);
      myColor = color;
      return changes;
    }

    public ItemElement getElement() {
      return myElement;
    }
  }
