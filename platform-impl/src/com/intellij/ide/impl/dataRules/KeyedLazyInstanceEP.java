package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.util.LazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author yole
 */
public class KeyedLazyInstanceEP extends AbstractExtensionPointBean {

  // these must be public for scrambling compatibility
  @Attribute("key")
  public String key;
  
  @Attribute("implementationClass")
  public String implementationClass;

  private final LazyInstance myHandler = new LazyInstance() {
    protected Class getInstanceClass() throws ClassNotFoundException {
      return findClass(implementationClass);
    }
  };

  public Object getInstance() {
    return myHandler.getValue();
  }
}