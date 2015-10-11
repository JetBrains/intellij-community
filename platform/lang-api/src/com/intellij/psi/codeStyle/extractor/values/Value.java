/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.extractor.values;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Value {
  public enum STATE {
    INIT,
    SELECTED,
    ANY
  }

  public static abstract class VAR_KIND {

    public int getMutagenFactor() {
      return 1;
    }

    @NotNull
    private Object[] myPossibleValues;

    VAR_KIND(@NotNull Object[] possibleValues) {
      myPossibleValues = possibleValues;
    }

    @NotNull
    public Object[] getPossibleValues() {
      return myPossibleValues;
    }

    public abstract boolean accepts(@NotNull String name, @NotNull Object value);

    @NotNull
    private static Object[] getRMValues() {
      int from = 10;
      int to = 120;
      Object[] ret = new Object[to - from + 1];
      ret[0] = -1; //any
      for (int i = to; i > from; --i) ret[i - from] = i;
      return ret;
    }

    //----------------Default var kinds go here---------------------
    public static final VAR_KIND RIGHT_MARGIN = new CLASS_BASED_VAR_KIND(getRMValues(), Integer.class) {
      @Override
      public boolean acceptsName(@NotNull String name) {
        return name.equals("RIGHT_MARGIN");
      }
      @Override
      public int getMutagenFactor() {
        return 0;
      }
    };

    public static final VAR_KIND BLANK = new CLASS_BASED_VAR_KIND(new Object[]{0, 1, 2}, Integer.class) {
      @Override
      public boolean acceptsName(@NotNull String name) {
        return name.contains("BLANK");
      }
      @Override
      public int getMutagenFactor() {
        return 2;
      }
    };

    public static final VAR_KIND INDENT = new CLASS_BASED_VAR_KIND(new Object[]{0, 1, 2, 3, 4, 5, 6, 7, 8}, Integer.class) {
      @Override
      public boolean acceptsName(@NotNull String name) {
        return name.contains("INDENT");
      }
      @Override
      public int getMutagenFactor() {
        return 7;
      }
    };

    public static final VAR_KIND DEFAULT = new CLASS_BASED_VAR_KIND(new Object[]{0, 1, 2, 3, 4, 5, 6, 7, 8}, Integer.class) {
      @Override
      public boolean acceptsName(@NotNull String name) {
        return true;
      }
    };

    public static final VAR_KIND TAB_SIZE = new CLASS_BASED_VAR_KIND(new Object[]{2, 4, 8}, Integer.class) {
      @Override
      public boolean acceptsName(@NotNull String name) {
        return name.contains("TAB_SIZE");
      }
      @Override
      public int getMutagenFactor() {
        return 2;
      }
    };

    public static final VAR_KIND WRAP = new CLASS_BASED_VAR_KIND(new Object[]{0, 1, 2, 5}, Integer.class) {
      @Override
      public boolean acceptsName(@NotNull String name) {
        return name.endsWith("_WRAP");
      }
    };

    public static final VAR_KIND BRACE_STYLE = new CLASS_BASED_VAR_KIND(new Object[]{1, 2, 3, 4, 5}, Integer.class) {
      @Override
      public boolean acceptsName(@NotNull String name) {
        return name.endsWith("BRACE_STYLE") || name.endsWith("BRACE_PLACEMENT");
      }
      @Override
      public int getMutagenFactor() {
        return 7;
      }
    };

    public static final VAR_KIND KEEP = new CLASS_BASED_VAR_KIND(new Object[]{true, false}, Boolean.class) {
      @Override
      public boolean acceptsName(@NotNull String name) {
        return name.contains("KEEP");
      }
      @Override
      public int getMutagenFactor() {
        return 3;
      }
    };

    public static final VAR_KIND BOOL = new CLASS_BASED_VAR_KIND(new Object[]{true, false}, Boolean.class) {
      @Override
      public boolean acceptsName(@NotNull String name) {
        return true;
      }
    };

    public static final VAR_KIND NOTHING = new VAR_KIND(new Object[]{}) {
      @Override
      public boolean accepts(@NotNull String name, @NotNull Object value) {
        return true;
      }
    };

    public static final VAR_KIND[] defaultKinds = {RIGHT_MARGIN, WRAP, BRACE_STYLE, TAB_SIZE, INDENT, BLANK, DEFAULT,
        KEEP, BOOL, NOTHING};
  }

  public static abstract class CLASS_BASED_VAR_KIND extends VAR_KIND {

    private final Class<?> myVarClass;

    public CLASS_BASED_VAR_KIND(@NotNull Object[] possibleValues, @NotNull Class<?> varClass) {
      super(possibleValues);
      myVarClass = varClass;
    }

    @Override
    public boolean accepts(@NotNull String name, @NotNull Object value) {
      return value.getClass().equals(myVarClass) && acceptsName(name);
    }

    public abstract boolean acceptsName(@NotNull String name);
  }

  public Object[] getPossibleValues() {
    return kind.getPossibleValues();
  }

  public int getMutagenFactor() {
    return kind.getMutagenFactor();
  }

  @NotNull
  public final String name;
  @NotNull
  public Object value;
  @NotNull
  public STATE state;
  @NotNull
  public ClassSerializer serializer;
  @NotNull
  public final VAR_KIND kind;

  public Value(@NotNull String name, @NotNull Object value, @NotNull ClassSerializer serializer, @NotNull VAR_KIND kind) {
    this.kind = kind;
    this.name = name;
    if (value instanceof Integer && ((Integer)value) == 0 && kind == VAR_KIND.BRACE_STYLE) {
      this.value = 1;
    }
    else {
      this.value = value;
    }

    state = STATE.INIT;
    this.serializer = serializer;
  }

  public Value(@NotNull Value valueZ) {
    name = valueZ.name;
    value = valueZ.value;
    state = valueZ.state;
    serializer = valueZ.serializer;
    kind = valueZ.kind;
  }

  @Nullable
  @Contract("false -> null")
  public Value write(boolean retPrevValue) {
    final Object orig = serializer.write(name, value, retPrevValue);
    return orig == null ? null : new Value(name, orig, serializer, kind);
  }

  @Override
  public String toString() {
    return name + "=" + value + ";";
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof Value) {
      Value other = (Value) o;
      return other.name.equals(name) && other.serializer.equals(serializer);
    } else {
      return false;
    }
  }
}
