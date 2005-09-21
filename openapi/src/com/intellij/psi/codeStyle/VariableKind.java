/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle;

import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;

/**
 * Defines different types of variables for which the code style specifies naming settings.
 *
 * @see CodeStyleManager#getVariableKind(com.intellij.psi.PsiVariable)
 */
public class VariableKind {
  private final String myName;

  private static HashMap<String,VariableKind> ourNameToObjectMap = new HashMap<String, VariableKind>();

  private VariableKind(@NonNls String name) {
    myName = name;
    ourNameToObjectMap.put(name, this);
  }

  public String toString() {
    return myName;
  }

  public static VariableKind fromString(String s){
    return ourNameToObjectMap.get(s);
  }

  public static final VariableKind FIELD = new VariableKind("FIELD");
  public static final VariableKind STATIC_FIELD = new VariableKind("STATIC_FIELD");
  public static final VariableKind STATIC_FINAL_FIELD = new VariableKind("STATIC_FINAL_FIELD");
  public static final VariableKind PARAMETER = new VariableKind("PARAMETER");
  public static final VariableKind LOCAL_VARIABLE = new VariableKind("LOCAL_VARIABLE");
}
