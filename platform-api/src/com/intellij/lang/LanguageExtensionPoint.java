package com.intellij.lang;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.util.LazyInstance;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author yole
 */
public class LanguageExtensionPoint<T> extends AbstractExtensionPointBean implements KeyedLazyInstance<T> {

  // these must be public for scrambling compatibility
  @Attribute("language")
  public String language;

  @Attribute("implementationClass")
  public String implementationClass;

  private final LazyInstance<T> myHandler = new LazyInstance<T>() {
    protected Class<T> getInstanceClass() throws ClassNotFoundException {
      if (implementationClass == null) {
        throw new RuntimeException("implementation class is not specified for unknown language extension point, language: " + language);
      }
      return findClass(implementationClass);
    }
  };

  public T getInstance() {
    return myHandler.getValue();
  }

  public String getKey() {
    return language;
  }
}