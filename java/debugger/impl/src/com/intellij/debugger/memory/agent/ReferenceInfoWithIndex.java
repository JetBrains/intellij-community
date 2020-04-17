// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.FieldReferringObject;
import com.intellij.debugger.engine.ReferringObject;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

public class ReferenceInfoWithIndex extends MemoryAgentReferenceInfo {
  private int fieldIndex;

  public ReferenceInfoWithIndex(@NotNull ObjectReference from,
                                @NotNull ReferenceKind kind,
                                int fieldIndex) {
    super(from, kind);
    this.fieldIndex = fieldIndex;
  }

  @Override
  public ReferringObject createReferringObject() {
    return new FieldReferringObject(referrer, referrer.referenceType().allFields().get(fieldIndex));
  }
}
