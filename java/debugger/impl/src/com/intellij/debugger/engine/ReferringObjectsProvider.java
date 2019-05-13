// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface ReferringObjectsProvider {
  @NotNull
  List<ObjectReference> getReferringObjects(@NotNull ObjectReference value, long limit);

  ReferringObjectsProvider BASIC_JDI = new ReferringObjectsProvider() {
    @NotNull
    @Override
    public List<ObjectReference> getReferringObjects(@NotNull ObjectReference value, long limit) {
      return value.referringObjects(limit);
    }
  };
}
