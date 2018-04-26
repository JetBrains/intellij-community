// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.ui;

import com.intellij.xdebugger.memory.ui.ReferenceInfo;
import com.intellij.xdebugger.memory.ui.TypeInfo;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;


public class JavaTypeInfo implements TypeInfo {
  private final ReferenceType referenceType;

  public static List<TypeInfo> wrap(List<ReferenceType> types) {
    return types.stream().map(JavaTypeInfo::new).collect(Collectors.toList());
  }

  public JavaTypeInfo(@NotNull ReferenceType referenceType) {
    this.referenceType = referenceType;
  }

  @NotNull
  @Override
  public String name() {
    return getReferenceType().name();
  }

  @NotNull
  @Override
  public List<ReferenceInfo> getInstances(int limit) {
    return getReferenceType().instances(limit).stream().map(JavaReferenceInfo::new).collect(Collectors.toList());
  }

  @NotNull
  public ReferenceType getReferenceType() {
    return referenceType;
  }

  @Override
  public boolean canGetInstanceInfo() {
    return referenceType.virtualMachine().canGetInstanceInfo();
  }

  @Override
  public int hashCode() {
    return referenceType.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof JavaTypeInfo)) {
      return false;
    }
    return ((JavaTypeInfo)obj).referenceType.equals(referenceType);
  }
}
