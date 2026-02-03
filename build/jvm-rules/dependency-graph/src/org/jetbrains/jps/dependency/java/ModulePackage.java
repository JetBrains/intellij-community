// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.jps.dependency.GraphDataInput;
import org.jetbrains.jps.dependency.GraphDataOutput;
import org.jetbrains.jps.dependency.diff.DiffCapable;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.dependency.impl.RW;
import org.jetbrains.jps.util.Iterators;

import java.io.IOException;
import java.util.Collections;

public final class ModulePackage extends Proto implements DiffCapable<ModulePackage, ModulePackage.Diff> {

  private final Iterable<String> myModules;

  public ModulePackage(String name, Iterable<String> modules) {
    super(JVMFlags.EMPTY, "", name, Collections.emptyList());
    myModules = modules;
  }

  public ModulePackage(GraphDataInput in) throws IOException {
    super(in);
    myModules = RW.readCollection(in, () -> in.readUTF());
  }

  @Override
  public void write(GraphDataOutput out) throws IOException {
    super.write(out);
    RW.writeCollection(out, myModules, m -> out.writeUTF(m));
  }

  public boolean isQualified() {
    return !Iterators.isEmpty(myModules);
  }

  public Iterable<String> getModules() {
    return myModules;
  }

  @Override
  public boolean isSame(DiffCapable<?, ?> other) {
    return other instanceof ModulePackage && getName().equals(((ModulePackage)other).getName());
  }

  @Override
  public int diffHashCode() {
    return getName().hashCode();
  }

  @Override
  public Diff difference(ModulePackage past) {
    return new Diff(past);
  }

  public final class Diff extends Proto.Diff<ModulePackage> {

    public Diff(ModulePackage past) {
      super(past);
    }

    @Override
    public boolean unchanged() {
      return super.unchanged() && targetModules().unchanged();
    }

    public Specifier<String, ?> targetModules() {
      return Difference.diff(myPast.getModules(), getModules());
    }
  }

}
