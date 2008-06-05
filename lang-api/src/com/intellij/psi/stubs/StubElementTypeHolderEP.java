package com.intellij.psi.stubs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author yole
 */
public class StubElementTypeHolderEP extends AbstractExtensionPointBean {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.StubElementTypeHolderEP");

  public static final ExtensionPointName<StubElementTypeHolderEP> EP_NAME = ExtensionPointName.create("com.intellij.stubElementTypeHolder");

  @Attribute("class")
  public String holderClass;

  public void initialize() {
    try {
      findClass(holderClass);
    }
    catch (ClassNotFoundException e) {
      LOG.error(e);
    }
  }
}
