// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.MutablePicoContainer;

final class BeanExtensionPoint<T> extends ExtensionPointImpl<T> {
  BeanExtensionPoint(@NotNull String name,
                     @NotNull String className,
                     @NotNull MutablePicoContainer picoContainer,
                     @NotNull PluginDescriptor pluginDescriptor) {
    super(name, className, picoContainer, pluginDescriptor);
  }

  @Override
  @NotNull
  protected ExtensionComponentAdapter createAdapterAndRegisterInPicoContainerIfNeeded(@NotNull Element extensionElement,
                                                                                      @NotNull PluginDescriptor pluginDescriptor,
                                                                                      @NotNull MutablePicoContainer picoContainer) {
    // project level extensions requires Project as constructor argument, so, for now constructor injection disabled only for app level
    return doCreateAdapter(getClassName(), extensionElement, !JDOMUtil.isEmpty(extensionElement), pluginDescriptor, picoContainer.getParent() != null);
  }
}