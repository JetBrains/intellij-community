// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.ui;

import com.intellij.xdebugger.memory.ui.ReferenceInfo;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

public class JavaReferenceInfo implements ReferenceInfo {
  private final ObjectReference objectReference;

  public JavaReferenceInfo(@NotNull ObjectReference objectReference) {
    this.objectReference = objectReference;
  }

  @NotNull
  public ObjectReference getObjectReference() {
    return objectReference;
  }

  @Override
  public int hashCode() {
    return objectReference.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof JavaReferenceInfo)) {
      return false;
    }
    return ((JavaReferenceInfo)obj).objectReference.equals(objectReference);
  }
}
