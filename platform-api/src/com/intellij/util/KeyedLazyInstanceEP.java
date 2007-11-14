package com.intellij.util;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.util.LazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author yole
 */
public class KeyedLazyInstanceEP<T> extends AbstractExtensionPointBean {

  // these must be public for scrambling compatibility
  @Attribute("key")
  public String key;
  
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
}