// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.jps.dependency.GraphDataInput;
import org.jetbrains.jps.dependency.GraphDataOutput;
import org.jetbrains.jps.dependency.diff.DiffCapable;

import java.io.IOException;
import java.util.Collections;
import java.util.Objects;

public final class ModuleRequires extends Proto implements DiffCapable<ModuleRequires, ModuleRequires.Diff> {

  private final String myVersion;

  public ModuleRequires(JVMFlags flags, String name, String version) {
    super(flags, "", name, Collections.emptyList());
    myVersion = version == null? "" : version;
  }

  public ModuleRequires(GraphDataInput in) throws IOException {
    super(in);
    myVersion = in.readUTF();
  }

  @Override
  public void write(GraphDataOutput out) throws IOException {
    super.write(out);
    out.writeUTF(myVersion);
  }

  public boolean isTransitive() {
    return getFlags().isTransitive();
  }

  public String getVersion() {
    return myVersion;
  }

  @Override
  public boolean isSame(DiffCapable<?, ?> other) {
    return other instanceof ModuleRequires && getName().equals(((ModuleRequires)other).getName());
  }

  @Override
  public int diffHashCode() {
    return getName().hashCode();
  }

  @Override
  public Diff difference(ModuleRequires past) {
    return new Diff(past);
  }

  public final class Diff extends Proto.Diff<ModuleRequires> {

    public Diff(ModuleRequires past) {
      super(past);
    }

    @Override
    public boolean unchanged() {
      return super.unchanged() && !versionChanged();
    }

    public boolean versionChanged() {
      return !Objects.equals(myPast.getVersion(), getVersion());
    }

    public boolean becameNonTransitive() {
      return myPast.getFlags().isTransitive() && !getFlags().isTransitive();
    }
  }
}
