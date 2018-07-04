// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;

/**
 * @author Eugene Zhuravlev
 */
public class ModuleRequiresRepr extends Proto {

  private final int myVersion;

  public ModuleRequiresRepr(DependencyContext context, int access, int name, String version) {
    super(access, context.get(null), name, Collections.emptySet());
    myVersion = context.get(version);
  }

  public ModuleRequiresRepr(DependencyContext context, DataInput in) {
    super(context, in);
    try {
      myVersion = DataInputOutputUtil.readINT(in);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  public boolean isTransitive() {
    return (access & Opcodes.ACC_TRANSITIVE) != 0;
  }

  public int getVersion() {
    return myVersion;
  }

  @Override
  public void save(final DataOutput out) {
    try {
      super.save(out);
      DataInputOutputUtil.writeINT(out, myVersion);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  public static DataExternalizer<ModuleRequiresRepr> externalizer(DependencyContext context) {
    return new DataExternalizer<ModuleRequiresRepr>() {
      public void save(@NotNull DataOutput out, ModuleRequiresRepr value) throws IOException {
        value.save(out);
      }

      public ModuleRequiresRepr read(@NotNull DataInput in) throws IOException {
        return new ModuleRequiresRepr(context, in);
      }
    };
  }

  public abstract static class Diff extends DifferenceImpl {

    Diff(@NotNull Difference delegate) {
      super(delegate);
    }

    public abstract boolean versionChanged();

    public abstract boolean becameNonTransitive();
  }
  
  public Diff difference(Proto past) {
    final ModuleRequiresRepr pastRequirement = (ModuleRequiresRepr)past;
    return new Diff(super.difference(past)) {
      public boolean versionChanged() {
        return pastRequirement.myVersion != myVersion;
      }

      public boolean no() {
        return super.no() && !versionChanged();
      }

      public boolean becameNonTransitive() {
        return pastRequirement.isTransitive() && !ModuleRequiresRepr.this.isTransitive();
      }
    };
  }

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    return name == ((ModuleRequiresRepr)o).name;
  }

  public int hashCode() {
    return 31 * name;
  }

  @Override
  public void toStream(final DependencyContext context, final PrintStream stream) {
    stream.println("Requires module: " + context.getValue(name) + ":" + access + ":" + myVersion);
  }
}
