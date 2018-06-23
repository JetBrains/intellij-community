// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.backwardRefs;

import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.DifferentSerializableBytesImplyNonEqualityPolicy;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.jetbrains.jps.backwardRefs.CompilerRef.*;

public final class CompilerRefDescriptor implements KeyDescriptor<CompilerRef>, DifferentSerializableBytesImplyNonEqualityPolicy {
  public static final CompilerRefDescriptor INSTANCE = new CompilerRefDescriptor();

  @Override
  public int getHashCode(CompilerRef value) {
    return value.hashCode();
  }

  @Override
  public boolean isEqual(CompilerRef val1, CompilerRef val2) {
    return val1.equals(val2);
  }

  @Override
  public void save(@NotNull DataOutput out, CompilerRef value) throws IOException {
    value.save(out);
  }

  @Override
  public CompilerRef read(@NotNull DataInput in) throws IOException {
    final byte type = in.readByte();
    switch (type) {
      case CLASS_MARKER:
        return new JavaCompilerClassRef(DataInputOutputUtil.readINT(in));
      case ANONYMOUS_CLASS_MARKER:
        return new JavaCompilerAnonymousClassRef(DataInputOutputUtil.readINT(in));
      case METHOD_MARKER:
        return new JavaCompilerMethodRef(DataInputOutputUtil.readINT(in), DataInputOutputUtil.readINT(in), DataInputOutputUtil.readINT(in));
      case FIELD_MARKER:
        return new JavaCompilerFieldRef(DataInputOutputUtil.readINT(in), DataInputOutputUtil.readINT(in));
      case FUN_EXPR_MARKER:
        return new JavaCompilerFunExprDef(DataInputOutputUtil.readINT(in));
    }
    throw new AssertionError();
  }
}
