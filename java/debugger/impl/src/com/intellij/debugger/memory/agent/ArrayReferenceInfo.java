// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.ReferringObject;
import com.intellij.debugger.engine.SimpleReferringObject;
import org.jetbrains.annotations.NotNull;
import com.sun.jdi.ObjectReference;

public class ArrayReferenceInfo extends MemoryAgentReferenceInfo {
  private int index;

  public ArrayReferenceInfo(@NotNull ObjectReference referrer,
                            int index) {
    super(referrer);
    this.index = index;
  }

  @NotNull
  @Override
  public ReferringObject createReferringObject() {
    return new SimpleReferringObject(referrer) {
      @Override
      public String getNodeName(int order) {
        return String.format("[%d]", index);
      }
    };
  }
}
