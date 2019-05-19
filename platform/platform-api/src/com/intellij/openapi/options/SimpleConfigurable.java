// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import com.intellij.openapi.util.Getter;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SimpleConfigurable<UI extends ConfigurableUi<S>, S> extends ConfigurableBase<UI, S> {
  private final Class<? extends UI> uiClass;
  private final Getter<? extends S> settingsGetter;

  private SimpleConfigurable(@NotNull String id, @NotNull String displayName, @Nullable String helpTopic, @NotNull Class<? extends UI> uiClass, @NotNull Getter<? extends S> settingsGetter) {
    super(id, displayName, helpTopic);

    this.uiClass = uiClass;
    this.settingsGetter = settingsGetter;
  }

  public static <UI extends ConfigurableUi<S>, S> SimpleConfigurable<UI, S> create(@NotNull String id, @NotNull String displayName, @Nullable String helpTopic, @NotNull Class<? extends UI> uiClass, @NotNull Getter<? extends S> settingsGetter) {
    return new SimpleConfigurable<>(id, displayName, helpTopic, uiClass, settingsGetter);
  }

  public static <UI extends ConfigurableUi<S>, S> SimpleConfigurable<UI, S> create(@NotNull String id, @NotNull String displayName, @NotNull Class<? extends UI> uiClass, @NotNull Getter<? extends S> settingsGetter) {
    return create(id, displayName, id, uiClass, settingsGetter);
  }

  @NotNull
  @Override
  protected S getSettings() {
    return settingsGetter.get();
  }

  @Override
  protected UI createUi() {
    return ReflectionUtil.newInstance(uiClass);
  }

  @NotNull
  @Override
  public Class<?> getOriginalClass() {
    return uiClass;
  }
}