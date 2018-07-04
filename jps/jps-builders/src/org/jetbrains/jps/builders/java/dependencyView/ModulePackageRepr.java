// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public class ModulePackageRepr extends Proto {
  private static final DataExternalizer<Integer> INT_EXTERNALIZER = new DataExternalizer<Integer>() {
    public void save(@NotNull DataOutput out, Integer value) throws IOException {
      DataInputOutputUtil.writeINT(out, value);
    }

    public Integer read(@NotNull DataInput in) throws IOException {
      return DataInputOutputUtil.readINT(in);
    }
  };
  private final Set<Integer> myModuleNames = new THashSet<>();

  protected ModulePackageRepr(DependencyContext context, int name, Collection<String> modules) {
    super(0, context.get(null), name, Collections.emptySet());
    for (String module : modules) {
      myModuleNames.add(context.get(module));
    }
  }

  protected ModulePackageRepr(DependencyContext context, DataInput in) {
    super(context, in);
    RW.read(INT_EXTERNALIZER, myModuleNames, in);
  }

  public Set<Integer> getModuleNames() {
    return Collections.unmodifiableSet(myModuleNames);
  }

  public boolean isQualified() {
    return !myModuleNames.isEmpty();
  }

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

  public Diff difference(Proto past) {
    final Difference.Specifier<Integer, Difference> targetModulesDiff = Difference.make(((ModulePackageRepr)past).myModuleNames, myModuleNames);
    return new Diff(super.difference(past)) {
      public Specifier<Integer, Difference> targetModules() {
        return targetModulesDiff;
      }

      public boolean no() {
        return super.no() && targetModules().unchanged();
      }
    };
  }

  public void toStream(DependencyContext context, PrintStream stream) {
    final StringBuilder sb = new StringBuilder();
    sb.append("Module package: ").append(context.getValue(name));
    final Set<Integer> moduleNames = myModuleNames;
    if (moduleNames != null && !moduleNames.isEmpty()) {
      final List<String> names = new ArrayList<>();
      for (Integer moduleName : moduleNames) {
        names.add(context.getValue(moduleName));
      }
      Collections.sort(names, String::compareToIgnoreCase);
      sb.append(" to");
      for (String s : names) {
        sb.append(" ").append(s);
      }
    }
    stream.println(sb.toString());
  }

  public static DataExternalizer<ModulePackageRepr> externalizer(final DependencyContext context) {
    return new DataExternalizer<ModulePackageRepr>() {
      public void save(@NotNull DataOutput out, ModulePackageRepr value) {
        value.save(out);
      }

      public ModulePackageRepr read(@NotNull DataInput in) {
        return new ModulePackageRepr(context, in);
      }
    };
  }
}
