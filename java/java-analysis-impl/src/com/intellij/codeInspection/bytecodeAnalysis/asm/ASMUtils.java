/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInspection.bytecodeAnalysis.asm;

import org.jetbrains.annotations.Contract;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicValue;

/**
 * @author lambdamix
 */
public class ASMUtils {
  public static final Type THROWABLE_TYPE = Type.getType("java/lang/Throwable");
  public static final BasicValue THROWABLE_VALUE = new BasicValue(THROWABLE_TYPE);

  @Contract(pure = true)
  public static boolean isReferenceType(Type tp) {
    int sort = tp.getSort();
    return sort == Type.OBJECT || sort == Type.ARRAY;
  }

  @Contract(pure = true)
  public static boolean isBooleanType(Type tp) {
    return Type.BOOLEAN_TYPE.equals(tp);
  }

  @Contract(pure = true)
  public static int getSizeFast(String desc) {
    switch (desc.charAt(0)) {
      case 'J':
      case 'D':
        return 2;
      default:
        return 1;
    }
  }

  @Contract(pure = true)
  public static int getReturnSizeFast(String methodDesc) {
    switch (methodDesc.charAt(methodDesc.indexOf(')') + 1)) {
      case 'J':
      case 'D':
        return 2;
      default:
        return 1;
    }
  }

}
