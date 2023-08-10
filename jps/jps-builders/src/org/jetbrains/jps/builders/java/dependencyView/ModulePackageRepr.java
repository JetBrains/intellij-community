// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public final class ModulePackageRepr extends Proto {
  private static final DataExternalizer<Integer> INT_EXTERNALIZER = new DataExternalizer<Integer>() {
    @Override
    public void save(@NotNull DataOutput out, Integer value) throws IOException {
      DataInputOutputUtil.writeINT(out, value);
    }

    @Override
    public Integer read(@NotNull DataInput in) throws IOException {
      return DataInputOutputUtil.readINT(in);
    }
  };
  private final Set<Integer> myModuleNames = new HashSet<>();

  ModulePackageRepr(DependencyContext context, int name, Collection<String> modules) {
    super(0, context.get(null), name, Collections.emptySet());
    for (String module : modules) {
      myModuleNames.add(context.get(module));
    }
  }

  private ModulePackageRepr(DependencyContext context, DataInput in) {
    super(context, in);
    RW.read(INT_EXTERNALIZER, myModuleNames, in);
  }

  public Set<Integer> getModuleNames() {
    return Collections.unmodifiableSet(myModuleNames);
  }

  public boolean isQualified() {
    return !myModuleNames.isEmpty();
  }

  @Override
  public void save(DataOutput out) {
    super.save(out);
    RW.save(myModuleNames, INT_EXTERNALIZER, out);
  }

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    return name == ((ModulePackageRepr)o).name;
  }

  public int hashCode() {
    return 31 * name;
  }

  public abstract static class Diff extends DifferenceImpl {

    public abstract Specifier<Integer, Difference> targetModules();

    Diff(@NotNull Difference delegate) {
      super(delegate);
    }
  }

  @Override
  public Diff difference(Proto past) {
    final Difference.Specifier<Integer, Difference> targetModulesDiff = Difference.make(((ModulePackageRepr)past).myModuleNames, myModuleNames);
    return new Diff(super.difference(past)) {
      @Override
      public Specifier<Integer, Difference> targetModules() {
        return targetModulesDiff;
      }

      @Override
      public boolean no() {
        return super.no() && targetModules().unchanged();
      }
    };
  }

  @Override
  public void toStream(DependencyContext context, PrintStream stream) {
    final StringBuilder sb = new StringBuilder();
    sb.append("Module package: ").append(context.getValue(name));
    final Set<Integer> moduleNames = myModuleNames;
    if (!moduleNames.isEmpty()) {
      final List<String> names = new ArrayList<>();
      for (Integer moduleName : moduleNames) {
        names.add(context.getValue(moduleName));
      }
      names.sort(String::compareToIgnoreCase);
      sb.append(" to");
      for (String s : names) {
        sb.append(" ").append(s);
      }
    }
    stream.println(sb);
  }

  public static DataExternalizer<ModulePackageRepr> externalizer(final DependencyContext context) {
    return new DataExternalizer<ModulePackageRepr>() {
      @Override
      public void save(@NotNull DataOutput out, ModulePackageRepr value) {
        value.save(out);
      }

      @Override
      public ModulePackageRepr read(@NotNull DataInput in) {
        return new ModulePackageRepr(context, in);
      }
    };
  }
}
