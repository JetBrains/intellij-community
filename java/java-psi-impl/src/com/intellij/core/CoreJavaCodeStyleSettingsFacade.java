/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.core;

import com.intellij.psi.codeStyle.JavaCodeStyleSettingsFacade;

/**
 * @author yole
 */
public class CoreJavaCodeStyleSettingsFacade extends JavaCodeStyleSettingsFacade {
  @Override
  public int getNamesCountToUseImportOnDemand() {
    return 0;
  }

  @Override
  public boolean useFQClassNames() {
    return false;
  }

  @Override
  public boolean isJavaDocLeadingAsterisksEnabled() {
    return false;
  }

  @Override
  public int getIndentSize() {
    return 4;
  }

  @Override
  public boolean isGenerateFinalParameters() {
    return false;
  }

  @Override
  public boolean isGenerateFinalLocals() {
    return false;
  }
}
