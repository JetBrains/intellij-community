/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions;

/**
 * Extensions should implement this interface when it is important to find out what particular plugin has provided this extension.
 * @author akireyev
 */
public interface PluginAware {
  /**
   * Called by extensions framework when extension is loaded from plugin.xml descriptor.
   * @param pluginDescriptor descriptor of the plugin that provided this particular extension.
   */
  void setPluginDescriptor(PluginDescriptor pluginDescriptor);
}
