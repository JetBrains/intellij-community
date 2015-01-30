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

package com.intellij.application.options.codeStyle;

import com.intellij.application.options.CodeStyleAbstractConfigurable;
import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.application.options.OptionsContainingConfigurable;
import com.intellij.application.options.TabbedLanguageCodeStylePanel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.Set;

/**
 * @author max
 */
public class NewCodeStyleSettingsPanel extends JPanel implements TabbedLanguageCodeStylePanel.TabChangeListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.options.codeStyle.NewCodeStyleSettingsPanel");

  private final Configurable myTab;

  public NewCodeStyleSettingsPanel(@NotNull Configurable tab) {
    super(new BorderLayout());
    myTab = tab;
    JComponent component = myTab.createComponent();
    add(component, BorderLayout.CENTER);
  }

  public boolean isModified() {
    return myTab.isModified();
  }

  public void updatePreview() {
    if (myTab instanceof CodeStyleAbstractConfigurable) {
      ((CodeStyleAbstractConfigurable)myTab).getPanel().onSomethingChanged();
    }
  }

  public void apply() throws ConfigurationException {
    if (myTab.isModified()) {
      myTab.apply();
    }
  }

  @Nullable
  public String getHelpTopic() {
    return myTab.getHelpTopic();
  }

  public void dispose() {
    myTab.disposeUIResources();
  }

  public void reset() {
    myTab.reset();
    updatePreview();
  }

  public String getDisplayName() {
    return myTab.getDisplayName();
  }

  public void setModel(final CodeStyleSchemesModel model) {
    if (myTab instanceof CodeStyleAbstractConfigurable) {
      ((CodeStyleAbstractConfigurable)myTab).setModel(model);
    }
  }

  public void onSomethingChanged() {
    if (myTab instanceof CodeStyleAbstractConfigurable) {
      ((CodeStyleAbstractConfigurable)myTab).onSomethingChanged();
    }
  }

  public Set<String> processListOptions() {
    if (myTab instanceof OptionsContainingConfigurable) {
      return ((OptionsContainingConfigurable) myTab).processListOptions();
    }
    return Collections.emptySet();
  }


  @Nullable
  public CodeStyleAbstractPanel getSelectedPanel() {
    if (myTab instanceof CodeStyleAbstractConfigurable) {
      return ((CodeStyleAbstractConfigurable)myTab).getPanel();
    }
    return null;
  }

  @Override
  public void tabChanged(@NotNull TabbedLanguageCodeStylePanel source, @NotNull String tabTitle) {
    CodeStyleAbstractPanel panel = getSelectedPanel();
    if (panel instanceof TabbedLanguageCodeStylePanel && panel != source) {
      ((TabbedLanguageCodeStylePanel)panel).changeTab(tabTitle);
    }
  }
}
