// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.serviceContainer.BaseKeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.util.ClearableLazyValue.createAtomic;

public final class LiveTemplateContextBean extends BaseKeyedLazyInstance<TemplateContextType> implements LiveTemplateContext {
  static final ExtensionPointName<LiveTemplateContextBean> EP_NAME = new ExtensionPointName<>("com.intellij.liveTemplateContext");

  public static final String EVERYWHERE_CONTEXT_ID = "OTHER";

  @Attribute("implementation")
  @RequiredElement
  public String implementationClass;

  @Attribute("contextId")
  @RequiredElement
  public String contextId;

  @Attribute("baseContextId")
  public String baseContextId;

  public LiveTemplateContextBean() {
  }

  @Override
  public @NotNull String getContextId() {
    if (contextId == null) {
      TemplateContextType instance = getInstance();
      return instance.myContextId;
    }

    return contextId;
  }

  @Override
  public @Nullable String getBaseContextId() {
    if (contextId == null) {
      TemplateContextType instance = getInstance();
      TemplateContextType baseContextType = instance.getBaseContextType();
      return baseContextType != null ? baseContextType.myContextId : null;
    }

    if (EVERYWHERE_CONTEXT_ID.equals(contextId)) return null;

    return baseContextId == null ? EVERYWHERE_CONTEXT_ID : baseContextId;
  }

  @Override
  public @NotNull TemplateContextType getTemplateContextType() {
    return getInstance();
  }

  @Override
  protected @Nullable String getImplementationClassName() {
    return implementationClass;
  }

  @Override
  public @NotNull TemplateContextType createInstance(@NotNull ComponentManager componentManager,
                                                     @NotNull PluginDescriptor pluginDescriptor) {
    TemplateContextType instance = super.createInstance(componentManager, pluginDescriptor);
    if (instance.myContextId == null) { // new declaration syntax
      instance.myContextId = contextId;

      if (!EVERYWHERE_CONTEXT_ID.equals(contextId)) {
        String actualBaseId = baseContextId != null ? baseContextId : EVERYWHERE_CONTEXT_ID;
        instance.myBaseContextType = createAtomic(() -> LiveTemplateContextService.getInstance().getTemplateContextType(actualBaseId));
      }
    }
    return instance;
  }

  @Override
  public String toString() {
    return "LiveTemplateContextBean{" +
           "contextId='" + contextId + '\'' +
           ", baseContextId='" + baseContextId + '\'' +
           ", implementationClass='" + implementationClass + '\'' +
           '}';
  }
}
