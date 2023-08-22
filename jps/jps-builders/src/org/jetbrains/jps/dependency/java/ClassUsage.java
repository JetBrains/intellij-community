// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.ReferenceID;
import org.jetbrains.jps.dependency.Usage;

public class ClassUsage implements Usage {

  @NotNull
  private final ReferenceID myClassNode;

  public ClassUsage(@NotNull ReferenceID classNode) {
    myClassNode = classNode;
  }

  @Override
  public @NotNull ReferenceID getElementOwner() {
    return myClassNode;
  }

}
