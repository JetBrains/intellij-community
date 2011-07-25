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
package com.intellij.application.options;

import com.intellij.application.options.codeStyle.CodeStyleBlankLinesPanel;
import com.intellij.application.options.codeStyle.CodeStyleSpacesPanel;
import com.intellij.application.options.codeStyle.LanguageSelector;
import com.intellij.application.options.codeStyle.WrappingAndBracesPanel;
import com.intellij.lang.Language;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;

/**
 * Used for languages sharing common settings.
 *
 * @author Rustam Vishnyakov
 */
public class CommonCodeStyleSettingsConfigurable extends CodeStyleAbstractConfigurable {
  public CommonCodeStyleSettingsConfigurable(@NotNull CodeStyleSettings settings, CodeStyleSettings cloneSettings) {
    super(settings, cloneSettings, "A");
  }

  @Override
  protected CodeStyleAbstractPanel createPanel(CodeStyleSettings settings) {
    return new MyCodeStylePanel(settings);
  }

  @Override
  public String getHelpTopic() {
    return null;
  }

  private static class MyCodeStylePanel extends MultiTabCodeStyleAbstractPanel {
    private CodeStyleSpacesPanel mySpacesPanel;
    private CodeStyleBlankLinesPanel myBlankLinesPanel;
    private WrappingAndBracesPanel myWrappingAndBracesPanel;

    protected MyCodeStylePanel(CodeStyleSettings settings) {
      super(settings);
    }

    @Override
    protected void initTabs(CodeStyleSettings settings) {
      mySpacesPanel = new CodeStyleSpacesPanel(settings);
      myBlankLinesPanel = new CodeStyleBlankLinesPanel(settings);
      myWrappingAndBracesPanel = new WrappingAndBracesPanel(settings);
      addTab(mySpacesPanel);
      addTab(myBlankLinesPanel);
      addTab(myWrappingAndBracesPanel);
    }

    @Override
    public void setLanguageSelector(LanguageSelector langSelector) {
      mySpacesPanel.setLanguageSelector(langSelector);
      myBlankLinesPanel.setLanguageSelector(langSelector);
      myWrappingAndBracesPanel.setLanguageSelector(langSelector);
    }

    @Override
    public boolean setPanelLanguage(Language language) {
      mySpacesPanel.setPanelLanguage(language);
      myBlankLinesPanel.setPanelLanguage(language);
      myWrappingAndBracesPanel.setPanelLanguage(language);
      return true;
    }
  }
}
