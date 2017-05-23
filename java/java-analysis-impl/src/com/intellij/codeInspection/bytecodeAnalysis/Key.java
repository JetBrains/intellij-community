/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

public final class Key {
  final Method method;
  final Direction direction;
  final boolean stable;
  final boolean negated;

  public Key(Method method, Direction direction, boolean stable) {
    this.method = method;
    this.direction = direction;
    this.stable = stable;
    this.negated = false;
  }

  Key(Method method, Direction direction, boolean stable, boolean negated) {
    this.method = method;
    this.direction = direction;
    this.stable = stable;
    this.negated = negated;
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
