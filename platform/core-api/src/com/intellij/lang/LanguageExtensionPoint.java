// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Base class for {@link Language}-bound extension points.
 */
public class LanguageExtensionPoint<T> extends CustomLoadingExtensionPointBean<T> implements KeyedLazyInstance<T> {
  // these must be public for scrambling compatibility
  /**
   * Language ID.
   * Use empty string for "all languages".
   *
   * @see Language#getID()
   */
  @Attribute("language")
  @RequiredElement(allowEmpty = true)
  public String language;

  @Attribute("implementationClass")
  public String implementationClass;

  /**
   * @deprecated You must pass plugin descriptor, use {@link LanguageExtensionPoint#LanguageExtensionPoint(String, Object)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public LanguageExtensionPoint() {
  }

  @TestOnly
  @NonInjectable
  public LanguageExtensionPoint(@NotNull String language, @NotNull String implementationClass, @NotNull PluginDescriptor pluginDescriptor) {
    this.language = language;
    this.implementationClass = implementationClass;
    setPluginDescriptor(pluginDescriptor);
  }

  @TestOnly
  public LanguageExtensionPoint(@NotNull String language, @NotNull T instance) {
    super(instance);

    this.language = language;
    implementationClass = instance.getClass().getName();
  }

  @Override
  protected final @Nullable String getImplementationClassName() {
    return implementationClass;
  }

  @Override
  public @NotNull String getKey() {
    // empty string means any language
    return StringUtilRt.notNullize(language);
  }
}
