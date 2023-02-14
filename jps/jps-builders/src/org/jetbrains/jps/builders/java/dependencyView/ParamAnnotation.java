// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 */
public final class ParamAnnotation implements RW.Savable {
  public static final ParamAnnotation[] EMPTY_ARRAY = new ParamAnnotation[0];

  public final int paramIndex;
  public final @NotNull TypeRepr.ClassType type;

  public ParamAnnotation(int paramIndex, @NotNull TypeRepr.ClassType type) {
    this.paramIndex = paramIndex;
    this.type = type;
  }

  public ParamAnnotation(DataExternalizer<TypeRepr.ClassType> externalizer, DataInput in) {
    try {
      paramIndex = DataInputOutputUtil.readINT(in);
      type = externalizer.read(in);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void save(DataOutput out) {
    try {
      DataInputOutputUtil.writeINT(out, paramIndex);
      type.save(out);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ParamAnnotation that = (ParamAnnotation)o;

    if (paramIndex != that.paramIndex) return false;
    if (!type.equals(that.type)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = paramIndex;
    result = 31 * result + type.hashCode();
    return result;
  }
}
