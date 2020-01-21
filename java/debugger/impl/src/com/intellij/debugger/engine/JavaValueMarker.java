// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.xdebugger.frame.XValueMarkerProvider;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

public class JavaValueMarker extends XValueMarkerProvider<JavaValue, ObjectReference> {
  public JavaValueMarker() {
    super(JavaValue.class);
  }

  @Override
  public boolean canMark(@NotNull JavaValue value) {
    return value.getDescriptor().canMark();
  }

  @Override
  public ObjectReference getMarker(@NotNull JavaValue value) {
    Value obj = value.getDescriptor().getValue();
    if (obj instanceof ObjectReference) {
      return ((ObjectReference)obj);
    }
    return null;
  }
}
