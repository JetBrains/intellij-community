package com.intellij.openapi.vfs.impl.http;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class RemoteFileUtil {
  private RemoteFileUtil() {
  }

  @Nullable
  private static FileType getFileType(@NotNull Language language) {
    for (final FileType fileType : FileTypeManager.getInstance().getRegisteredFileTypes()) {
      if (fileType instanceof LanguageFileType && ((LanguageFileType)fileType).getLanguage() == language) {
        return fileType;
      }
    }
    return language.getAssociatedFileType();
  }

  @Nullable
  public static FileType getFileType(@Nullable String contentType) {
    if (contentType == null) return null;

    int end = contentType.indexOf(';');
    String mimeType = end != -1 ? contentType.substring(0, end) : contentType;
    if (mimeType.length() == 0) return null;

    for (Language language : Language.getRegisteredLanguages()) {
      String[] types = language.getMimeTypes();
      for (String type : types) {
        if (type.equalsIgnoreCase(mimeType)) {
          FileType fileType = getFileType(language);
          if (fileType != null) {
            return fileType;
          }
        }
      }
    }
    return null;
  }

}
