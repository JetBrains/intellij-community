package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.GlassPanel;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class LiveTemplatesConfigurable extends BaseConfigurable implements SearchableConfigurable, ApplicationComponent {
  private TemplateListPanel myPanel;
  private GlassPanel myGlassPanel;

  public String getComponentName() {
    return "LiveTemplatesConfigurable";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public LiveTemplatesConfigurable() {
  }

  public boolean isModified() {
    return myPanel.isModified();
  }

  public JComponent createComponent() {
    myPanel = new TemplateListPanel();
    myGlassPanel = new GlassPanel(myPanel);
    return myPanel;
  }

  public String getDisplayName() {
    return CodeInsightBundle.message("templates.settings.page.title");
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/liveTemplates.png");
  }

  public void reset() {
    myPanel.getRootPane().setGlassPane(myGlassPanel);
    myPanel.reset();
  }

  public void apply() {
    myPanel.apply();
  }

  public void disposeUIResources() {
    if (myPanel != null) {
      myPanel.dispose();
    }
    myPanel = null;
  }

  public String getHelpTopic() {
    return "editing.templates";
  }

  public Runnable showOption(String option) {
    return SearchUtil.lightOptions(this, myPanel, option, myGlassPanel);
  }

  public String getId() {
    return getHelpTopic();
  }

  public void clearSearch() {
    myGlassPanel.clear();
  }
}