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

package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.Macro;
import com.intellij.openapi.extensions.Extensions;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;
import java.util.HashMap;

public class MacroFactory {
  private static HashMap<String,Macro> myMacroTable = null;

  private MacroFactory() {
  }

  public static Macro createMacro(@NonNls String name) {
    if(myMacroTable == null) {
      init();
    }

    return myMacroTable.get(name);
  }

  public static Macro[] getMacros() {
    if(myMacroTable == null) {
      init();
    }

    final Collection<Macro> values = myMacroTable.values();
    return values.toArray(new Macro[values.size()]);
  }

  private static void init() {
    myMacroTable = new HashMap<String, Macro>();

    for(Macro macro: Extensions.getExtensions(Macro.EP_NAME)) {
      myMacroTable.put(macro.getName(), macro);
    }
  }

  /**
   * @deprecated use com.intellij.liveTemplateMacro extension point instead
   */
  public static void register(Macro macro) {
    if (myMacroTable == null) init();
    myMacroTable.put(macro.getName(), macro);
  }
}

