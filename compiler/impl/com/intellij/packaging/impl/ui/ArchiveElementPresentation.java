package com.intellij.packaging.impl.ui;

import com.intellij.packaging.impl.elements.ArchivePackagingElement;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.packaging.ui.PackagingElementWeights;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class ArchiveElementPresentation extends PackagingElementPresentation {
  private final ArchivePackagingElement myElement;

  public ArchiveElementPresentation(ArchivePackagingElement element) {
    myElement = element;
  }

  public String getPresentableName() {
    return myElement.getArchiveFileName();
  }

  public void render(@NotNull ColoredTreeCellRenderer renderer) {
    renderer.setIcon(Icons.JAR_ICON);
    renderer.append(myElement.getArchiveFileName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  @Override
  public double getWeight() {
    return PackagingElementWeights.FILE;
  }
}