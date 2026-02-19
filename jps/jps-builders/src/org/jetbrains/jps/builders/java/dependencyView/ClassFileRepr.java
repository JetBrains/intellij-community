// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

@ApiStatus.Internal
public abstract class ClassFileRepr extends Proto {
  protected final DependencyContext myContext;
  private final int myFileName;
  private final Set<UsageRepr.Usage> myUsages;

  public ClassFileRepr(
    int access,
    int signature,
    int name,
    @NotNull Set<TypeRepr.ClassType> annotations,
    final int fileName, final DependencyContext context, final Set<UsageRepr.Usage> usages) {
    super(access, signature, name, annotations);
    myFileName = fileName;
    this.myContext = context;
    this.myUsages = usages;
  }

  public ClassFileRepr(DependencyContext context, DataInput in) {
    super(context, in);
    myContext = context;
    try {
      myFileName = DataInputOutputUtil.readINT(in);
      myUsages = RW.read(UsageRepr.externalizer(context), new HashSet<>(), in);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  public int getFileName() {
    return myFileName;
  }

  public Set<UsageRepr.Usage> getUsages() {
    return myUsages;
  }

  public boolean addUsage(final UsageRepr.Usage usage) {
    return myUsages.add(usage);
  }

  protected abstract void updateClassUsages(DependencyContext context, Set<? super UsageRepr.Usage> s);

  @Override
  public void toStream(DependencyContext context, PrintStream stream) {
    super.toStream(context, stream);
    stream.print("      Filename   : ");
    stream.println(context.getValue(myFileName));
  }

  @Override
  public void save(final DataOutput out) {
    try {
      super.save(out);
      DataInputOutputUtil.writeINT(out, myFileName);
      RW.save(myUsages, UsageRepr.externalizer(myContext), out);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public int hashCode() {
    return 31 * myFileName + name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ClassFileRepr classRepr = (ClassFileRepr)o;

    if (myFileName != classRepr.myFileName) return false;
    if (name != classRepr.name) return false;

    return true;
  }
}
