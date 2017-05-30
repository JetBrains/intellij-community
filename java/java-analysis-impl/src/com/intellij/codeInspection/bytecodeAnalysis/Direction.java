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

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public abstract class Direction {
  public static final Direction Out = explicitDirection("Out");
  public static final Direction NullableOut = explicitDirection("NullableOut");
  public static final Direction Pure = explicitDirection("Pure");
  public static final Direction Throw = explicitDirection("Throw");

  private static final List<Direction> ourConcreteDirections = Arrays.asList(Out, NullableOut, Pure, Throw);
  private static final int CONCRETE_DIRECTIONS_OFFSET = ourConcreteDirections.size();
  private static final int IN_OUT_OFFSET = 2; // nullity mask is 0/1
  private static final int IN_THROW_OFFSET = 2 + Value.values().length;
  private static final int DIRECTIONS_PER_PARAM_ID = IN_THROW_OFFSET + Value.values().length;

  /**
   * Converts int to Direction object.
   *
   * @param directionKey int representation of direction
   * @return Direction object
   * @see #asInt()
   */
  @NotNull
  static Direction fromInt(int directionKey) {
    if(directionKey < CONCRETE_DIRECTIONS_OFFSET) {
      return ourConcreteDirections.get(directionKey);
    }
    int paramKey = directionKey - CONCRETE_DIRECTIONS_OFFSET;
    int paramId = paramKey / DIRECTIONS_PER_PARAM_ID;
    int subDirectionId = paramKey % DIRECTIONS_PER_PARAM_ID;
    // 0 - 1 - @NotNull, @Nullable, parameter
    if (subDirectionId < IN_OUT_OFFSET) {
      return new In(paramId, subDirectionId == 1);
    }
    if (subDirectionId < IN_THROW_OFFSET) {
      int valueId = subDirectionId - IN_OUT_OFFSET;
      return new InOut(paramId, Value.values()[valueId]);
    }
    int valueId = subDirectionId - IN_THROW_OFFSET;
    return new InThrow(paramId, Value.values()[valueId]);
  }

  /**
   * Encodes Direction object as non-negative int.
   *
   * @return unique int for direction
   */
  abstract int asInt();

  @Override
  public int hashCode() {
    return asInt();
  }

  @Override
  public boolean equals(Object obj) {
    if(obj == this) return true;
    if(obj == null || obj.getClass() != this.getClass()) return false;
    return asInt() == ((Direction)obj).asInt();
  }

  @NotNull
  private static Direction explicitDirection(String name) {
    return new Direction() {
      @Override
      int asInt() {
        for (int i = 0; i < ourConcreteDirections.size(); i++) {
          if(ourConcreteDirections.get(i) == this) return i;
        }
        throw new InternalError("Explicit direction absent in ourConcreteDirections: " + name);
      }

      @Override
      public String toString() {
        return name;
      }
    };
  }

  abstract static class ParamIdBasedDirection extends Direction {
    final int paramIndex;

    protected ParamIdBasedDirection(int index) {
      paramIndex = index;
    }

    public int paramId() {
      return paramIndex;
    }

    @Override
    int asInt() {
      return CONCRETE_DIRECTIONS_OFFSET + DIRECTIONS_PER_PARAM_ID * this.paramId();
    }
  }

  static final class In extends ParamIdBasedDirection {
    final boolean nullable;

    In(int paramIndex, boolean nullable) {
      super(paramIndex);
      this.nullable = nullable;
    }

    @Override
    int asInt() {
      return super.asInt() + (nullable ? 1 : 0);
    }

    @Override
    public String toString() {
      return "In " + paramIndex + "(" + (nullable ? "nullable" : "not null") + ")";
    }
  }

  static abstract class ParamValueBasedDirection extends ParamIdBasedDirection {
    final Value inValue;

    ParamValueBasedDirection(int paramIndex, Value inValue) {
      super(paramIndex);
      this.inValue = inValue;
    }

    abstract ParamValueBasedDirection withIndex(int paramIndex);
  }

  static final class InOut extends ParamValueBasedDirection {
    InOut(int paramIndex, Value inValue) {
      super(paramIndex, inValue);
    }

    @Override
    InOut withIndex(int paramIndex) {
      return new InOut(paramIndex, inValue);
    }

    @Override
    int asInt() {
      return super.asInt() + IN_OUT_OFFSET + inValue.ordinal();
    }

    @Override
    public String toString() {
      return "InOut " + paramIndex + " " + inValue.toString();
    }
  }

  static final class InThrow extends ParamValueBasedDirection {
    InThrow(int paramIndex, Value inValue) {
      super(paramIndex, inValue);
    }

    @Override
    InThrow withIndex(int paramIndex) {
      return new InThrow(paramIndex, inValue);
    }

    @Override
    int asInt() {
      return super.asInt() + IN_THROW_OFFSET + inValue.ordinal();
    }

    @Override
    public String toString() {
      return "InThrow " + paramIndex + " " + inValue.toString();
    }
  }
}

