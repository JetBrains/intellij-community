// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.GraphDataInput;
import org.jetbrains.jps.dependency.GraphDataOutput;
import org.jetbrains.jps.dependency.Usage;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.dependency.impl.RW;

import java.io.IOException;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Supplier;

public final class JvmModule extends JVMClassNode<JvmModule, JvmModule.Diff>{

  private final String myVersion;
  private final Iterable<ModuleRequires> myRequires;
  private final Iterable<ModulePackage> myExports;

  public JvmModule(JVMFlags flags, String name, String outFilePath, String version, @NotNull Iterable<ModuleRequires> requires, @NotNull Iterable<ModulePackage> exports, @NotNull Iterable<Usage> usages, @NotNull Iterable<JvmMetadata<?, ?>> metadata) {
    super(flags, "", name, outFilePath, Collections.emptyList(), usages, metadata);
    myVersion = version == null? "" : version;
    myRequires = requires;
    myExports = exports;
  }

  public JvmModule(GraphDataInput in) throws IOException {
    super(in);
    myVersion = in.readUTF();
    myRequires = RW.readCollection(in, () -> new ModuleRequires(in));
    myExports = RW.readCollection(in, () -> new ModulePackage(in));
  }

  @Override
  public void write(GraphDataOutput out) throws IOException {
    super.write(out);
    out.writeUTF(myVersion);
    RW.writeCollection(out, myRequires, r -> r.write(out));
    RW.writeCollection(out, myExports, p -> p.write(out));
  }

//@Override
  //public Iterable<Usage> getUsages() {
  //  return Iterators.unique(Iterators.flat(
  //    super.getUsages(),
  //    Iterators.map(Iterators.filter(getRequires(), r -> !Objects.equals(getName(), r.getName())), r -> new ModuleUsage(r.getName()))
  //  ));
  //}

  public String getVersion() {
    return myVersion;
  }

  public Iterable<ModuleRequires> getRequires() {
    return myRequires;
  }

  public Iterable<ModulePackage> getExports() {
    return myExports;
  }

  public boolean requiresTransitively(String requirementName) {
    for (ModuleRequires require : getRequires()) {
      if (Objects.equals(require.getName(), requirementName)) {
        return require.getFlags().isTransitive();
      }
    }
    return false;
  }

  @Override
  public Diff difference(JvmModule past) {
    return new Diff(past);
  }

  public final class Diff extends JVMClassNode<JvmModule, JvmModule.Diff>.Diff {
    private final Supplier<Specifier<ModuleRequires, ModuleRequires.Diff>> myRequiresDiff;
    private final Supplier<Specifier<ModulePackage, ModulePackage.Diff>> myExportsDiff;

    public Diff(JvmModule past) {
      super(past);
      myRequiresDiff = Utils.lazyValue(() -> Difference.deepDiff(myPast.getRequires(), getRequires()));
      myExportsDiff = Utils.lazyValue(() -> Difference.deepDiff(myPast.getExports(), getExports()));
    }

    @Override
    public boolean unchanged() {
      return super.unchanged() && !versionChanged() && requires().unchanged() && exports().unchanged();
    }

    public Specifier<ModuleRequires, ModuleRequires.Diff> requires() {
      return myRequiresDiff.get();
    }

    public Specifier<ModulePackage, ModulePackage.Diff> exports() {
      return myExportsDiff.get();
    }

    public boolean versionChanged() {
      return !Objects.equals(myPast.getVersion(), getVersion());
    }
  }

}
