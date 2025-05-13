// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.ElementSnapshot;
import com.intellij.tools.build.bazel.jvmIncBuilder.ElementSnapshotDelta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.ExternalizableGraphElement;
import org.jetbrains.jps.dependency.diff.Difference;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static org.jetbrains.jps.javac.Iterators.*;

public class ElementSnapshotDeltaImpl<T extends ExternalizableGraphElement> implements ElementSnapshotDelta<T> {
  private final ElementSnapshot<T> myBaseSnapshot;
  private final Iterable<T> myDeleted;
  private final Iterable<T> myModified;

  public ElementSnapshotDeltaImpl(ElementSnapshot<T> base) {
    myBaseSnapshot = base;
    myDeleted = Set.of();
    myModified = Set.of();
  }

  public ElementSnapshotDeltaImpl(ElementSnapshot<T> pastSnapshot, ElementSnapshot<T> presentSnapshot) {
    myBaseSnapshot = presentSnapshot;
    Iterable<T> modified;
    Iterable<T> deleted;
    if (!isEmpty(pastSnapshot.getElements())) {
      Difference.Specifier<@NotNull T, Difference> diff = Difference.deepDiff(
        pastSnapshot.getElements(), presentSnapshot.getElements(), T::equals, T::hashCode, (pastElem, presentElem) -> () -> Objects.equals(pastSnapshot.getDigest(pastElem), presentSnapshot.getDigest(presentElem))
      );
      deleted = collect(diff.removed(), new HashSet<>());
      modified = collect(flat(map(diff.changed(), Difference.Change::getPast), diff.added()), new HashSet<>());
    }
    else {
      deleted = Set.of();
      modified = presentSnapshot.getElements();
    }
    myModified = modified;
    myDeleted = deleted;
  }

  @Override
  public @NotNull ElementSnapshot<T> getBaseSnapshot() {
    return myBaseSnapshot;
  }

  @Override
  public @NotNull Iterable<@NotNull T> getDeleted() {
    return myDeleted;
  }

  @Override
  public @NotNull Iterable<@NotNull T> getModified() {
    return myModified;
  }
}
