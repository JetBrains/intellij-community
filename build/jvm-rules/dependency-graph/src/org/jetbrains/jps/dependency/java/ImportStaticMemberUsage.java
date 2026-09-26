// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.GraphDataInput;

import java.io.IOException;

public final class ImportStaticMemberUsage extends MemberUsage{

  public ImportStaticMemberUsage(String className, String name) {
    super(className, name);
  }

  public ImportStaticMemberUsage(JvmNodeReferenceID clsId, String name) {
    super(clsId, name);
  }

  public ImportStaticMemberUsage(@NotNull JvmNodeReferenceID owner, GraphDataInput in) throws IOException {
    super(owner, in);
  }

  @Override
  public int hashCode() {
    return super.hashCode() + 1;
  }
}
