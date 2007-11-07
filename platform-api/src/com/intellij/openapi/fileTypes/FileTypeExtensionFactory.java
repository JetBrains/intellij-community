/*
 * @author max
 */
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.KeyedFactoryEPBean;
import com.intellij.openapi.util.KeyedExtensionFactory;

public class FileTypeExtensionFactory<T> extends KeyedExtensionFactory<T, FileType> {
  public FileTypeExtensionFactory(final Class<T> interfaceClass, final ExtensionPointName<KeyedFactoryEPBean> epName) {
    super(interfaceClass, epName);
  }

  public String getKey(final FileType key) {
    return key.getName();
  }
}