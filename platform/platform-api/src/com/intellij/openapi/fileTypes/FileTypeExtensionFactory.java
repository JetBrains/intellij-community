/*
 * @author max
 */
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.util.KeyedExtensionFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class FileTypeExtensionFactory<T> extends KeyedExtensionFactory<T, FileType> {
  public FileTypeExtensionFactory(@NotNull final Class<T> interfaceClass, @NonNls @NotNull final String epName) {
    super(interfaceClass, epName);
  }

  public String getKey(final FileType key) {
    return key.getName();
  }
}