// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery.indices;

import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;

public class TestId {
  private final int myClassId;
  private final int myMethodId;
  private final byte myFrameworkId;

  TestId(int classId, int methodId, byte id) {
    myClassId = classId;
    myMethodId = methodId;
    myFrameworkId = id;
  }

  public int getClassId() {
    return myClassId;
  }

  public int getMethodId() {
    return myMethodId;
  }

  public byte getFrameworkId() {
    return myFrameworkId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TestId id = (TestId)o;
    return myClassId == id.myClassId &&
           myMethodId == id.myMethodId &&
           myFrameworkId == id.myFrameworkId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(myClassId, myMethodId, myFrameworkId);
  }

  static final KeyDescriptor<TestId> DESCRIPTOR = new KeyDescriptor<TestId>() {
    @Override
    public int getHashCode(TestId id) {
      return id.hashCode();
    }

    @Override
    public boolean isEqual(TestId id1, TestId id2) {
      return id1.equals(id2);
    }

    @Override
    public void save(@NotNull DataOutput out, TestId id) throws IOException {
      DataInputOutputUtil.writeINT(out, id.getClassId());
      DataInputOutputUtil.writeINT(out, id.getMethodId());
      out.writeByte(id.getFrameworkId());
    }

    @Override
    public TestId read(@NotNull DataInput in) throws IOException {
      return new TestId(DataInputOutputUtil.readINT(in), DataInputOutputUtil.readINT(in), in.readByte());
    }
  };
}
