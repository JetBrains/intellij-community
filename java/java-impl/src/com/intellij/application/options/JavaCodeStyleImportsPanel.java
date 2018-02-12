/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.application.options;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.ui.OptionGroup;
import org.jetbrains.annotations.NotNull;

class JavaCodeStyleImportsPanel extends CodeStyleImportsPanelBase {
  private FullyQualifiedNamesInJavadocOptionProvider myFqnInJavadocOption;
  
  @Override
  protected void fillCustomOptions(OptionGroup group) {
    myFqnInJavadocOption = new FullyQualifiedNamesInJavadocOptionProvider();
    group.add(myFqnInJavadocOption.getPanel());
  }

  @Override
  public void apply(CodeStyleSettings settings) {
    applyLayoutSettings(getJavaSettings(settings));
    myFqnInJavadocOption.apply(settings);
  }

  @Override
  public void reset(CodeStyleSettings settings) {
    resetLayoutSettings(getJavaSettings(settings));
    myFqnInJavadocOption.reset(settings);
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    return isModifiedLayoutSettings(getJavaSettings(settings)) || myFqnInJavadocOption.isModified(settings);
  }

  private static JavaCodeStyleSettings getJavaSettings(@NotNull CodeStyleSettings settings) {
    return settings.getCustomSettings(JavaCodeStyleSettings.class);
  }
  
}