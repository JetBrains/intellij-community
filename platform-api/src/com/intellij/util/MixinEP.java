package com.intellij.util;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.util.LazyInstance;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class MixinEP<T> extends AbstractExtensionPointBean {

  // these must be public for scrambling compatibility
  @Attribute("key")
  public String key;

  @Attribute("implementationClass")
  public String implementationClass;

  private final NotNullLazyValue<Class> myKey = new NotNullLazyValue<Class>() {
    @NotNull
    protected Class compute() {
      try {
        return findClass(key);
      }
      catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  };

  private final LazyInstance<T> myHandler = new LazyInstance<T>() {
    protected Class<T> getInstanceClass() throws ClassNotFoundException {
      return findClass(implementationClass);
    }
  };

  public Class getKey() {
    return myKey.getValue();
  }

  public T getInstance() {
    return myHandler.getValue();
  }
}