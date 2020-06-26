// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ToolWindowContentUiType {
  private static final Logger LOG = Logger.getInstance(ToolWindowContentUiType.class);

  public static final ToolWindowContentUiType TABBED = new ToolWindowContentUiType("tabs");
  public static final ToolWindowContentUiType COMBO = new ToolWindowContentUiType("combo");

  private final String myName;

  private ToolWindowContentUiType(String name) {
    myName = name;
  }

  public String getName() {
    return myName;
  }

  @NotNull
  public static ToolWindowContentUiType getInstance(@Nullable String name) {
    if (TABBED.getName().equals(name)) {
      return TABBED;
    }
    else if (COMBO.getName().equals(name)) {
      return COMBO;
    }
    else {
      LOG.debug("Unknown content type=" + name);
      return TABBED;
    }
  }

  @Override
  public String toString() {
    return "ToolWindowContentUiType{" +
           "myName='" + myName + '\'' +
           '}';
  }
}
