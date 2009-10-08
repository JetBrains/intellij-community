package com.intellij.packaging.impl.ui;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.compiler.CompilerBundle;
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
public class DirectoryCopyPresentation extends PackagingElementPresentation {
  private final String mySourcePath;
  private final String mySourceFileName;
  private final VirtualFile myFile;

  public DirectoryCopyPresentation(String filePath) {
    mySourceFileName = PathUtil.getFileName(filePath);

    String parentPath;
    myFile = LocalFileSystem.getInstance().findFileByPath(filePath);
    if (myFile != null) {
      final VirtualFile parent = myFile.getParent();
      parentPath = parent != null ? FileUtil.toSystemDependentName(parent.getPath()) : "";
    }
    else {
      parentPath = FileUtil.toSystemDependentName(PathUtil.getParentPath(filePath));
    }

    mySourcePath = parentPath;
  }

  public String getPresentableName() {
    return mySourceFileName;
  }

  public void render(@NotNull PresentationData presentationData, SimpleTextAttributes mainAttributes, SimpleTextAttributes commentAttributes) {
    presentationData.setIcons(PackagingElementFactoryImpl.DirectoryCopyElementType.COPY_OF_FOLDER_ICON);
    if (myFile == null || !myFile.isDirectory()) {
      mainAttributes = SimpleTextAttributes.ERROR_ATTRIBUTES;
      final VirtualFile parentFile = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(mySourcePath));
      if (parentFile == null) {
        commentAttributes = SimpleTextAttributes.ERROR_ATTRIBUTES;
      }
    }
    presentationData.addText(CompilerBundle.message("node.text.0.directory.content", mySourceFileName), mainAttributes);
    presentationData.addText(" (" + mySourcePath + ")", commentAttributes);
  }

  @Override
  public int getWeight() {
    return PackagingElementWeights.DIRECTORY_COPY;
  }
}