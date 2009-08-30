package com.intellij.packaging.impl.ui;

import com.intellij.packaging.impl.elements.DirectoryPackagingElement;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.packaging.ui.PackagingElementWeights;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Icons;
import com.intellij.ide.projectView.PresentationData;
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

  public void render(@NotNull PresentationData presentationData, SimpleTextAttributes mainAttributes, SimpleTextAttributes commentAttributes) {
    presentationData.setOpenIcon(Icons.DIRECTORY_OPEN_ICON);
    presentationData.setClosedIcon(Icons.DIRECTORY_CLOSED_ICON);
    presentationData.addText(myElement.getDirectoryName(), mainAttributes);
  }

  @Override
  public int getWeight() {
    return PackagingElementWeights.DIRECTORY;
  }
}
