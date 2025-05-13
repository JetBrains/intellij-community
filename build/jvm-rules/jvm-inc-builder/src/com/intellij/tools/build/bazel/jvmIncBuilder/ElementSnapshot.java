// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.ExternalizableGraphElement;
import org.jetbrains.jps.dependency.GraphDataOutput;
import org.jetbrains.jps.javac.Iterators;

import java.io.IOException;
import java.util.function.Predicate;

public interface ElementSnapshot<T extends ExternalizableGraphElement> {

  @NotNull
  Iterable<@NotNull T> getElements();

  @NotNull
  String getDigest(T elem);

  void write(GraphDataOutput out) throws IOException;

  static <T extends ExternalizableGraphElement> ElementSnapshot<T> derive(ElementSnapshot<T> snapshot, Predicate<? super T> pred) {
    return new ElementSnapshot<>() {
      @Override
      public @NotNull Iterable<@NotNull T> getElements() {
        return Iterators.filter(snapshot.getElements(), pred::test);
      }

      @Override
      public @NotNull String getDigest(T elem) {
        return snapshot.getDigest(elem);
      }

      @Override
      public void write(GraphDataOutput out) throws IOException {
        snapshot.write(out);
      }
    };
  }
}
