package com.intellij.openapi.extensions;

import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author yole
 */
public class KeyedFactoryEPBean extends AbstractExtensionPointBean {
  // these must be public for scrambling compatibility
  @Attribute("key")
  public String key;

  @Attribute("implementationClass")
  public String implementationClass;

  @Attribute("factoryClass")
  public String factoryClass;
}