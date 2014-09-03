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
package com.intellij.codeInspection.bytecodeAnalysis;

final class Method {
  final String internalClassName;
  final String methodName;
  final String methodDesc;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Method method = (Method) o;
    return internalClassName.equals(method.internalClassName) && methodDesc.equals(method.methodDesc) && methodName.equals(method.methodName);
  }

  @Override
  public int hashCode() {
    int result = internalClassName.hashCode();
    result = 31 * result + methodName.hashCode();
    result = 31 * result + methodDesc.hashCode();
    return result;
  }

  Method(String internalClassName, String methodName, String methodDesc) {
    this.internalClassName = internalClassName;
    this.methodName = methodName;
    this.methodDesc = methodDesc;
  }

  @Override
  public String toString() {
    return internalClassName + ' ' + methodName + ' ' + methodDesc;
  }
}

enum Value {
  Bot, NotNull, Null, True, False, Top
}

interface Direction {
  final class In implements Direction {
    static final int NOT_NULL = 0;
    static final int NULLABLE = 1;
    final int paramIndex;
    final int nullityMask;

    In(int paramIndex, int nullityMask) {
      this.paramIndex = paramIndex;
      this.nullityMask = nullityMask;
    }

    @Override
    public String toString() {
      return "In " + paramIndex;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      In in = (In)o;
      if (paramIndex != in.paramIndex) return false;
      if (nullityMask != in.nullityMask) return false;
      return true;
    }

    @Override
    public int hashCode() {
      return 31 * paramIndex + nullityMask;
    }

    public int paramId() {
      return paramIndex;
    }
  }

  final class InOut implements Direction {
    final int paramIndex;
    final Value inValue;

    InOut(int paramIndex, Value inValue) {
      this.paramIndex = paramIndex;
      this.inValue = inValue;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      InOut inOut = (InOut)o;

      if (paramIndex != inOut.paramIndex) return false;
      if (inValue != inOut.inValue) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = paramIndex;
      result = 31 * result + inValue.ordinal();
      return result;
    }

    @Override
    public String toString() {
      return "InOut " + paramIndex + " " + inValue.toString();
    }

    public int paramId() {
      return paramIndex;
    }

    public int valueId() {
      return inValue.ordinal();
    }
  }

  Direction Out = new Direction() {
    @Override
    public String toString() {
      return "Out";
    }

    @Override
    public int hashCode() {
      return -1;
    }
  };

  Direction NullableOut = new Direction() {
    @Override
    public String toString() {
      return "NullableOut";
    }

    @Override
    public int hashCode() {
      return -2;
    }
  };
}

final class Key {
  final Method method;
  final Direction direction;
  final boolean stable;

  Key(Method method, Direction direction, boolean stable) {
    this.method = method;
    this.direction = direction;
    this.stable = stable;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Key key = (Key) o;

    if (!direction.equals(key.direction)) return false;
    if (!method.equals(key.method)) return false;
    if (stable != key.stable) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = method.hashCode();
    result = 31 * result + direction.hashCode();
    result = 31 * result + (stable ? 1 : 0);
    return result;
  }

  @Override
  public String toString() {
    return method + " " + direction + " " + stable;
  }
}
