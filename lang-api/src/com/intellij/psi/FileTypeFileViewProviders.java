/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.openapi.fileTypes.FileTypeExtension;

public class FileTypeFileViewProviders extends FileTypeExtension<FileViewProviderFactory> {
  public static FileTypeFileViewProviders INSTANCE = new FileTypeFileViewProviders();

  private FileTypeFileViewProviders() {
    super("com.intellij.fileType.fileViewProviderFactory");
  }
}