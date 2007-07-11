package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.Macro;
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

    register(new ArrayVariableMacro());
    register(new VariableOfTypeMacro());
    register(new ComponentTypeOfMacro());
    register(new SuggestVariableNameMacro());

    register(new SuggestIndexNameMacro());
    register(new GuessElementTypeMacro());
    register(new ExpectedTypeMacro());
    register(new MethodNameMacro());

    register(new ClassNameMacro());
    register(new QualifiedClassNameMacro());
    register(new LineNumberMacro());
    register(new EnumMacro());

    register(new CapitalizeMacro());
    register(new DecapitalizeMacro());
    register(new FirstWordMacro());
    register(new CompleteMacro());
    register(new CompleteSmartMacro());

    register(new ClassNameCompleteMacro());
    register(new CurrentPackageMacro());
    register(new RightSideTypeMacro());
    register(new CastToLeftSideTypeMacro());

    register(new IterableVariableMacro());
    register(new IterableComponentTypeMacro());
    register(new DescendantClassesEnumMacro());
    register(new SubtypesMacro());
    register(new TypeOfVariableMacro());

    register(new CurrentDateMacro());
    register(new CurrentTimeMacro());
    register(new CurrentUserMacro());
  }

  public static void register(Macro macro) {
    if (myMacroTable == null) init();
    myMacroTable.put(macro.getName(), macro);
  }
}

