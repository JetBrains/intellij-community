/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ui.speedSearch;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.regex.Matcher;

/**
 * User: spLeaner
 */
public abstract class SpeedSearchSupply {
  protected static final Key SPEED_SEARCH_COMPONENT_MARKER = new Key("SPEED_SEARCH_COMPONENT_MARKER");

  @Nullable
  public static SpeedSearchSupply getSupply(@NotNull final JComponent speedSearchEnabledComponent) {
      SpeedSearchSupply speedSearch = (SpeedSearchSupply) speedSearchEnabledComponent.getClientProperty(SPEED_SEARCH_COMPONENT_MARKER);
      return speedSearch != null && speedSearch.isPopupActive() ? speedSearch : null;
  }

  public abstract boolean isPopupActive();

  @Nullable
  public abstract Matcher compareAndGetMatcher(@NotNull final String text);
}
