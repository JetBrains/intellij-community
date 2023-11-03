// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.Usage;
import org.jetbrains.jps.dependency.diff.Difference;

import java.util.Collections;
import java.util.Objects;

public final class JvmModule extends JVMClassNode<JvmModule, JvmModule.Diff>{

  private final String myVersion;
  private final Iterable<ModuleRequires> myRequires;
  private final Iterable<ModulePackage> myExports;

  public JvmModule(JVMFlags flags, String name, String outFilePath, String version, @NotNull Iterable<ModuleRequires> requires, @NotNull Iterable<ModulePackage> exports, @NotNull Iterable<Usage> usages) {
    super(flags, "", name, outFilePath, Collections.emptyList(), usages);
    myVersion = version == null? "" : version;
    myRequires = requires;
    myExports = exports;
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

  public final class Diff extends Proto.Diff<JvmModule> {

    public Diff(JvmModule past) {
      super(past);
    }

    @Override
    public boolean unchanged() {
      return super.unchanged() && !versionChanged() && requires().unchanged() && exports().unchanged();
    }

    public Specifier<ModuleRequires, ModuleRequires.Diff> requires() {
      return Difference.deepDiff(myPast.getRequires(), getRequires());
    }

    public Specifier<ModulePackage, ModulePackage.Diff> exports() {
      return Difference.deepDiff(myPast.getExports(), getExports());
    }

    public boolean versionChanged() {
      return !Objects.equals(myPast.getVersion(), getVersion());
    }
  }

}
