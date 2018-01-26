// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.ui;

import com.intellij.xdebugger.memory.ui.ReferenceInfo;
import com.sun.jdi.ObjectReference;

public class JavaReferenceInfo implements ReferenceInfo {
  private final ObjectReference objectReference;

  public JavaReferenceInfo(ObjectReference objectReference) {
    this.objectReference = objectReference;
  }

  public ObjectReference getObjectReference() {
    return objectReference;
  }
}
