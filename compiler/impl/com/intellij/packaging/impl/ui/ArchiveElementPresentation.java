package com.intellij.packaging.impl.ui;

import com.intellij.packaging.impl.elements.ArchivePackagingElement;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.packaging.ui.PackagingElementWeights;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Icons;
import com.intellij.ide.projectView.PresentationData;
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

  public void render(@NotNull PresentationData presentationData, SimpleTextAttributes mainAttributes, SimpleTextAttributes commentAttributes) {
    presentationData.setIcons(Icons.JAR_ICON);
    presentationData.addText(myElement.getArchiveFileName(), mainAttributes);
  }

  @Override
  public int getWeight() {
    return PackagingElementWeights.FILE_COPY;
  }
}