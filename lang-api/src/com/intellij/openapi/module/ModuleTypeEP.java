package com.intellij.openapi.module;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author yole
 */
public class ModuleTypeEP extends AbstractExtensionPointBean {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.module.ModuleTypeEP");

  public static final ExtensionPointName<ModuleTypeEP> EP_NAME = ExtensionPointName.create("com.intellij.moduleType");

  private ModuleType myModuleType;

  @Attribute("id")
  public String id;

  @Attribute("implementationClass")
  public String implementationClass;

  @Attribute("classpathProvider")
  public boolean classpathProvider;

  public ModuleType getModuleType() {
    if (myModuleType == null) {
      try {
        myModuleType = instantiate(implementationClass, ApplicationManager.getApplication().getPicoContainer());
      }
      catch(Exception e) {
        LOG.error(e);
        return null;
      }
    }
    return myModuleType;
  }
}
