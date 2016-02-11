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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;
import java.util.List;

public class MacroFactory {
  private static final MultiMap<String, Macro> myMacroTable = init();

  public static Macro createMacro(@NonNls String name) {
    return ContainerUtil.getFirstItem(myMacroTable.get(name));
  }

  public static List<Macro> getMacros(@NonNls String name) {
    return (List<Macro>)myMacroTable.get(name);
  }

  public static Macro[] getMacros() {
    final Collection<? extends Macro> values = myMacroTable.values();
    return values.toArray(new Macro[values.size()]);
  }

  private static MultiMap<String, Macro> init() {
    MultiMap<String, Macro> result = MultiMap.create();
    for(Macro macro: Extensions.getExtensions(Macro.EP_NAME)) {
      result.putValue(macro.getName(), macro);
    }
    return result;
  }

}

