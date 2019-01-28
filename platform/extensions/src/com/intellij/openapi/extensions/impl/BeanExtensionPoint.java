// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

final class BeanExtensionPoint<T> extends ExtensionPointImpl<T> {
  BeanExtensionPoint(@NotNull String name,
                     @NotNull String className,
                     @NotNull ExtensionsAreaImpl owner,
                     @NotNull PluginDescriptor pluginDescriptor) {
    super(name, className, owner, pluginDescriptor);
  }

  @Override
  @NotNull
  ExtensionComponentAdapter createAdapter(@NotNull Element extensionElement, @NotNull PluginDescriptor pluginDescriptor) {
    // project level extensions requires Project as constructor argument, so, for now constructor injection disabled only for app level
    return doCreateAdapter(getClassName(), extensionElement, !JDOMUtil.isEmpty(extensionElement), pluginDescriptor, getArea() != null);
  }
}