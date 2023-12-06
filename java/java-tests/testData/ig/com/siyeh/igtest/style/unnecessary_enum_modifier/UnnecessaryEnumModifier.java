package com.siyeh.igtest.style.unnecessary_enum_modifier;

public enum UnnecessaryEnumModifier {
    Red, Green, Blue;

    <warning descr="Modifier 'private' is redundant for enum constructors">private</warning> UnnecessaryEnumModifier() {
    }

    <warning descr="Modifier 'static' is redundant for inner enums">static</warning> enum X {
      A, B, C
    }
}