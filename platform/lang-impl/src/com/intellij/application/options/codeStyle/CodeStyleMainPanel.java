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

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.application.options.TabbedLanguageCodeStylePanel;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.DetailsComponent;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CodeStyleMainPanel extends JPanel implements LanguageSelectorListener {
  private final CardLayout myLayout = new CardLayout();
  private final JPanel mySettingsPanel = new JPanel(myLayout);

  private final Map<String, NewCodeStyleSettingsPanel> mySettingsPanels = new HashMap<String, NewCodeStyleSettingsPanel>();

  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  private final CodeStyleSchemesModel myModel;
  private final CodeStyleSettingsPanelFactory myFactory;
  private final CodeStyleSchemesPanel mySchemesPanel;
  private final LanguageSelector myLangSelector;
  private boolean myIsDisposed = false;
  private final DetailsComponent myDetailsComponent;

  @NonNls
  private static final String WAIT_CARD = "CodeStyleSchemesConfigurable.$$$.Wait.placeholder.$$$";


  public CodeStyleMainPanel(CodeStyleSchemesModel model, LanguageSelector langSelector, CodeStyleSettingsPanelFactory factory) {
    super(new BorderLayout());
    myModel = model;
    myFactory = factory;
    mySchemesPanel = new CodeStyleSchemesPanel(model);
    myLangSelector = langSelector;

    model.addListener(new CodeStyleSettingsListener(){
      @Override
      public void currentSchemeChanged(final Object source) {
        if (source != mySchemesPanel) {
          mySchemesPanel.onSelectedSchemeChanged();
        }
        onCurrentSchemeChanged();
      }

      @Override
      public void schemeListChanged() {
        mySchemesPanel.resetSchemesCombo();
      }

      @Override
      public void currentSettingsChanged() {
        ensureCurrentPanel().onSomethingChanged();
      }

      @Override
      public void usePerProjectSettingsOptionChanged() {
        mySchemesPanel.usePerProjectSettingsOptionChanged();
      }

      @Override
      public void schemeChanged(final CodeStyleScheme scheme) {
        ensurePanel(scheme).reset();
      }
    });

    myLangSelector.addListener(this);

    addWaitCard();

    add(mySchemesPanel.getPanel(), BorderLayout.NORTH);

    myDetailsComponent = new DetailsComponent();
    myDetailsComponent.setPaintBorder(false);
    myDetailsComponent.setContent(mySettingsPanel);
    myDetailsComponent.setText(getDisplayName());
    myDetailsComponent.setBannerMinHeight(24);

    add(myDetailsComponent.getComponent(), BorderLayout.CENTER);

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
      @Override
      public void run() {
        if (!myIsDisposed) {
          ensureCurrentPanel().onSomethingChanged();
          String schemeName = myModel.getSelectedScheme().getName();
          myDetailsComponent.setText(schemeName);
          updateSetFrom();
          myLayout.show(mySettingsPanel, schemeName);
        }
      }
    };
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      replaceLayout.run();
    } else {
      myAlarm.cancelAllRequests();
      final Runnable request = new Runnable() {
        @Override
        public void run() {
          SwingUtilities.invokeLater(replaceLayout);
        }
      };
      myAlarm.addRequest(request, 200);
    }
  }

  private void updateSetFrom() {
    final CodeStyleAbstractPanel selectedPanel = ensureCurrentPanel().getSelectedPanel();
    if (selectedPanel instanceof TabbedLanguageCodeStylePanel) {
      myDetailsComponent.setBannerActions(new Action[]{new AbstractAction("Set from...") {
        @Override
        public void actionPerformed(ActionEvent e) {
          final CodeStyleAbstractPanel selectedPanel = ensureCurrentPanel().getSelectedPanel();
          if (selectedPanel instanceof TabbedLanguageCodeStylePanel) {
            ((TabbedLanguageCodeStylePanel)selectedPanel).showSetFrom(e.getSource());
          }
        }
      }});
    }
  }

  public NewCodeStyleSettingsPanel[] getPanels() {
    final Collection<NewCodeStyleSettingsPanel> panels = mySettingsPanels.values();
    return panels.toArray(new NewCodeStyleSettingsPanel[panels.size()]);
  }

  public boolean isModified() {
    final NewCodeStyleSettingsPanel[] panels = getPanels();
    for (NewCodeStyleSettingsPanel panel : panels) {
      //if (!panel.isMultiLanguage()) mySchemesPanel.setPredefinedEnabled(false);
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

  public void apply() throws ConfigurationException {
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
      panel.setLanguageSelector(myLangSelector);
      panel.reset();
      panel.setModel(myModel);
      mySettingsPanels.put(name, panel);
      mySettingsPanel.add(scheme.getName(), panel);
      mySchemesPanel.setCodeStyleSettingsPanel(panel);
      panel.setLanguage(myLangSelector.getLanguage());
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
    myIsDisposed = true;
  }

  public boolean isModified(final CodeStyleScheme scheme) {
    if (!mySettingsPanels.containsKey(scheme.getName())) {
      return false;
    }

    return mySettingsPanels.get(scheme.getName()).isModified();
  }

  @Override
  public void languageChanged(Language lang) {
    for (NewCodeStyleSettingsPanel panel : mySettingsPanels.values()) {
      panel.setLanguage(lang);
    }
  }
  
  public Set<String> processListOptions() {
    final CodeStyleScheme defaultScheme = CodeStyleSchemes.getInstance().getDefaultScheme();
    final NewCodeStyleSettingsPanel panel = ensurePanel(defaultScheme);
    return panel.processListOptions();
  }
}
