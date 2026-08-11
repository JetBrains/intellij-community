// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.Macro;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;
import java.util.List;

public final class MacroFactory {
  public static Macro createMacro(@NonNls String name) {
    return ContainerUtil.getFirstItem(MacroService.getInstance().getMacroTable().get(name));
  }

  public static List<Macro> getMacros(@NonNls String name) {
    return (List<Macro>)MacroService.getInstance().getMacroTable().get(name);
  }

  public static Macro[] getMacros() {
    final Collection<? extends Macro> values = MacroService.getInstance().getMacroTable().values();
    return values.toArray(new Macro[0]);
  }
}
