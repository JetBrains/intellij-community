package com.intellij.packaging.impl.ui;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.impl.elements.PackagingElementFactoryImpl;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.packaging.ui.PackagingElementWeights;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class FileCopyPresentation extends PackagingElementPresentation {
  private final String mySourcePath;
  private final String myOutputFileName;
  private final VirtualFile myFile;

  public FileCopyPresentation(String filePath, String outputFileName) {
    myOutputFileName = outputFileName;

    String parentPath;
    myFile = LocalFileSystem.getInstance().findFileByPath(filePath);
    if (myFile != null) {
      final VirtualFile parent = myFile.getParent();
      parentPath = parent != null ? FileUtil.toSystemDependentName(parent.getPath()) : "";
    }
    else {
      parentPath = FileUtil.toSystemDependentName(PathUtil.getParentPath(filePath));
    }

    String sourceFileName = PathUtil.getFileName(filePath);
    if (!sourceFileName.equals(myOutputFileName)) {
      mySourcePath = parentPath + "/" + sourceFileName;
    }
    else {
      mySourcePath = parentPath;
    }
  }

  public String getPresentableName() {
    return myOutputFileName;
  }

  public void render(@NotNull PresentationData presentationData, SimpleTextAttributes mainAttributes, SimpleTextAttributes commentAttributes) {
    if (myFile != null && !myFile.isDirectory()) {
      presentationData.setIcons(myFile.getIcon());
      presentationData.addText(myOutputFileName, mainAttributes);
      presentationData.addText(" (" + mySourcePath + ")", commentAttributes);
    }
    else {
      presentationData.setIcons(PackagingElementFactoryImpl.FileCopyElementType.ICON);
      presentationData.addText(myOutputFileName, SimpleTextAttributes.ERROR_ATTRIBUTES);
      final VirtualFile parentFile = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(mySourcePath));
      presentationData.addText("(" + mySourcePath + ")",
                      parentFile != null ? commentAttributes : SimpleTextAttributes.ERROR_ATTRIBUTES);
    }
  }

  @Override
  public int getWeight() {
    return PackagingElementWeights.FILE_COPY;
  }
}
