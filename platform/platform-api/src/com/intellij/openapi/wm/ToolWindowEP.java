// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ToolWindowEP implements PluginAware {
  private static final Logger LOG = Logger.getInstance(ToolWindowEP.class);

  public static final ExtensionPointName<ToolWindowEP> EP_NAME = new ExtensionPointName<>("com.intellij.toolWindow");

  private PluginDescriptor pluginDescriptor;

  @RequiredElement
  @Attribute
  public String id;

  /**
   * The side of the screen on which the toolwindow is displayed ("left", "right" or "bottom").
   */
  @Attribute
  public String anchor;

  /**
   * The stripe side bar on which large toolwindow icon are displayed ("left", "right" or "bottom").
   */
  @Attribute
  public String largeStripeAnchor;

  /**
   * @deprecated Use {@link #secondary}
   */
  @Attribute
  @Deprecated
  public boolean side;

  /**
   * The resource path of the icon displayed on the toolwindow button. Toolwindow icons must have the size of 13x13 pixels.
   */
  @Attribute("icon")
  public String icon;

  /**
   * Tool window saves its state on project close and restore on when project opens.
   * In some cases, it is useful to postpone its activation until the user explicitly activates it.
   * Example: Tool Window initialization takes a huge amount of time and makes project loading slower.
   *
   * {@code true} if Tool Window should not be activated on start even if was opened previously.
   * {@code false} otherwise. Please note that active (visible and focused) tool window would be activated on start in any case.
   */
  @Attribute("doNotActivateOnStart")
  public boolean isDoNotActivateOnStart;

  /**
   * The name of the class implementing {@link ToolWindowFactory}, used to create the toolwindow contents.
   */
  @RequiredElement
  @Attribute
  public String factoryClass;

  /**
   * @deprecated Implement {@link ToolWindowFactory#isApplicable(Project)} instead.
   */
  @Attribute
  @Deprecated
  public String conditionClass;

  @Attribute
  public boolean secondary;

  @Attribute
  public boolean canCloseContents;

  private Class<? extends ToolWindowFactory> myFactoryClass;

  private volatile ToolWindowFactory myFactory;

  @Transient
  public final @NotNull PluginDescriptor getPluginDescriptor() {
    return pluginDescriptor;
  }

  @Override
  public final void setPluginDescriptor(@NotNull PluginDescriptor value) {
    pluginDescriptor = value;
  }

  /**
   * @deprecated Do not use ToolWindowEP.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public @Nullable ToolWindowFactory getToolWindowFactory() {
    return getToolWindowFactory(getPluginDescriptor());
  }

  public @Nullable ToolWindowFactory getToolWindowFactory(@NotNull PluginDescriptor pluginDescriptor) {
    ToolWindowFactory factory = myFactory;
    if (factory != null) {
      return factory;
    }

    if (factoryClass == null) {
      LOG.error(new PluginException("No toolwindow factory specified for " + id, pluginDescriptor.getPluginId()));
      return null;
    }

    //noinspection SynchronizeOnThis
    synchronized (this) {
      factory = myFactory;
      if (factory != null) {
        return factory;
      }

      try {
        //noinspection NonPrivateFieldAccessedInSynchronizedContext
        factory = ApplicationManager.getApplication().instantiateClass(factoryClass, pluginDescriptor);
        myFactory = factory;
      }
      catch (Exception e) {
        LOG.error(e);
        return null;
      }
    }
    return factory;
  }

  public @Nullable Class<? extends ToolWindowFactory> getFactoryClass(@NotNull PluginDescriptor pluginDescriptor) {
    if (myFactoryClass == null) {
      if (factoryClass == null) {
        LOG.error(new PluginException("No toolwindow factory specified for " + id, pluginDescriptor.getPluginId()));
        return null;
      }

      ClassLoader classLoader = pluginDescriptor.getPluginClassLoader();
      try {
        //noinspection unchecked
        myFactoryClass = (Class<? extends ToolWindowFactory>)Class.forName(factoryClass, true, classLoader == null ? ToolWindowEP.class.getClassLoader() : classLoader);
      }
      catch (ClassNotFoundException e) {
        return null;
      }
    }
    return myFactoryClass;
  }

  /**
   * @deprecated Do not use ToolWindowEP.
   */
  @Deprecated
  public @Nullable Condition<Project> getCondition() {
    return getCondition(getPluginDescriptor());
  }

  public @Nullable Condition<Project> getCondition(@NotNull PluginDescriptor pluginDescriptor) {
    if (conditionClass == null) {
      return null;
    }

    try {
      return ApplicationManager.getApplication().instantiateClass(conditionClass, pluginDescriptor);
    }
    catch (Exception e) {
      LOG.error(e);
      return null;
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + ":" + id;
  }
}
