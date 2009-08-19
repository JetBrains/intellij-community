package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author peter
 */
public class ModuleBuilderFactory extends AbstractExtensionPointBean {
  @Attribute("builderClass")
  public String builderClass;

  public ModuleBuilder createBuilder() {
    try {
      return instantiate(builderClass, ApplicationManager.getApplication().getPicoContainer());
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }


}
