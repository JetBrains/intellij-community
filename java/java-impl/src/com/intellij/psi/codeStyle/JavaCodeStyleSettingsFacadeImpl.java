/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle;

import com.intellij.ide.highlighter.JavaFileType;

/**
 * @author yole
 */
public class JavaCodeStyleSettingsFacadeImpl extends JavaCodeStyleSettingsFacade {
  private final CodeStyleSettingsManager myManager;

  public JavaCodeStyleSettingsFacadeImpl(ProjectCodeStyleSettingsManager manager) {
    myManager = manager;
  }

  @Override
  public int getNamesCountToUseImportOnDemand() {
    return myManager.getCurrentSettings().getCustomSettings(JavaCodeStyleSettings.class).NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND;
  }

  @Override
  public boolean useFQClassNames() {
    return myManager.getCurrentSettings().getCustomSettings(JavaCodeStyleSettings.class).USE_FQ_CLASS_NAMES;
  }

  @Override
  public boolean isJavaDocLeadingAsterisksEnabled() {
    return myManager.getCurrentSettings().getCustomSettings(JavaCodeStyleSettings.class).JD_LEADING_ASTERISKS_ARE_ENABLED;
  }

  @Override
  public int getIndentSize() {
    return myManager.getCurrentSettings().getIndentSize(JavaFileType.INSTANCE);
  }

  @Override
  public boolean isGenerateFinalParameters() {
    return myManager.getCurrentSettings().getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_PARAMETERS;
  }

  @Override
  public boolean isGenerateFinalLocals() {
    return myManager.getCurrentSettings().getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_LOCALS;
  }
}
