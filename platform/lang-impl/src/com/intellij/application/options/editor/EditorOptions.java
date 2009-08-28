package com.intellij.application.options.editor;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class EditorOptions implements SearchableConfigurable.Parent {
  private EditorOptionsPanel myEditorOptionsPanel;

  public Configurable[] getConfigurables() {
    return Extensions.getExtensions(EditorOptionsProvider.EP_NAME);
  }

  public String getDisplayName() {
    return ApplicationBundle.message("title.editor");
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableEditor.png");
  }

  public String getHelpTopic() {
    return "preferences.editor";
  }

  public String getId() {
    return getHelpTopic();
  }

  public Runnable enableSearch(final String option) {
    return null;
  }

  public boolean hasOwnContent() {
    return true;
  }

  public boolean isVisible() {
    return true;
  }

  public JComponent createComponent() {
    myEditorOptionsPanel = new EditorOptionsPanel();
    return myEditorOptionsPanel.getComponent();
  }

  public boolean isModified() {
    return myEditorOptionsPanel != null && myEditorOptionsPanel.isModified();
  }

  public void apply() throws ConfigurationException {
    if (myEditorOptionsPanel != null) {
      myEditorOptionsPanel.apply();
    }
  }

  public void reset() {
    if (myEditorOptionsPanel != null) {
      myEditorOptionsPanel.reset();
    }
  }

  public void disposeUIResources() {
    myEditorOptionsPanel = null;

  }
}