/*
 * @author max
 */
package com.intellij.openapi.fileTypes;

public class FileTypes {
  public static final FileType ARCHIVE = FileTypeManager.getInstance().getStdFileType("ARCHIVE");
  public static final FileType UNKNOWN = FileTypeManager.getInstance().getStdFileType("UNKNOWN");
  public static final LanguageFileType PLAIN_TEXT = (LanguageFileType)FileTypeManager.getInstance().getStdFileType("PLAIN_TEXT");

  protected FileTypes() {}
}