// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionProfileEntry;
import org.jetbrains.annotations.ApiStatus;

/**
 * @deprecated use {@link AbstractBaseJavaLocalInspectionTool} instead.
 * Inspections should not override {@link InspectionProfileEntry#isEnabledByDefault()}.
 * This property should be specified in descriptor: {@link com.intellij.codeInspection.InspectionEP#enabledByDefault}
 */
@ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
@Deprecated
public abstract class BaseLocalInspectionTool extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public boolean isEnabledByDefault() {
    return true;
  }
}
