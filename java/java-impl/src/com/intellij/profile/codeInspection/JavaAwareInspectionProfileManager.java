// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.profile.codeInspection;

import com.intellij.codeInsight.daemon.InspectionProfileConvertor;
import com.intellij.codeInsight.daemon.JavaAwareInspectionProfileCoverter;
import com.intellij.codeInspection.ex.ApplicationInspectionProfileManager;

final class JavaAwareInspectionProfileManager extends ApplicationInspectionProfileManager {
  @Override
  public InspectionProfileConvertor getConverter() {
    return new JavaAwareInspectionProfileCoverter(this);
  }
}