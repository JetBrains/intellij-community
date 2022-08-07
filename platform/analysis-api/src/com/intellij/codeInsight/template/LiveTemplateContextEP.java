// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template;

import com.intellij.codeInsight.template.TemplateContextType.TemplateContextTypeCache;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.serviceContainer.BaseKeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class LiveTemplateContextEP extends BaseKeyedLazyInstance<TemplateContextType> {
  public static final ExtensionPointName<LiveTemplateContextEP> EP_NAME = new ExtensionPointName<>("com.intellij.liveTemplateContext");

  public static final String EVERYWHERE_CONTEXT_ID = "OTHER";

  @Attribute("implementation")
  @RequiredElement
  public String implementationClass;

  // not marked as required to be able to load old-style definitions
  @Attribute("contextId")
  public String contextId;

  @Attribute("baseContextId")
  public String baseContextId;

  private TemplateContextType instanceOverride;

  public LiveTemplateContextEP() {
  }

  // Required for dynamic registration from SqlDialectTemplateRegistrar
  @ApiStatus.Internal
  public LiveTemplateContextEP(@NotNull TemplateContextType instance) {
    this.instanceOverride = instance;
  }

  public String getContextId() {
    if (contextId == null) {
      TemplateContextType instance = getInstance();
      return instance.myContextId;
    }

    return contextId;
  }

  public @Nullable String getBaseContextId() {
    if (contextId == null) {
      TemplateContextType instance = getInstance();
      TemplateContextType baseContextType = instance.getBaseContextType();
      return baseContextType != null ? baseContextType.myContextId : null;
    }

    if (EVERYWHERE_CONTEXT_ID.equals(contextId)) return null;

    return baseContextId == null ? EVERYWHERE_CONTEXT_ID : baseContextId;
  }

  public @Nullable LiveTemplateContextEP getBaseContextType() {
    String myBaseContextId = getBaseContextId();
    if (myBaseContextId == null) return null;

    for (LiveTemplateContextEP liveTemplateContext : EP_NAME.getExtensionList()) {
      if (Objects.equals(liveTemplateContext.getContextId(), myBaseContextId)) {
        return liveTemplateContext;
      }
    }

    return null;
  }

  @Override
  protected @Nullable String getImplementationClassName() {
    return implementationClass;
  }

  @Override
  public @NotNull TemplateContextType createInstance(@NotNull ComponentManager componentManager,
                                                     @NotNull PluginDescriptor pluginDescriptor) {
    if (instanceOverride != null) return instanceOverride;

    TemplateContextType instance = super.createInstance(componentManager, pluginDescriptor);
    if (instance.myContextId == null) { // new declaration syntax
      instance.myContextId = contextId;

      if (!EVERYWHERE_CONTEXT_ID.equals(contextId)) {
        instance.myBaseContextType = baseContextId != null ?
                                     new TemplateContextTypeCache(baseContextId) : TemplateContextTypeCache.EVERYWHERE_CONTEXT;
      }
    }
    return instance;
  }
}
