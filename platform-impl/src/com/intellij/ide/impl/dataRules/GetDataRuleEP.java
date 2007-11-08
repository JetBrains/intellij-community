package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.LazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author yole
 */
public class GetDataRuleEP extends AbstractExtensionPointBean {
  public static final ExtensionPointName<GetDataRuleEP> EP_NAME = ExtensionPointName.create("com.intellij.getDataRule");

  // these must be public for scrambling compatibility
  @Attribute("key")
  public String key;
  
  @Attribute("implementationClass")
  public String implementationClass;

  private final LazyInstance<GetDataRule> myHandler = new LazyInstance<GetDataRule>() {
    protected Class<GetDataRule> getInstanceClass() throws ClassNotFoundException {
      return findClass(implementationClass);
    }
  };

  public GetDataRule getDataRule() {
    return myHandler.getValue();
  }
}