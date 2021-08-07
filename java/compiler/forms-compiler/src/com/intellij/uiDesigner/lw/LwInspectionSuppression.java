// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.lw;

public class LwInspectionSuppression {
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
