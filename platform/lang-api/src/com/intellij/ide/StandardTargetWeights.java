/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide;

/**
 * @author max
 */
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
