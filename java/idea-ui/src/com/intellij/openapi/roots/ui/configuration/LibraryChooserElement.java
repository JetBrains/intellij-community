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
      LOG.assertTrue(false, "Both library and order entry are null");
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