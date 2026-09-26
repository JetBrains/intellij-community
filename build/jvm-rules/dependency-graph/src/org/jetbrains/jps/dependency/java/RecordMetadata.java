// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.jps.dependency.GraphDataInput;
import org.jetbrains.jps.dependency.GraphDataOutput;
import org.jetbrains.jps.dependency.diff.DiffCapable;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.dependency.impl.RW;
import org.jetbrains.jps.util.Iterators;

import java.io.IOException;

/**
 * Persisted image of a record class file's Record attribute (JVMS 4.7.30): the component names in their declaration order.
 */
public final class RecordMetadata implements JvmMetadata<RecordMetadata, RecordMetadata.Diff> {

  private final Iterable<String> myComponents;

  public RecordMetadata(Iterable<String> components) {
    myComponents = components;
  }

  public RecordMetadata(GraphDataInput in) throws IOException {
    myComponents = RW.readCollection(in, () -> in.readUTF());
  }

  @Override
  public void write(GraphDataOutput out) throws IOException {
    RW.writeCollection(out, myComponents, s -> out.writeUTF(s));
  }

  /**
   * @return record component names in their declaration (Record attribute) order
   */
  public Iterable<String> getComponents() {
    return myComponents;
  }

  @Override
  public boolean isSame(DiffCapable<?, ?> other) {
    return other instanceof RecordMetadata;
  }

  @Override
  public int diffHashCode() {
    return RecordMetadata.class.hashCode();
  }

  @Override
  public Diff difference(RecordMetadata past) {
    return new Diff(past);
  }

  public final class Diff implements Difference {
    private final RecordMetadata myPast;

    public Diff(RecordMetadata past) {
      myPast = past;
    }

    @Override
    public boolean unchanged() {
      return !componentsChanged();
    }

    public boolean componentsChanged() {
      return !Iterators.equals(myPast.getComponents(), getComponents());
    }
  }
}
