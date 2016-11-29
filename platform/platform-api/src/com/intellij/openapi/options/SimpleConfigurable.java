/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.options;

import com.intellij.openapi.util.Getter;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SimpleConfigurable<UI extends ConfigurableUi<S>, S> extends ConfigurableBase<UI, S> {
  private final Class<UI> uiClass;
  private final Getter<S> settingsGetter;

  private SimpleConfigurable(@NotNull String id, @NotNull String displayName, @Nullable String helpTopic, @NotNull Class<UI> uiClass, @NotNull Getter<S> settingsGetter) {
    super(id, displayName, helpTopic);

    this.uiClass = uiClass;
    this.settingsGetter = settingsGetter;
  }

  public static <UI extends ConfigurableUi<S>, S> SimpleConfigurable<UI, S> create(@NotNull String id, @NotNull String displayName, @Nullable String helpTopic, @NotNull Class<UI> uiClass, @NotNull Getter<S> settingsGetter) {
    return new SimpleConfigurable<>(id, displayName, helpTopic, uiClass, settingsGetter);
  }

  public static <UI extends ConfigurableUi<S>, S> SimpleConfigurable<UI, S> create(@NotNull String id, @NotNull String displayName, @NotNull Class<UI> uiClass, @NotNull Getter<S> settingsGetter) {
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
}