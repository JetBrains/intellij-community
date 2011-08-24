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

import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

public interface Iconable {
  int ICON_FLAG_VISIBILITY = 0x0001;
  int ICON_FLAG_READ_STATUS = 0x0002;
  int ICON_FLAG_OPEN = 0x0004;
  int ICON_FLAG_CLOSED = 0x0008;

  Icon getIcon(int flags);


  class LastComputedIcon {
    private static final Key<Map<Integer, Icon>> LAST_COMPUTED_ICON = Key.create("lastComputedIcon");

    @Nullable
    public static Icon get(UserDataHolder holder, int flags) {
      Map<Integer, Icon> map = holder.getUserData(LAST_COMPUTED_ICON);
      return map != null ? map.get(flags) : null;
    }

    public static void put(UserDataHolder holder, Icon icon, int flags) {
      Map<Integer, Icon> map = holder.getUserData(LAST_COMPUTED_ICON);
      if (map == null) {
        map = new HashMap<Integer, Icon>();
        holder.putUserData(LAST_COMPUTED_ICON, map);
      }

      map.put(flags, icon);
    }
  }


}