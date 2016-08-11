/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.GutterIconDescriptor;
import com.intellij.codeInsight.daemon.LineMarkerSettings;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
@State(
  name = "LineMarkerSettings",
  storages = @Storage("gutter.xml")
)
public class LineMarkerSettingsImpl extends LineMarkerSettings implements PersistentStateComponent<LineMarkerSettingsImpl> {

  @Override
  public boolean isEnabled(@NotNull GutterIconDescriptor descriptor) {
    Boolean aBoolean = providers.get(descriptor.getId());
    return aBoolean == null || aBoolean;
  }

  @Override
  public void setEnabled(@NotNull GutterIconDescriptor descriptor, boolean selected) {
    providers.put(descriptor.getId(), selected);
  }

  @MapAnnotation
  public Map<String, Boolean> providers = new HashMap<>();

  @Nullable
  @Override
  public LineMarkerSettingsImpl getState() {
    return this;
  }

  @Override
  public void loadState(LineMarkerSettingsImpl state) {
    providers.clear();
    providers.putAll(state.providers);
  }
}
