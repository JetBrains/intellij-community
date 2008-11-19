package com.intellij.application.options;

import com.intellij.application.options.codeStyle.*;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemeImpl;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class CodeStyleSchemesConfigurable extends SearchableConfigurable.Parent.Abstract {

  private CodeStyleSchemesPanel myRootSchemesPanel;
  private CodeStyleSchemesModel myModel;
  private List<CodeStyleMainPanel> myPanels;
  private boolean myResetCompleted = false;
  private boolean myInitResetInvoked = false;
  private boolean myRevertCompleted = false;

  private boolean myApplyCompleted = false;
  private final Project myProject;

  public CodeStyleSchemesConfigurable(Project project) {
    myProject = project;
  }

  public JComponent createComponent() {
    myModel = ensureModel();

    return myRootSchemesPanel.getPanel();
  }

  @Override
  public boolean hasOwnContent() {
    return true;
  }

  @Override
  public void disposeUIResources() {
    if (myPanels != null) {
      try {
        super.disposeUIResources();
        for (CodeStyleMainPanel panel : myPanels) {
          panel.disposeUIResources();
        }
      }
      finally {
        myPanels = null;
        myModel = null;
        myRootSchemesPanel = null;
        myResetCompleted = false;
        myRevertCompleted = false;
        myApplyCompleted = false;
        myInitResetInvoked = false;
      }
    }
  }

  @Override
  public synchronized void reset() {
    if (!myInitResetInvoked) {
      try {
        if (!myResetCompleted) {
          try {
            resetImpl();
          }
          finally {
            myResetCompleted = true;
          }
        }
      }
      finally {
        myInitResetInvoked = true;
      }
    }
    else {
      revert();
    }

  }

  private void resetImpl() {
    myModel.reset();
    for (CodeStyleMainPanel panel : myPanels) {
      panel.reset();
    }
  }

  public synchronized void resetFromChild() {
    if (!myResetCompleted) {
      try {
        resetImpl();
      }
      finally {
        myResetCompleted = true;
      }
    }
  }

  public void revert() {
    if (myModel.isSchemeListModified() || isSomeSchemeModified()) {
      myRevertCompleted = false;
    }
    if (!myRevertCompleted) {
      try {
        resetImpl();
      }
      finally {
        myRevertCompleted = true;
      }
    }
  }

  private boolean isSomeSchemeModified() {
    for (CodeStyleMainPanel panel : myPanels) {
      if (panel.isModified()) return true;
    }

    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    if (!myApplyCompleted) {
      try {
        super.apply();

        for (CodeStyleScheme scheme : new ArrayList<CodeStyleScheme>(myModel.getSchemes())) {
          final boolean isDefaultModified = CodeStyleSchemesModel.cannotBeModified(scheme) && isSchemeModified(scheme);
          if (isDefaultModified) {
            CodeStyleScheme newscheme = myModel.createNewScheme(null, scheme);
            CodeStyleSettings settingsWillBeModified = scheme.getCodeStyleSettings();
            CodeStyleSettings notModifiedSettings = settingsWillBeModified.clone();
            ((CodeStyleSchemeImpl)scheme).setCodeStyleSettings(notModifiedSettings);
            ((CodeStyleSchemeImpl)newscheme).setCodeStyleSettings(settingsWillBeModified);
            myModel.addScheme(newscheme, false);

            if (myModel.getSelectedScheme() == scheme) {
              myModel.selectScheme(newscheme, this);
            }

          }
        }

        for (CodeStyleMainPanel panel : myPanels) {
          panel.apply();
        }

        myModel.apply();
        EditorFactory.getInstance().refreshAllEditors();
      }
      finally {
        myApplyCompleted = true;
      }

    }

  }

  private boolean isSchemeModified(final CodeStyleScheme scheme) {
    for (CodeStyleMainPanel panel : myPanels) {
      if (panel.isModified(scheme)) {
        return true;
      }
    }
    return false;
  }

  protected Configurable[] buildConfigurables() {
    myPanels = new ArrayList<CodeStyleMainPanel>();

    myPanels.add(new CodeStyleMainPanel(ensureModel(), new CodeStyleSettingsPanelFactory() {
      public NewCodeStyleSettingsPanel createPanel(final CodeStyleScheme scheme) {
        return new NewCodeStyleSettingsPanel(new CodeStyleAbstractConfigurable(scheme.getCodeStyleSettings(), ensureModel().getCloneSettings(scheme),
                                                                            ApplicationBundle.message("title.general")) {
          protected CodeStyleAbstractPanel createPanel(final CodeStyleSettings settings) {
            return new GeneralCodeStylePanel(settings);
          }

          public Icon getIcon() {
            return FileTypes.PLAIN_TEXT.getIcon();
          }

          public String getHelpTopic() {
            return "reference.settingsdialog.IDE.globalcodestyle.general";
          }
        });
      }
    }));

    for (final CodeStyleSettingsProvider provider : Extensions.getExtensions(CodeStyleSettingsProvider.EXTENSION_POINT_NAME)) {
      myPanels.add(new CodeStyleMainPanel(ensureModel(), new CodeStyleSettingsPanelFactory() {
        public NewCodeStyleSettingsPanel createPanel(final CodeStyleScheme scheme) {
          return new NewCodeStyleSettingsPanel(provider.createSettingsPage(scheme.getCodeStyleSettings(), ensureModel().getCloneSettings(scheme)));
        }
      }));
    }

    Configurable[] result = new Configurable[myPanels.size()];
    for (int i = 0; i < result.length; i++) {
      final CodeStyleMainPanel panel = myPanels.get(i);
      result[i] = new SearchableConfigurable(){
        private boolean myInitialResetInvoked = false;
        @Nls
        public String getDisplayName() {
          return panel.getDisplayName();
        }

        public Icon getIcon() {
          return null;
        }

        public String getHelpTopic() {
          return panel.getHelpTopic();
        }

        public JComponent createComponent() {
          return panel;
        }

        public boolean isModified() {
          boolean someSchemeModified = panel.isModified();
          if (someSchemeModified) {
            myApplyCompleted = false;
            myRevertCompleted = false;
          }
          return someSchemeModified;
        }

        public void apply() throws ConfigurationException {
          CodeStyleSchemesConfigurable.this.apply();
        }

        public void reset() {
          if (!myInitialResetInvoked) {
            try {
              resetFromChild();
            }
            finally {
              myInitialResetInvoked = true;
            }
          }
          else {
            revert();
          }

        }

        public String getId() {
          return "preferences.sourceCode." + getDisplayName();
        }

        public Runnable enableSearch(final String option) {
          return null;
        }

        public void disposeUIResources() {
          CodeStyleSchemesConfigurable.this.disposeUIResources();
        }
      };
    }

    return result;
  }

  private CodeStyleSchemesModel ensureModel() {
    if (myModel == null) {
      myModel = new CodeStyleSchemesModel(myProject);
      myRootSchemesPanel = new CodeStyleSchemesPanel(myModel);

      myModel.addListener(new CodeStyleSettingsListener(){
        public void currentSchemeChanged(final Object source) {
          if (source != myRootSchemesPanel) {
            myRootSchemesPanel.onSelectedSchemeChanged();
          }
        }

        public void schemeListChanged() {
          myRootSchemesPanel.resetSchemesCombo();
        }

        public void currentSettingsChanged() {
          
        }

        public void usePerProjectSettingsOptionChanged() {
          myRootSchemesPanel.usePerProjectSettingsOptionChanged();
        }

        public void schemeChanged(final CodeStyleScheme scheme) {
          //do nothing
        }
      });
    }
    return myModel;
  }

  public String getDisplayName() {
    return "Code Style";
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableCodeStyle.png");
  }

  public String getHelpTopic() {
    return "reference.settingsdialog.IDE.globalcodestyle";
  }

  public static CodeStyleSchemesConfigurable getInstance(Project project) {
    return ShowSettingsUtil.getInstance().findProjectConfigurable(project, CodeStyleSchemesConfigurable.class);
  }

  public void selectPage(Class pageToSelect) {
    //TODO lesya
    //getActivePanel().selectTab(pageToSelect);
  }

  public boolean isModified() {
    boolean schemeListModified = myModel.isSchemeListModified();
    if (schemeListModified) {
      myApplyCompleted = false;
      myRevertCompleted = false;
    }
    return schemeListModified;
  }

  public String getId() {
    return "preferences.sourceCode";
  }

  public HashSet<OptionDescription> processOptions() {
    return new HashSet<OptionDescription>();
    //TODO lesya
    //return getActivePanel().processOptions();
  }
}