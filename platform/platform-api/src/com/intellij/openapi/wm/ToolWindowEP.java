// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ToolWindowEP implements PluginAware {
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
   * The stripe sidebar on which large toolwindow icon are displayed ("left", "right" or "bottom").
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
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
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

  private volatile ToolWindowFactory factory;

  @Transient
  public final @NotNull PluginDescriptor getPluginDescriptor() {
    return pluginDescriptor;
  }

  @Override
  public final void setPluginDescriptor(@NotNull PluginDescriptor value) {
    pluginDescriptor = value;
  }

  public @NotNull ToolWindowFactory getToolWindowFactory(@NotNull PluginDescriptor pluginDescriptor) {
    ToolWindowFactory factory = this.factory;
    if (factory != null) {
      return factory;
    }

    if (factoryClass == null) {
      throw new PluginException("No toolwindow factory specified for " + id, pluginDescriptor.getPluginId());
    }

    //noinspection SynchronizeOnThis
    synchronized (this) {
      factory = this.factory;
      if (factory != null) {
        return factory;
      }

      //noinspection NonPrivateFieldAccessedInSynchronizedContext
      factory = ApplicationManager.getApplication().instantiateClass(factoryClass, pluginDescriptor);
      this.factory = factory;
    }
    return factory;
  }

  public @Nullable Condition<Project> getCondition(@NotNull PluginDescriptor pluginDescriptor) {
    if (conditionClass == null) {
      return null;
    }
    return ApplicationManager.getApplication().instantiateClass(conditionClass, pluginDescriptor);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + ":" + id;
  }
}
