/*
 * @author max
 */
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.util.KeyedExtensionCollector;
import org.jetbrains.annotations.NonNls;

import java.util.List;

public class FileTypeExtension<T> extends KeyedExtensionCollector<T, FileType> {
  public FileTypeExtension(@NonNls final String epName) {
    super(epName);
  }

  protected String keyToString(final FileType key) {
    return key.getName();
  }

  public List<T> allForFileType(FileType t) {
    return forKey(t);
  }

  public T forFileType(FileType t) {
    final List<T> all = allForFileType(t);
    if (all.isEmpty()) {
      return null;
    }
    else {
      return all.get(0);
    }
  }
}