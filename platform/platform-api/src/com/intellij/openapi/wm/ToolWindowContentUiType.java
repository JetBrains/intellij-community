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
package com.intellij.openapi.wm;

import com.intellij.openapi.diagnostic.Logger;

public class ToolWindowContentUiType {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.wm.ToolWindowContentUiType");

  public static ToolWindowContentUiType TABBED = new ToolWindowContentUiType("tabs");
  public static ToolWindowContentUiType COMBO = new ToolWindowContentUiType("combo");

  private final String myName;

  private ToolWindowContentUiType(String name) {
    myName = name;
  }

  public String getName() {
    return myName;
  }

  public static ToolWindowContentUiType getInstance(String name) {
    if (TABBED.getName().equals(name)) {
      return TABBED;
    } else if (COMBO.getName().equals(name)) {
      return COMBO;
    } else {
      LOG.debug("Unknown content type=" + name);
      return TABBED;
    }
  }
}
