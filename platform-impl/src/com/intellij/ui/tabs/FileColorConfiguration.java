package com.intellij.ui.tabs;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jdom.Element;

/**
* @author spleaner
*/
class FileColorConfiguration implements Cloneable {
  private static final String COLOR = "color";

  private String myPath;
  private String myColorName;
  private VirtualFile myFile;
  private static final String PATH = "path";

  public FileColorConfiguration() {
  }

  public FileColorConfiguration(final String path, final String colorName) {
    myPath = path;
    myColorName = colorName;
  }

  public String getPath() {
    return myPath;
  }

  public void setPath(String path) {
    myPath = path;
  }

  public String getColorName() {
    return myColorName;
  }

  public void setColorName(final String colorName) {
    myColorName = colorName;
  }

  public boolean isValid() {
    if (myPath == null || myPath.length() == 0) {
      return false;
    }

    if (myColorName == null) {
      return false;
    }

    return true;
  }

  public void save(@NotNull final Element e) {
    if (!isValid()) {
      return;
    }

    final Element tab = new Element(FileColorsModel.FILE_COLOR);

    tab.setAttribute(PATH, getPath());
    tab.setAttribute(COLOR, myColorName);

    e.addContent(tab);
  }

  @Nullable
  public VirtualFile resolve() {
    if (myFile == null) {
      myFile = LocalFileSystem.getInstance().findFileByPath(getPath());
    }

    return myFile;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FileColorConfiguration that = (FileColorConfiguration)o;

    if (!myColorName.equals(that.myColorName)) return false;
    if (!myPath.equals(that.myPath)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myPath.hashCode();
    result = 31 * result + myColorName.hashCode();
    return result;
  }

  public FileColorConfiguration clone() throws CloneNotSupportedException {
    final FileColorConfiguration result = new FileColorConfiguration();

    result.myColorName = myColorName;
    result.myPath = myPath;

    return result;
  }

  @Nullable
  public static FileColorConfiguration load(@NotNull final Element e) {
    final String path = e.getAttributeValue(PATH);
    if (path == null) {
      return null;
    }

    final String colorName = e.getAttributeValue(COLOR);
    if (colorName == null) {
      return null;
    }

    return new FileColorConfiguration(path, colorName);
  }
}
