/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.backwardRefs.javac.ast.api;

import org.jetbrains.annotations.NotNull;

public abstract class JavacDef {
  private final JavacRef myDefinedElement;

  public JavacDef(JavacRef element) {
    myDefinedElement = element;
  }

  @NotNull
  public JavacRef getDefinedElement() {
    return myDefinedElement;
  }

  public static class JavacFunExprDef extends JavacDef {
    public JavacFunExprDef(JavacRef functionalInterface) {
      super(functionalInterface);
    }
  }

  public static class JavacClassDef extends JavacDef {
    private final JavacRef[] myClasses;

    public JavacClassDef(JavacRef aClass, JavacRef[] superClasses) {
      super(aClass);
      myClasses = superClasses;
    }

    @NotNull
    public JavacRef[] getSuperClasses() {
      return myClasses;
    }
  }

  public static class JavacMemberDef extends JavacDef {
    private final JavacRef myRawReturnType;
    private final byte myArrayDimension;
    private final boolean myStatic;

    public JavacMemberDef(JavacRef element, JavacRef rawReturnType, byte dimension, boolean isStatic) {
      super(element);
      myRawReturnType = rawReturnType;
      myArrayDimension = dimension;
      myStatic = isStatic;
    }

    public JavacRef getReturnType() {
      return myRawReturnType;
    }

    public byte getIteratorKind() {
      return myArrayDimension;
    }

    public boolean isStatic() {
      return myStatic;
    }
  }
}
