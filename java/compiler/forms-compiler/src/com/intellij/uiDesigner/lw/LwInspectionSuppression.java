// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.lw;

public final class LwInspectionSuppression {
  public static final LwInspectionSuppression[] EMPTY_ARRAY = new LwInspectionSuppression[0];

  private final String myInspectionId;
  private final String myComponentId;

  public LwInspectionSuppression(final String inspectionId, final String componentId) {
    myInspectionId = inspectionId;
    myComponentId = componentId;
  }

  public String getInspectionId() {
    return myInspectionId;
  }

  public String getComponentId() {
    return myComponentId;
  }
}
