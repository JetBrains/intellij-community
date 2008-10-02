package com.intellij.application.options.editor;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.TabbedPaneWrapper;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class EditorOptions implements SearchableConfigurable.Parent {

  private EditorOptionsPanel myPanel;
  private Wrapper myComponentWrapper = new Wrapper();


  public boolean isModified() {
    return myPanel.isModified();
  }

  public JComponent createComponent() {
    if (myPanel != null) return myComponentWrapper;

    myPanel = new EditorOptionsPanel();
    myComponentWrapper.setContent(myPanel.getTabbedPanel());
    return myComponentWrapper;
  }

  public Configurable[] getConfigurables() {
    if (myPanel == null) {
      createComponent();
    }

    myComponentWrapper.removeAll();

    final TabbedPaneWrapper tabs = myPanel.getTabs();
    Configurable[] result = new Configurable[tabs.getTabCount()];

    for (int i= 0; i < tabs.getTabCount(); i++) {
      result[i] = new Kid(tabs.getTitleAt(i), tabs.getComponentAt(i));
    }

    tabs.removeAll();

    return result;
  }

  public String getDisplayName() {
    return ApplicationBundle.message("title.editor");
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableEditor.png");
  }

  public void reset() {
    myPanel.reset();
  }

  public void apply() throws ConfigurationException {
    myPanel.apply();
  }

  public void disposeUIResources() {
    myPanel = null;
  }

  public String getHelpTopic() {
    return "preferences.editor";
  }

  public String getId() {
    return getHelpTopic();
  }

  public boolean clearSearch() {
    return false;
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }

  private class Kid implements Configurable {

    String myName;
    JComponent myComponent;

    private Kid(final String name, final JComponent component) {
      myName = name;
      myComponent = component;
    }

    @Nls
    public String getDisplayName() {
      return myName;
    }

    public Icon getIcon() {
      return null;
    }

    public String getHelpTopic() {
      return null;
    }

    public JComponent createComponent() {
      return myComponent;
    }

    public boolean isModified() {
      return EditorOptions.this.isModified();
    }

    public void apply() throws ConfigurationException {
      EditorOptions.this.apply();
    }

    public void reset() {
      EditorOptions.this.reset();
    }

    public void disposeUIResources() {
      EditorOptions.this.disposeUIResources();
    }
  }

  public boolean isResponsibleForChildren() {
    return true;
  }
}