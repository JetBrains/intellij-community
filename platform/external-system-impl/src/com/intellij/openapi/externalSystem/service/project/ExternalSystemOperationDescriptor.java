// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project;

import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;

import java.io.Serializable;

public final class ExternalSystemOperationDescriptor implements Serializable {

  public static final Key<ExternalSystemOperationDescriptor> OPERATION_DESCRIPTOR_KEY =
    Key.create(ExternalSystemOperationDescriptor.class, ExternalSystemConstants.UNORDERED + 1);

  private final long myActivityId;

  public ExternalSystemOperationDescriptor() {
    this(0);
  }

  public ExternalSystemOperationDescriptor(long id) {
    myActivityId = id;
  }

  public long getActivityId() {
    return myActivityId;
  }
}
