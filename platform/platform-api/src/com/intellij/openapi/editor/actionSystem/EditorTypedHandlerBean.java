// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.util.pico.CachingConstructorInjectionComponentAdapter;
import com.intellij.util.pico.DefaultPicoContainer;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Please use com.intellij.codeInsight.editorActions.TypedHandlerDelegate instead
 * @author yole
 */
@Deprecated
public final class EditorTypedHandlerBean {
  // these must be public for scrambling compatibility
  @Attribute("implementationClass")
  public String implementationClass;

  private TypedActionHandler myHandler;

  public @NotNull TypedActionHandler getHandler(@NotNull DefaultPicoContainer container, @NotNull PluginDescriptor pluginDescriptor)
    throws ClassNotFoundException {
    if (myHandler == null) {
      Class<?> aClass = Class.forName(implementationClass, true, pluginDescriptor.getPluginClassLoader());
      //noinspection deprecation
      myHandler = (TypedActionHandler)CachingConstructorInjectionComponentAdapter.instantiateGuarded(null, container, aClass);
    }
    return myHandler;
  }
}