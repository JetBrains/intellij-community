/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.codeStyle;

import com.intellij.util.containers.HashMap;

public class VariableKind {
  private final String myName;

  private static HashMap ourNameToObjectMap = new HashMap();

  private VariableKind(String name) {
    myName = name;
    ourNameToObjectMap.put(name, this);
  }

  public String toString() {
    return myName;
  }

  public static VariableKind fromString(String s){
    return (VariableKind)ourNameToObjectMap.get(s);
  }

  public static final VariableKind FIELD = new VariableKind("FIELD");
  public static final VariableKind STATIC_FIELD = new VariableKind("STATIC_FIELD");
  public static final VariableKind STATIC_FINAL_FIELD = new VariableKind("STATIC_FINAL_FIELD");
  public static final VariableKind PARAMETER = new VariableKind("PARAMETER");
  public static final VariableKind LOCAL_VARIABLE = new VariableKind("LOCAL_VARIABLE");
}
