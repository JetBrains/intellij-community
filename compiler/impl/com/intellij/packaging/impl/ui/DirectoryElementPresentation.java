package com.intellij.packaging.impl.ui;

import com.intellij.packaging.impl.elements.DirectoryPackagingElement;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.packaging.ui.PackagingElementWeights;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class DirectoryElementPresentation extends PackagingElementPresentation {
  private final DirectoryPackagingElement myElement;

  public DirectoryElementPresentation(DirectoryPackagingElement element) {
    myElement = element;
  }

  public String getPresentableName() {
    return myElement.getDirectoryName();
  }

  public void render(@NotNull ColoredTreeCellRenderer renderer) {
    renderer.setIcon(Icons.FOLDER_ICON);
    renderer.append(myElement.getDirectoryName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  @Override
  public double getWeight() {
    return PackagingElementWeights.DIRECTORY;
  }
}
