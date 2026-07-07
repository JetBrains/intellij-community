// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree;

import com.intellij.psi.impl.source.tree.mvcc.VersionedPsiReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A reference to {@link FileElement}. Can be either versioned or non-versioned (with the semantics of versioned PSI),
 * depending on the properties of {@link com.intellij.psi.FileViewProvider} for which this pointer was created.
 * <p>
 * While mutation operations are copy-on-write, the produced elements can retain memory about past values of {@link FileElement} in order to support versioning.
 * <p>
 * In the majority of cases, the underlying references are either weak or soft, so the dereferencing function is not pure.
 */
@ApiStatus.Internal
public interface ConcreteAstPointer {

  @Nullable FileElement dereference();

  @NotNull ConcreteAstPointer updateWith(@Nullable Supplier<? extends FileElement> supplier);

  @Contract("_, _ -> new")
  static @NotNull ConcreteAstPointer createPointer(boolean isVersioned, @Nullable Supplier<? extends FileElement> initialValue) {
    return isVersioned ? new VersionedAstPointer(initialValue) : new NonVersionedAstPointer(initialValue);
  }
}

class VersionedAstPointer implements ConcreteAstPointer {
  @NotNull final VersionedPsiReference<Supplier<? extends FileElement>> ref;

  VersionedAstPointer(@Nullable Supplier<? extends FileElement> initialValue) {
    this.ref = new VersionedPsiReference<>();
    if (initialValue != null) {
      ref.set(initialValue);
    }
  }

  private VersionedAstPointer(@NotNull VersionedPsiReference<Supplier<? extends FileElement>> ref) {
    this.ref = ref;
  }

  @Override
  public @Nullable FileElement dereference() {
    Supplier<? extends FileElement> supplier = ref.get();
    return supplier == null ? null : supplier.get();
  }

  @Override
  public @NotNull ConcreteAstPointer updateWith(@Nullable Supplier<? extends FileElement> supplier) {
    VersionedPsiReference<Supplier<? extends FileElement>> forkedRef = ref.fork();
    forkedRef.set(supplier);
    return new VersionedAstPointer(forkedRef);
  }

  @Override
  public String toString() {
    return ref.toString();
  }
}

class NonVersionedAstPointer implements ConcreteAstPointer {
  final @Nullable Supplier<? extends FileElement> ref;

  NonVersionedAstPointer(@Nullable Supplier<? extends FileElement> provider) {
    ref = provider;
  }

  @Override
  public @Nullable FileElement dereference() {
    return ref == null ? null : ref.get();
  }

  @Override
  public @NotNull ConcreteAstPointer updateWith(@Nullable Supplier<? extends FileElement> supplier) {
    return new NonVersionedAstPointer(supplier);
  }

  @Override
  public String toString() {
    return ref == null ? "no ref" : "reference installed: " + ref.get();
  }
}
