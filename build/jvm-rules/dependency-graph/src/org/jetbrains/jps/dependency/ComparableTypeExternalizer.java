// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import java.io.IOException;
import java.util.Comparator;
import java.util.function.Function;

public interface ComparableTypeExternalizer<T> extends Externalizer<T>, Comparator<T> {

  T[] createStorage(int size);

  static <T extends ExternalizableGraphElement> ComparableTypeExternalizer<T> forGraphElement(DataReader<? extends T> reader, Function<Integer, T[]> arrayFactory, Comparator<T> comparator) {
    return new ComparableTypeExternalizer<>() {
      @Override
      public int compare(T o1, T o2) {
        return comparator.compare(o1, o2);
      }

      @Override
      public T[] createStorage(int size) {
        return arrayFactory.apply(size);
      }

      @Override
      public T load(GraphDataInput in) throws IOException {
        return reader.load(in);
      }

      @Override
      public void save(GraphDataOutput out, T value) throws IOException {
        value.write(out);
      }
    };
  }

  static <T extends ExternalizableGraphElement> ComparableTypeExternalizer<T> forAnyGraphElement(Function<Integer, T[]> arrayFactory, Comparator<? super T> comparator) {
    
    return new ComparableTypeExternalizer<>() {
      @Override
      public int compare(T o1, T o2) {
        return comparator.compare(o1, o2);
      }

      @Override
      public T[] createStorage(int size) {
        return arrayFactory.apply(size);
      }

      @Override
      public T load(GraphDataInput in) throws IOException {
        return in.readGraphElement();
      }

      @Override
      public void save(GraphDataOutput out, T value) throws IOException {
        out.writeGraphElement(value);
      }
    };
  }
}
