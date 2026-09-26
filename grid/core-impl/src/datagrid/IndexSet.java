package com.intellij.database.datagrid;

import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.JBIterator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * @author gregsh
 */
public abstract class IndexSet<S extends Index> {
  final int[] values;

  IndexSet(int[] indices) {
    values = indices;
  }

  public int size() {
    return values.length;
  }

  public int @NotNull [] asArray() {
    return values.clone();
  }

  public @NotNull List<S> asList() {
    List<S> result = new ArrayList<>(values.length);
    for (int value : values) {
      result.add(forValue(value));
    }
    return result;
  }

  public @NotNull JBIterable<S> asIterable() {
    return new JBIterable<>() {
      @Override
      public Iterator<S> iterator() {
        return new JBIterator<>() {
          private int myNextValueIdx;

          @Override
          protected S nextImpl() {
            return myNextValueIdx < values.length ? forValue(values[myNextValueIdx++]) : stop();
          }
        };
      }
    };
  }

  public @NotNull S first() {
    return values.length == 0 ? forValue(-1) : forValue(values[0]);
  }

  public @NotNull S last() {
    return values.length == 0 ? forValue(-1) : forValue(values[values.length - 1]);
  }

  protected abstract S forValue(int value);

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    IndexSet set = (IndexSet)o;

    return Arrays.equals(values, set.values);
  }

  @Override
  public int hashCode() {
    return values != null ? Arrays.hashCode(values) : 0;
  }

  protected static int @NotNull [] convert(IntUnaryOperator converter, int... ints) {
    for (int i = 0; i < ints.length; i++) {
      ints[i] = converter.applyAsInt(ints[i]);
    }
    return ints;
  }
}
