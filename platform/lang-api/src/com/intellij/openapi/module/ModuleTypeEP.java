/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
