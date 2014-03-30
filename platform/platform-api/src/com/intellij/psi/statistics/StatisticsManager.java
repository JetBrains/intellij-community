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
package com.intellij.psi.statistics;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.KeyedExtensionCollector;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class StatisticsManager implements SettingsSavingComponent {
  public static final int OBLIVION_THRESHOLD = 7;
  private static final KeyedExtensionCollector<Statistician,Key> COLLECTOR = new KeyedExtensionCollector<Statistician, Key>("com.intellij.statistician") {
    @Override
    @NotNull
    protected String keyToString(@NotNull final Key key) {
      return key.toString();
    }
  };

  @Nullable
  public static <T,Loc> StatisticsInfo serialize(Key<? extends Statistician<T,Loc>> key, T element, Loc location) {
    for (final Statistician<T,Loc> statistician : COLLECTOR.forKey(key)) {
      final StatisticsInfo info = statistician.serialize(element, location);
      if (info != null) return info;
    }
    return null;
  }


  public static StatisticsManager getInstance() {
    return ServiceManager.getService(StatisticsManager.class);
  }

  public abstract int getUseCount(@NotNull StatisticsInfo info);
  public abstract int getLastUseRecency(@NotNull StatisticsInfo info);
  public abstract void incUseCount(@NotNull StatisticsInfo info);

  public <T,Loc> int getUseCount(final Key<? extends Statistician<T, Loc>> key, final T element, final Loc location) {
    final StatisticsInfo info = serialize(key, element, location);
    return info == null ? 0 : getUseCount(info);
  }

  public <T, Loc> void incUseCount(final Key<? extends Statistician<T, Loc>> key,
                                                                 final T element,
                                                                 final Loc location) {
    final StatisticsInfo info = serialize(key, element, location);
    if (info != null) {
      incUseCount(info);
    }
  }

  /**
   * @param context
   * @return infos by this context ordered by usage time: recent first
   */
  public abstract StatisticsInfo[] getAllValues(@NonNls String context);
}
