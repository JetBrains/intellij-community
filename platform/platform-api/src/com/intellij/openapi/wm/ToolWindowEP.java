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

package com.intellij.openapi.wm;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class ToolWindowEP extends AbstractExtensionPointBean {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.wm.ToolWindowEP");

  public static final ExtensionPointName<ToolWindowEP> EP_NAME = ExtensionPointName.create("com.intellij.toolWindow");

  @Attribute("id")
  public String id;

  /**
   * The side of the screen on which the toolwindow is displayed ("left", "right" or "bottom").
   */
  @Attribute("anchor")
  public String anchor;

  /**
   * The resource path of the icon displayed on the toolwindow button. Toolwindow icons must have the size of 13x13 pixels.
   */
  @Attribute("icon")
  public String icon;

  /**
   * The name of the class implementing {@link ToolWindowFactory}, used to create the toolwindow contents.
   */
  @Attribute("factoryClass")
  public String factoryClass;

  @Attribute("conditionClass")
  public String conditionClass;

  @Attribute("secondary")
  public boolean secondary;

  @Attribute("canCloseContents")
  public boolean canCloseContents;

  private Class<? extends ToolWindowFactory> myFactoryClass;
  private ToolWindowFactory myFactory;

  public ToolWindowFactory getToolWindowFactory() {
    if (myFactory == null) {
      try {
        myFactory = instantiate(getFactoryClass(), ApplicationManager.getApplication().getPicoContainer());
      }
      catch(Exception e) {
        LOG.error(e);
        return null;
      }
    }
    return myFactory;
  }

  public Class<? extends ToolWindowFactory> getFactoryClass() {
    if (myFactoryClass == null) {
      if (factoryClass == null) {
        LOG.error(new PluginException("No toolwindow factory specified for " + id, getPluginId()));
        return null;
      }

      try {
        myFactoryClass = findClass(factoryClass);
      }
      catch(Exception e) {
        LOG.error(e);
        return null;
      }
    }
    return myFactoryClass;
  }

  @Nullable
  public Condition<Project> getCondition() {
    if (conditionClass != null) {
      try {
        return instantiate(conditionClass, ApplicationManager.getApplication().getPicoContainer());
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + ":" + id;
  }
}
