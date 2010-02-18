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

/**
 * @author cdr
 */
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.util.Icons;

import javax.swing.*;
import java.awt.*;

public class LibraryChooserElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.configuration.LibraryChooserElement");

  private final String myName;
  private final Library myLibrary;
  private LibraryOrderEntry myOrderEntry;
  public static final ElementsChooser.ElementProperties VALID_LIBRARY_ELEMENT_PROPERTIES = new ElementsChooser.ElementProperties() {
    public Icon getIcon() {
      return Icons.LIBRARY_ICON;
    }
    public Color getColor() {
      return null;
    }
  };
  public static final ElementsChooser.ElementProperties INVALID_LIBRARY_ELEMENT_PROPERTIES = new ElementsChooser.ElementProperties() {
    public Icon getIcon() {
      return Icons.LIBRARY_ICON;
    }
    public Color getColor() {
      return Color.RED;
    }
  };

  public LibraryChooserElement(Library library, final LibraryOrderEntry orderEntry) {
    myLibrary = library;
    myOrderEntry = orderEntry;
    if (myLibrary == null && myOrderEntry == null) {
      LOG.error("Both library and order entry are null");
      myName = ProjectBundle.message("module.libraries.unknown.item");
    }
    else {
      myName = myLibrary != null? myLibrary.getName() : myOrderEntry.getLibraryName();
    }
  }

  public String toString() {
    return myName;
  }

  public String getName() {
    return myName;
  }

  public Library getLibrary() {
    return myLibrary;
  }

  public LibraryOrderEntry getOrderEntry() {
    return myOrderEntry;
  }

  public void setOrderEntry(LibraryOrderEntry orderEntry) {
    myOrderEntry = orderEntry;
  }

  public boolean isAttachedToProject() {
    return myOrderEntry != null;
  }

  public boolean isValid() {
    return myLibrary != null;
  }
}