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
package com.intellij.openapi.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public interface Queryable {

  void putInfo(Map<String, String> info);

  class PrintInfo {
    String[] myIdKeys;
    String[] myInfoKeys;

    public PrintInfo() {
      this(null, null);
    }

    public PrintInfo(@Nullable String[] idKeys) {
      this(idKeys, null);
    }

    public PrintInfo(@Nullable String[] idKeys, @Nullable String[] infoKeys) {
      myIdKeys = idKeys;
      myInfoKeys = infoKeys;
    }
  }

  class Util {
    @Nullable
    public static String print(@NotNull Queryable ui, @Nullable PrintInfo printInfo, @Nullable Contributor contributor) {
      PrintInfo print = printInfo != null ? printInfo : new PrintInfo();

      LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
      ui.putInfo(map);

      if (contributor != null) {
        contributor.apply(map);
      }

      String id = null;

      String[] names = print.myIdKeys != null ? print.myIdKeys : new String[] {"name"};
      for (String eachKey : names) {
        String eachValue = map.get(eachKey);
        if (eachValue != null) {
          id = eachValue;
        }
      }

      if (!map.isEmpty()) {
        id = map.values().iterator().next();
      }

      StringBuffer info = new StringBuffer();
      if (print.myInfoKeys != null) {
        for (String eachKey : print.myInfoKeys) {
          String eachValue = map.get(eachKey);
          if (eachValue != null) {
            if (info.length() > 0) {
              info.append(",");
            }
            info.append(eachKey).append("=").append(eachValue);
          }
        }
      }

      return id + (info.length() > 0 ? " " + info.toString() : "");
    }

    @Nullable
    public static String print(@NotNull Queryable ui, @Nullable PrintInfo printInfo) {
      return print(ui, printInfo, null);
    }
  }

  interface Contributor {
    void apply(Map<String, String> info);
  }

}