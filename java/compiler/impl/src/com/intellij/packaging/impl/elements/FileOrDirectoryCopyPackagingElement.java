package com.intellij.packaging.impl.elements;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class FileOrDirectoryCopyPackagingElement<T extends FileOrDirectoryCopyPackagingElement> extends PackagingElement<T> {
  @NonNls public static final String PATH_ATTRIBUTE = "path";
  protected String myFilePath;

  public FileOrDirectoryCopyPackagingElement(PackagingElementType type) {
    super(type);
  }

  protected FileOrDirectoryCopyPackagingElement(PackagingElementType type, String filePath) {
    super(type);
    myFilePath = filePath;
  }

  @Nullable
  public VirtualFile findFile() {
    return LocalFileSystem.getInstance().findFileByPath(myFilePath);
  }

  @Override
  public boolean isEqualTo(@NotNull PackagingElement<?> element) {
    return element instanceof FileOrDirectoryCopyPackagingElement &&
           myFilePath != null &&
           myFilePath.equals(((FileOrDirectoryCopyPackagingElement)element).getFilePath());
  }

  @Attribute(PATH_ATTRIBUTE)
  public String getFilePath() {
    return myFilePath;
  }

  public void setFilePath(String filePath) {
    myFilePath = filePath;
  }
}
