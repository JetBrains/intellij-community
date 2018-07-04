// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.types;

import gnu.trove.THashMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static com.intellij.psi.CommonClassNames.*;

/**
 * @since 2018.2
 */
public final class JvmPrimitiveTypeKind {

  public static final JvmPrimitiveTypeKind BOOLEAN = new JvmPrimitiveTypeKind("boolean", JAVA_LANG_BOOLEAN, "Z");
  public static final JvmPrimitiveTypeKind BYTE = new JvmPrimitiveTypeKind("byte", JAVA_LANG_BYTE, "B");
  public static final JvmPrimitiveTypeKind CHAR = new JvmPrimitiveTypeKind("char", JAVA_LANG_CHARACTER, "C");
  public static final JvmPrimitiveTypeKind DOUBLE = new JvmPrimitiveTypeKind("double", JAVA_LANG_DOUBLE, "D");
  public static final JvmPrimitiveTypeKind FLOAT = new JvmPrimitiveTypeKind("float", JAVA_LANG_FLOAT, "F");
  public static final JvmPrimitiveTypeKind INT = new JvmPrimitiveTypeKind("int", JAVA_LANG_INTEGER, "I");
  public static final JvmPrimitiveTypeKind LONG = new JvmPrimitiveTypeKind("long", JAVA_LANG_LONG, "J");
  public static final JvmPrimitiveTypeKind SHORT = new JvmPrimitiveTypeKind("short", JAVA_LANG_SHORT, "S");
  public static final JvmPrimitiveTypeKind VOID = new JvmPrimitiveTypeKind("void", JAVA_LANG_VOID, "V");

  private final String myName;
  private final String myBoxedFqn;
  private final String myBinaryName;

  private JvmPrimitiveTypeKind(String name, String boxedFqn, String binaryName) {
    myName = name;
    myBoxedFqn = boxedFqn;
    myBinaryName = binaryName;
  }

  @Contract(pure = true)
  @NotNull
  public String getName() {
    return myName;
  }

  @Contract(pure = true)
  @NotNull
  public String getBoxedFqn() {
    return myBoxedFqn;
  }

  @Contract(pure = true)
  @NotNull
  public String getBinaryName() {
    return myBinaryName;
  }

  private static final Map<String, JvmPrimitiveTypeKind> ourNameToKind;
  private static final Map<String, JvmPrimitiveTypeKind> ourFqnToKind;

  static {
    JvmPrimitiveTypeKind[] values = {BOOLEAN, BYTE, CHAR, DOUBLE, FLOAT, INT, LONG, SHORT, VOID};
    THashMap<String, JvmPrimitiveTypeKind> nameToKind = new THashMap<>();
    THashMap<String, JvmPrimitiveTypeKind> fqnToKind = new THashMap<>();
    for (JvmPrimitiveTypeKind kind : values) {
      nameToKind.put(kind.getName(), kind);
      fqnToKind.put(kind.getBoxedFqn(), kind);
    }
    nameToKind.compact();
    fqnToKind.compact();
    ourNameToKind = nameToKind;
    ourFqnToKind = fqnToKind;
  }

  @Contract(value = "null -> null", pure = true)
  @Nullable
  public static JvmPrimitiveTypeKind getKindByName(@Nullable String name) {
    return ourNameToKind.get(name);
  }

  @Contract(value = "null -> null", pure = true)
  @Nullable
  public static JvmPrimitiveTypeKind getKindByFqn(@Nullable String fqn) {
    return ourFqnToKind.get(fqn);
  }

  @Contract(pure = true)
  @NotNull
  public static Collection<String> getBoxedFqns() {
    return Collections.unmodifiableCollection(ourFqnToKind.keySet());
  }
}
