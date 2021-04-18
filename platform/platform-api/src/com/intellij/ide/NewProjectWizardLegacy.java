// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.application.Experiments;

public final class NewProjectWizardLegacy {
  public static boolean isAvailable() {
    return !Experiments.getInstance().isFeatureEnabled("new.project.wizard");
  }
}