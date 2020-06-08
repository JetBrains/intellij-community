// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public interface Queryable {

  void putInfo(@NotNull Map<String, String> info);

  class PrintInfo {
    private final String[] myIdKeys;
    private final String[] myInfoKeys;

    public PrintInfo() {
      this(null, null);
    }

    public PrintInfo(String @Nullable [] idKeys) {
      this(idKeys, null);
    }

    public PrintInfo(String @Nullable [] idKeys, String @Nullable [] infoKeys) {
      myIdKeys = idKeys;
      myInfoKeys = infoKeys;
    }
  }

  final class Util {
    @Nullable
    public static String print(@NotNull Queryable ui, @Nullable PrintInfo printInfo, @Nullable Contributor contributor) {
      PrintInfo print = printInfo != null ? printInfo : new PrintInfo();

      LinkedHashMap<String, String> map = new LinkedHashMap<>();
      ui.putInfo(map);

      if (contributor != null) {
        contributor.apply(map);
      }

      String id = null;

      //String[] names = print.myIdKeys != null ? print.myIdKeys : new String[] {"name"};
      //for (String eachKey : names) {
      //  String eachValue = map.get(eachKey);
      //  if (eachValue != null) {
      //    id = eachValue;
      //  }
      //}

      if (!map.isEmpty()) {
        id = map.values().iterator().next();
      }

      StringBuilder info = new StringBuilder();
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

      return id + (info.length() > 0 ? " " + info : "");
    }

    @Nullable
    public static String print(@NotNull Queryable ui, @Nullable PrintInfo printInfo) {
      return print(ui, printInfo, null);
    }
  }

  interface Contributor {
    void apply(@NotNull Map<String, String> info);
  }

}