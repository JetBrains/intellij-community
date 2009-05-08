package com.intellij.packaging.impl.ui;

import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.packaging.PackagingEditorUtil;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.packaging.ui.PackagingElementWeights;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class LibraryElementPresentation extends PackagingElementPresentation {
  private final Library myLibrary;
  private final String myName;

  public LibraryElementPresentation(String level, String name, Library library) {
    myLibrary = library;
    myName = name;
  }

  public String getPresentableName() {
    return myName;
  }

  public void render(@NotNull ColoredTreeCellRenderer renderer) {
    if (myLibrary != null) {
      PackagingEditorUtil.renderLibraryNode(renderer, myLibrary, SimpleTextAttributes.REGULAR_ATTRIBUTES, SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
    else {
      renderer.append(myName, SimpleTextAttributes.ERROR_ATTRIBUTES);
    }
  }

  @Override
  public double getWeight() {
    return PackagingElementWeights.LIBRARY;
  }

}
