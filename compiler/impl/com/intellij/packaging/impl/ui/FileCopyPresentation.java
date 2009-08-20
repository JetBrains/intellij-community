package com.intellij.packaging.impl.ui;

import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.packaging.ui.PackagingElementWeights;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ide.projectView.PresentationData;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public class FileCopyPresentation extends PackagingElementPresentation {
  private static final Icon COPY_OF_FOLDER_ICON = IconLoader.getIcon("/nodes/copyOfFolder.png");
  private final String mySourcePath;
  private final String myOutputFileName;
  private final String mySourceFileName;
  private final VirtualFile myFile;
  private final boolean myIsDirectory;

  public FileCopyPresentation(String filePath, String outputFileName) {
    mySourceFileName = StringUtil.getShortName(filePath, '/');
    myOutputFileName = outputFileName;

    String parentPath;
    myFile = LocalFileSystem.getInstance().findFileByPath(filePath);
    if (myFile != null) {
      final VirtualFile parent = myFile.getParent();
      parentPath = parent != null ? FileUtil.toSystemDependentName(parent.getPath()) : "";
      myIsDirectory = myFile.isDirectory();
    }
    else {
      parentPath = FileUtil.toSystemDependentName(StringUtil.getPackageName(filePath, '/'));
      myIsDirectory = false;
    }

    if (!myIsDirectory && !mySourceFileName.equals(myOutputFileName)) {
      mySourcePath = parentPath + "/" + mySourceFileName;
    }
    else {
      mySourcePath = parentPath;
    }
  }

  public String getPresentableName() {
    return myOutputFileName;
  }

  public void render(@NotNull PresentationData presentationData) {
    if (myFile != null) {
      presentationData.setIcons(myIsDirectory ? COPY_OF_FOLDER_ICON : myFile.getIcon());
      if (myIsDirectory) {
        presentationData.addText(CompilerBundle.message("node.text.0.directory.content", mySourceFileName), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
      else {
        presentationData.addText(myOutputFileName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
      presentationData.addText(" (" + mySourcePath + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
    else {
      presentationData.setIcons(COPY_OF_FOLDER_ICON);
      presentationData.addText(myOutputFileName, SimpleTextAttributes.ERROR_ATTRIBUTES);
      final VirtualFile parentFile = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(mySourcePath));
      presentationData.addText("(" + mySourcePath + ")",
                      parentFile != null ? SimpleTextAttributes.GRAY_ATTRIBUTES : SimpleTextAttributes.ERROR_ATTRIBUTES);
    }
  }

  @Override
  public int getWeight() {
    return myIsDirectory ? PackagingElementWeights.DIRECTORY_COPY : PackagingElementWeights.FILE_COPY;
  }
}
