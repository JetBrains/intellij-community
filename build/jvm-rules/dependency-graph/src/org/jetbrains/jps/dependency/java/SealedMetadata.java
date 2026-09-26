// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.jps.dependency.GraphDataInput;
import org.jetbrains.jps.dependency.GraphDataOutput;
import org.jetbrains.jps.dependency.diff.DiffCapable;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.dependency.impl.RW;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Persisted image of a sealed class file's PermittedSubclasses attribute (JVMS 4.7.31): the names of the permitted direct subclasses.
 */
public final class SealedMetadata implements JvmMetadata<SealedMetadata, SealedMetadata.Diff> {

  private final Iterable<String> myPermittedSubclasses;

  public SealedMetadata(Iterable<String> permittedSubclasses) {
    myPermittedSubclasses = permittedSubclasses;
  }

  public SealedMetadata(GraphDataInput in) throws IOException {
    myPermittedSubclasses = RW.readCollection(in, () -> in.readUTF());
  }

  @Override
  public void write(GraphDataOutput out) throws IOException {
    RW.writeCollection(out, myPermittedSubclasses, s -> out.writeUTF(s));
  }

  public Iterable<String> getPermittedSubclasses() {
    return myPermittedSubclasses;
  }

  @Override
  public boolean isSame(DiffCapable<?, ?> other) {
    return other instanceof SealedMetadata;
  }

  @Override
  public int diffHashCode() {
    return SealedMetadata.class.hashCode();
  }

  @Override
  public Diff difference(SealedMetadata past) {
    return new Diff(past);
  }

  public final class Diff implements Difference {
    private final Supplier<Specifier<String, ?>> myPermittedSubclassesDiff;

    public Diff(SealedMetadata past) {
      myPermittedSubclassesDiff = Utils.lazyValue(() -> Difference.diff(past.getPermittedSubclasses(), getPermittedSubclasses()));
    }

    @Override
    public boolean unchanged() {
      return permittedSubclasses().unchanged();
    }

    public Specifier<String, ?> permittedSubclasses() {
      return myPermittedSubclassesDiff.get();
    }
  }
}
