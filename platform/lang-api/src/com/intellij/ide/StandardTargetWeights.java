// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

public interface StandardTargetWeights {
  float PROJECT_WEIGHT = 1;
  float FAVORITES_WEIGHT = 1.01f;
  float PACKAGES_WEIGHT = 2;
  float J2EE_WEIGHT = 3;
  float STRUCTURE_WEIGHT = 4;
  float COMMANDER_WEIGHT = 5;
  float SCOPE_WEIGHT = 6.5f;
  float NAV_BAR_WEIGHT = 8;
  float CHANGES_VIEW = 9;
  float OS_FILE_MANAGER = 9.5f;
  float PROJECT_SETTINGS_WEIGHT = 10;
}
