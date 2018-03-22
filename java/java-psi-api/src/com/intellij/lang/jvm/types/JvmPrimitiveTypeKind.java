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
public enum JvmPrimitiveTypeKind {

  BYTE("byte", JAVA_LANG_BYTE),
  CHAR("char", JAVA_LANG_CHARACTER),
  DOUBLE("double", JAVA_LANG_DOUBLE),
  FLOAT("float", JAVA_LANG_FLOAT),
  INT("int", JAVA_LANG_INTEGER),
  LONG("long", JAVA_LANG_LONG),
  SHORT("short", JAVA_LANG_SHORT),
  BOOLEAN("boolean", JAVA_LANG_BOOLEAN),
  VOID("void", JAVA_LANG_VOID);

  private final String myName;
  private final String myBoxedFqn;

  JvmPrimitiveTypeKind(String name, String boxedFqn) {
    myName = name;
    myBoxedFqn = boxedFqn;
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

  private static final Map<String, JvmPrimitiveTypeKind> ourNameToKind;
  private static final Map<String, JvmPrimitiveTypeKind> ourFqnToKind;

  static {
    THashMap<String, JvmPrimitiveTypeKind> nameToKind = new THashMap<>();
    THashMap<String, JvmPrimitiveTypeKind> fqnToKind = new THashMap<>();
    for (JvmPrimitiveTypeKind kind : values()) {
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
