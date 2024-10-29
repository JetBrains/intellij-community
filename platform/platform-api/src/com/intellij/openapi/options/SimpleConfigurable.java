// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options;

import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public final class SimpleConfigurable<UI extends ConfigurableUi<S>, S> extends ConfigurableBase<UI, S> {
  private final Class<? extends UI> uiClass;
  private final Supplier<? extends S> settingsGetter;

  private SimpleConfigurable(@NotNull String id,
                             @NotNull @NlsContexts.ConfigurableName String displayName,
                             @Nullable String helpTopic,
                             @NotNull Class<? extends UI> uiClass,
                             @NotNull Supplier<? extends S> settingsGetter) {
    super(id, displayName, helpTopic);

    this.uiClass = uiClass;
    this.settingsGetter = settingsGetter;
  }

  /**
   * @deprecated Replaced by create method using Supplier instead of deprecated Getter interface
   */
  @Deprecated(forRemoval = true)
  public static <UI extends ConfigurableUi<S>, S> SimpleConfigurable<UI, S> create(@NotNull String id,
                                                                                   @NotNull @NlsContexts.ConfigurableName String displayName,
                                                                                   @Nullable String helpTopic,
                                                                                   @NotNull Class<? extends UI> uiClass,
                                                                                   @NotNull Getter<? extends S> settingsGetter) {
    return new SimpleConfigurable<>(id, displayName, helpTopic, uiClass, settingsGetter);
  }

  /**
   * @deprecated Replaced by create method using Supplier instead of deprecated Getter interface
   */
  @Deprecated(forRemoval = true)
  public static <UI extends ConfigurableUi<S>, S> SimpleConfigurable<UI, S> create(@NotNull String id,
                                                                                   @NotNull @NlsContexts.ConfigurableName String displayName,
                                                                                   @NotNull Class<? extends UI> uiClass,
                                                                                   @NotNull Getter<? extends S> settingsGetter) {
    return new SimpleConfigurable<>(id, displayName, id, uiClass, settingsGetter);
  }

  public static <UI extends ConfigurableUi<S>, S> SimpleConfigurable<UI, S> create(@NotNull String id,
                                                                                   @NotNull @NlsContexts.ConfigurableName String displayName,
                                                                                   @Nullable String helpTopic,
                                                                                   @NotNull Class<? extends UI> uiClass,
                                                                                   @NotNull Supplier<? extends S> settingsGetter) {
    return new SimpleConfigurable<>(id, displayName, helpTopic, uiClass, settingsGetter);
  }

  public static <UI extends ConfigurableUi<S>, S> SimpleConfigurable<UI, S> create(@NotNull String id,
                                                                                   @NotNull @NlsContexts.ConfigurableName String displayName,
                                                                                   @NotNull Class<? extends UI> uiClass,
                                                                                   @NotNull Supplier<? extends S> settingsGetter) {
    return new SimpleConfigurable<>(id, displayName, id, uiClass, settingsGetter);
  }

  @Override
  protected @NotNull S getSettings() {
    return settingsGetter.get();
  }

  @Override
  protected UI createUi() {
    return ReflectionUtil.newInstance(uiClass);
  }

  @Override
  public @NotNull Class<?> getOriginalClass() {
    return uiClass;
  }
}