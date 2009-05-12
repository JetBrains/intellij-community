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
  private final String myParentPath;
  private final String myFileName;
  private final VirtualFile myFile;
  private final boolean myIsDirectory;

  public FileCopyPresentation(String filePath) {
    myFile = LocalFileSystem.getInstance().findFileByPath(filePath);
    if (myFile != null) {
      myFileName = myFile.getName();
      final VirtualFile parent = myFile.getParent();
      myParentPath = parent != null ? FileUtil.toSystemDependentName(parent.getPath()) : "";
      myIsDirectory = myFile.isDirectory();
    }
    else {
      myFileName = StringUtil.getShortName(filePath, '/');
      myParentPath = FileUtil.toSystemDependentName(StringUtil.getPackageName(filePath, '/'));
      myIsDirectory = false;
    }
  }

  public String getPresentableName() {
    return myFileName;
  }

  public void render(@NotNull PresentationData presentationData) {
    if (myFile != null) {
      presentationData.setIcons(myIsDirectory ? COPY_OF_FOLDER_ICON : myFile.getIcon());
      if (myIsDirectory) {
        presentationData.addText(CompilerBundle.message("node.text.0.directory.content", myFileName), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
      else {
        presentationData.addText(myFileName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
      presentationData.addText(" (" + myParentPath + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
    else {
      presentationData.setIcons(COPY_OF_FOLDER_ICON);
      presentationData.addText(myFileName, SimpleTextAttributes.ERROR_ATTRIBUTES);
      final VirtualFile parentFile = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(myParentPath));
      presentationData.addText("(" + myParentPath + ")",
                      parentFile != null ? SimpleTextAttributes.GRAY_ATTRIBUTES : SimpleTextAttributes.ERROR_ATTRIBUTES);
    }
  }

  @Override
  public int getWeight() {
    return myIsDirectory ? PackagingElementWeights.DIRECTORY_COPY : PackagingElementWeights.FILE_COPY;
  }
}
