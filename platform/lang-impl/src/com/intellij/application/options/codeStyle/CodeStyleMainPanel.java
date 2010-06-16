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

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.DetailsComponent;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class CodeStyleMainPanel extends JPanel implements LanguageSelectorListener {
  private final CardLayout myLayout = new CardLayout();
  private final JPanel mySettingsPanel = new JPanel(myLayout);

  private final Map<String, NewCodeStyleSettingsPanel> mySettingsPanels = new HashMap<String, NewCodeStyleSettingsPanel>();

  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  private final CodeStyleSchemesModel myModel;
  private final CodeStyleSettingsPanelFactory myFactory;
  private final CodeStyleSchemesPanel mySchemesPanel;
  private final LanguageSelector myLangSelector;

  @NonNls
  private static final String WAIT_CARD = "CodeStyleSchemesConfigurable.$$$.Wait.placeholder.$$$";


  public CodeStyleMainPanel(CodeStyleSchemesModel model, LanguageSelector langSelector, CodeStyleSettingsPanelFactory factory) {
    super(new BorderLayout());
    myModel = model;
    myFactory = factory;
    mySchemesPanel = new CodeStyleSchemesPanel(model);
    myLangSelector = langSelector;

    model.addListener(new CodeStyleSettingsListener(){
      public void currentSchemeChanged(final Object source) {
        if (source != mySchemesPanel) {
          mySchemesPanel.onSelectedSchemeChanged();
        }
        onCurrentSchemeChanged();
      }

      public void schemeListChanged() {
        mySchemesPanel.resetSchemesCombo();
      }

      public void currentSettingsChanged() {
        ensureCurrentPanel().onSomethingChanged();
      }

      public void usePerProjectSettingsOptionChanged() {
        mySchemesPanel.usePerProjectSettingsOptionChanged();
      }

      public void schemeChanged(final CodeStyleScheme scheme) {
        ensurePanel(scheme).resetFromClone();
      }
    });

    myLangSelector.addListener(this);

    addWaitCard();

    add(mySchemesPanel.getPanel(), BorderLayout.NORTH);

    DetailsComponent detComp = new DetailsComponent();
    detComp.setPaintBorder(false);
    detComp.setContent(mySettingsPanel);
    detComp.setText(getDisplayName());

    add(detComp.getComponent(), BorderLayout.CENTER);

    mySchemesPanel.resetSchemesCombo();
    mySchemesPanel.onSelectedSchemeChanged();
    onCurrentSchemeChanged();

  }

  private void addWaitCard() {
    JPanel waitPanel = new JPanel(new BorderLayout());
    JLabel label = new JLabel(ApplicationBundle.message("label.loading.page.please.wait"));
    label.setHorizontalAlignment(SwingConstants.CENTER);
    waitPanel.add(label, BorderLayout.CENTER);
    label.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    waitPanel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    mySettingsPanel.add(WAIT_CARD, waitPanel);
  }

  public void onCurrentSchemeChanged() {
    myLayout.show(mySettingsPanel, WAIT_CARD);
    final Runnable replaceLayout = new Runnable() {
      public void run() {
        ensureCurrentPanel().onSomethingChanged();
        myLayout.show(mySettingsPanel, myModel.getSelectedScheme().getName());
      }
    };
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      replaceLayout.run();
    } else {
      myAlarm.cancelAllRequests();
      final Runnable request = new Runnable() {
        public void run() {
          SwingUtilities.invokeLater(replaceLayout);
        }
      };
      myAlarm.addRequest(request, 200);
    }
  }

  public NewCodeStyleSettingsPanel[] getPanels() {
    final Collection<NewCodeStyleSettingsPanel> panels = mySettingsPanels.values();
    return panels.toArray(new NewCodeStyleSettingsPanel[panels.size()]);
  }

  public boolean isModified() {
    final NewCodeStyleSettingsPanel[] panels = getPanels();
    for (NewCodeStyleSettingsPanel panel : panels) {
      if (panel.isModified()) return true;
    }
    return false;
  }

  public void reset() {
    for (NewCodeStyleSettingsPanel panel : mySettingsPanels.values()) {
      panel.reset();
    }

    onCurrentSchemeChanged();
  }

  private void clearPanels() {
    for (NewCodeStyleSettingsPanel panel : mySettingsPanels.values()) {
      panel.dispose();
    }
    mySettingsPanels.clear();
  }

  public void apply() {
    final NewCodeStyleSettingsPanel[] panels = getPanels();
    for (NewCodeStyleSettingsPanel panel : panels) {
      if (panel.isModified()) panel.apply();
    }
  }

  @NonNls
  public String getHelpTopic() {
    NewCodeStyleSettingsPanel selectedPanel = ensureCurrentPanel();
    if (selectedPanel == null) {
      return "reference.settingsdialog.IDE.globalcodestyle";
    }
    String helpTopic = selectedPanel.getHelpTopic();
    if (helpTopic != null) {
      return helpTopic;
    }
    return "";
  }

  private NewCodeStyleSettingsPanel ensureCurrentPanel() {
    return ensurePanel(myModel.getSelectedScheme());
  }

  private NewCodeStyleSettingsPanel ensurePanel(final CodeStyleScheme scheme) {
    String name = scheme.getName();
    if (!mySettingsPanels.containsKey(name)) {
      NewCodeStyleSettingsPanel panel = myFactory.createPanel(scheme);
      panel.reset();
      panel.setModel(myModel);
      panel.setLanguageSelector(myLangSelector);
      mySettingsPanels.put(name, panel);
      mySettingsPanel.add(scheme.getName(), panel);
      mySchemesPanel.setCodeStyleSettingsPanel(panel);
    }

    return mySettingsPanels.get(name);
  }

  public String getDisplayName() {
    return myModel.getSelectedScheme().getName();
  }

  public void disposeUIResources() {
    myAlarm.cancelAllRequests();
    clearPanels();
    myLangSelector.removeListener(this);
  }

  public boolean isModified(final CodeStyleScheme scheme) {
    if (!mySettingsPanels.containsKey(scheme.getName())) {
      return false;
    }

    return mySettingsPanels.get(scheme.getName()).isModified();
  }

  public void languageChanged(Language lang) {
    for (NewCodeStyleSettingsPanel panel : mySettingsPanels.values()) {
      panel.setLanguage(lang);
    }
  }
}
