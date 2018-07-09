// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public class ModuleRepr extends ClassFileRepr{
  private final int myVersion;
  private final Set<ModuleRequiresRepr> myRequires; // module names
  private final Set<ModulePackageRepr> myExports; // package names

  public ModuleRepr(DependencyContext context, int access, int version, int fileName, int name, Set<ModuleRequiresRepr> requires, Set<ModulePackageRepr> exports, Set<UsageRepr.Usage> usages) {
    super(access, context.get(null), name, Collections.emptySet(), fileName, context, usages);
    myVersion = version;
    myRequires = requires;
    myExports = exports;
    updateClassUsages(context, usages);
  }

  public ModuleRepr(DependencyContext context, DataInput in) {
    super(context, in);
    try {
      myVersion = DataInputOutputUtil.readINT(in);
      myRequires = RW.read(ModuleRequiresRepr.externalizer(context), new THashSet<>(), in);
      myExports = RW.read(ModulePackageRepr.externalizer(context), new THashSet<>(), in);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  public int getVersion() {
    return myVersion;
  }

  public Set<ModuleRequiresRepr> getRequires() {
    return myRequires;
  }

  public Set<ModulePackageRepr> getExports() {
    return myExports;
  }

  public void save(DataOutput out) {
    super.save(out);
    try {
      DataInputOutputUtil.writeINT(out, myVersion);
      RW.save(myRequires, out);
      RW.save(myExports, out);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  protected void updateClassUsages(DependencyContext context, Set<UsageRepr.Usage> s) {
    for (ModuleRequiresRepr require : myRequires) {
      if (require.name != name) {
        s.add(UsageRepr.createModuleUsage(context, require.name));
      }
    }
  }

  public void toStream(DependencyContext context, PrintStream stream) {
    super.toStream(context, stream);

    stream.println("      Requires:");
    streamProtoCollection(context, stream, myRequires);
    stream.println("      End Of Requires");

    stream.println("      Exports:");
    streamProtoCollection(context, stream, myExports);
    stream.println("      End Of Exports");
  }

  private static <T extends Proto> void streamProtoCollection(DependencyContext context, PrintStream stream, final Collection<T> collection) {
    final List<T> list = new ArrayList<>(collection);
    list.sort(Comparator.comparingInt(o -> o.name));
    for (T reqRepr : list) {
      reqRepr.toStream(context, stream);
    }
  }

  public boolean requiresTransitevely(int requirementName) {
    for (ModuleRequiresRepr require : myRequires) {
      if (require.name == requirementName) {
        return require.isTransitive();
      }
    }
    return false;
  }

  public abstract static class Diff extends DifferenceImpl {

    Diff(@NotNull Difference delegate) {
      super(delegate);
    }

    public abstract Specifier<ModuleRequiresRepr, ModuleRequiresRepr.Diff> requires();

    public abstract Specifier<ModulePackageRepr, ModulePackageRepr.Diff> exports();

    public abstract boolean versionChanged();

    public boolean no() {
      return base() == NONE && requires().unchanged() && exports().unchanged() && !versionChanged();
    }
  }

  public Diff difference(Proto past) {
    final Difference delegate = super.difference(past);
    final ModuleRepr pastModule = (ModuleRepr)past;
    final int base = !getUsages().equals(pastModule.getUsages())? delegate.base() | Difference.USAGES : delegate.base();

    return new Diff(delegate) {
      public Specifier<ModuleRequiresRepr, ModuleRequiresRepr.Diff> requires() {
        return Difference.make(pastModule.myRequires, myRequires);
      }

      public Specifier<ModulePackageRepr, ModulePackageRepr.Diff> exports() {
        return Difference.make(pastModule.myExports, myExports);
      }

      public boolean versionChanged() {
        return pastModule.getVersion() != myVersion;
      }

      public int base() {
        return base;
      }
    };
  }

  public static DataExternalizer<ModuleRepr> externalizer(final DependencyContext context) {
    return new DataExternalizer<ModuleRepr>() {
      @Override
      public void save(@NotNull final DataOutput out, final ModuleRepr value) throws IOException {
        value.save(out);
      }

      @Override
      public ModuleRepr read(@NotNull final DataInput in) throws IOException {
        return new ModuleRepr(context, in);
      }
    };
  }
}
