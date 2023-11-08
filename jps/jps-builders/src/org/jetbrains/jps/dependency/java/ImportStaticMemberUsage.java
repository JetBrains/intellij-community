// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

public final class ImportStaticMemberUsage extends MemberUsage{

  public ImportStaticMemberUsage(String className, String name) {
    super(className, name);
  }

  public ImportStaticMemberUsage(JvmNodeReferenceID clsId, String name) {
    super(clsId, name);
  }

  @Override
  public int hashCode() {
    return super.hashCode() + 1;
  }
}
