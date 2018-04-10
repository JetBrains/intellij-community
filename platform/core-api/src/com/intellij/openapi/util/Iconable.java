/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.util;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntObjectMap;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface Iconable {
  int ICON_FLAG_VISIBILITY = 0x0001;
  int ICON_FLAG_READ_STATUS = 0x0002;
  @Deprecated int ICON_FLAG_OPEN = 0x0004;
  @Deprecated int ICON_FLAG_CLOSED = 0x0008;

  Key<Integer> ICON_FLAG_IGNORE_MASK = new Key<>("ICON_FLAG_IGNORE_MASK");

  @MagicConstant(flags = {ICON_FLAG_VISIBILITY, ICON_FLAG_READ_STATUS})
  @interface IconFlags {}

  Icon getIcon(@IconFlags int flags);

  class LastComputedIcon {
    private static final Key<IntObjectMap<Icon>> LAST_COMPUTED_ICON = Key.create("lastComputedIcon");

    @Nullable
    public static Icon get(@NotNull UserDataHolder holder, int flags) {
      IntObjectMap<Icon> map = holder.getUserData(LAST_COMPUTED_ICON);
      return map == null ? null : map.get(flags);
    }

    public static void put(@NotNull UserDataHolder holder, Icon icon, int flags) {
      IntObjectMap<Icon> map = holder.getUserData(LAST_COMPUTED_ICON);
      if (icon == null) {
        if (map != null) {
          map.remove(flags);
        }
      }
      else {
        if (map == null) {
          map = ((UserDataHolderEx)holder).putUserDataIfAbsent(LAST_COMPUTED_ICON, ContainerUtil.createConcurrentIntObjectMap());
        }
        map.put(flags, icon);
      }
    }
  }
}
