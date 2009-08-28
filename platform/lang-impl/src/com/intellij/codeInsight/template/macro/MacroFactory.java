package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.Macro;
import com.intellij.openapi.extensions.Extensions;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;
import java.util.Hashtable;

public class MacroFactory {
  private static Hashtable<String,Macro> myMacroTable = null;

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
    myMacroTable = new Hashtable<String, Macro>();

    for(Macro macro: Extensions.getExtensions(Macro.EP_NAME)) {
      register(macro);
    }
  }

  public static void register(Macro macro) {
    if (myMacroTable == null) init();
    myMacroTable.put(macro.getName(), macro);
  }
}

