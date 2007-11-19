/*
 * @author max
 */
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.util.LazyInstance;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;

public class FileTypeExtensionPoint<T> extends AbstractExtensionPointBean implements KeyedLazyInstance<T> {

  // these must be public for scrambling compatibility
  @Attribute("filetype")
  public String filetype;

  @Attribute("implementationClass")
  public String implementationClass;

  private final LazyInstance<T> myHandler = new LazyInstance<T>() {
    protected Class<T> getInstanceClass() throws ClassNotFoundException {
      return findClass(implementationClass);
    }
  };

  public T getInstance() {
    return myHandler.getValue();
  }

  public String getKey() {
    return filetype;
  }
}