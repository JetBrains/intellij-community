// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.impl.StringReferenceID;

public class ClassUsage extends JvmElementUsage {

  public ClassUsage(@NotNull String className) {
    super(new StringReferenceID(className));
  }

  public String getClassName() {
    return ((StringReferenceID)getElementOwner()).getValue();
  }

}
