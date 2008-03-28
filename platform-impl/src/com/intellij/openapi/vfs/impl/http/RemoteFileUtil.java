package com.intellij.openapi.vfs.impl.http;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class RemoteFileUtil {
  private RemoteFileUtil() {
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
          return language.getAssociatedFileType();
        }
      }
    }
    return null;
  }

}
