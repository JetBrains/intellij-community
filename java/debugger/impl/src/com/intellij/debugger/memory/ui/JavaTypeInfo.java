// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.ui;

import com.intellij.xdebugger.memory.ui.ReferenceInfo;
import com.intellij.xdebugger.memory.ui.TypeInfo;
import com.sun.jdi.ReferenceType;

import java.util.List;
import java.util.stream.Collectors;


public class JavaTypeInfo implements TypeInfo {
  private ReferenceType referenceType;

  public JavaTypeInfo(ReferenceType referenceType) {
    this.referenceType = referenceType;
  }

  @Override
  public String name() {
    return getReferenceType().name();
  }

  @Override
  public List<ReferenceInfo> getInstances(int limit) {
    return getReferenceType().instances(limit).stream().map(JavaReferenceInfo::new).collect(Collectors.toList());
  }

  public ReferenceType getReferenceType() {
    return referenceType;
  }

  @Override
  public boolean canGetInstanceInfo() {
    return referenceType.virtualMachine().canGetInstanceInfo();
  }
}
