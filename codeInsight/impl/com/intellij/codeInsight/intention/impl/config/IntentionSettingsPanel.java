package com.intellij.codeInsight.intention.impl.config;

import com.intellij.ui.GuiUtils;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class IntentionSettingsPanel {
  private  JPanel myPanel;
  private final IntentionSettingsTree myIntentionSettingsTree;
  private final IntentionDescriptionPanel myIntentionDescriptionPanel;

  private JPanel myTreePanel;
  private JPanel myDescriptionPanel;

  public IntentionSettingsPanel() {
    myIntentionSettingsTree = new IntentionSettingsTree() {
      protected void selectionChanged(Object selected) {
        if (selected instanceof IntentionActionMetaData) {
          IntentionActionMetaData actionMetaData = (IntentionActionMetaData)selected;
          intentionSelected(actionMetaData);
        }
        else {
          categorySelected((String)selected);
        }
      }
    };
    myTreePanel.setLayout(new BorderLayout());
    myTreePanel.add(myIntentionSettingsTree.getComponent(), BorderLayout.CENTER);

    myIntentionDescriptionPanel = new IntentionDescriptionPanel();
    GuiUtils.replaceJSplitPaneWithIDEASplitter(myPanel, true);

    myDescriptionPanel.setLayout(new BorderLayout());
    myDescriptionPanel.add(myIntentionDescriptionPanel.getComponent(), BorderLayout.CENTER);
  }

  private void intentionSelected(IntentionActionMetaData actionMetaData) {
    myIntentionDescriptionPanel.reset(actionMetaData);
  }

  private void categorySelected(String intentionCategory) {
    myIntentionDescriptionPanel.reset(intentionCategory);
  }

  public void reset() {
    List<IntentionActionMetaData> list = IntentionManagerSettings.getInstance().getMetaData();
    myIntentionSettingsTree.reset(list);
  }

  public void apply() {
    myIntentionSettingsTree.apply();
  }

  public JPanel getComponent() {
    return myPanel;
  }

  public boolean isModified() {
    return myIntentionSettingsTree.isModified();
  }

  public void dispose() {
    myIntentionSettingsTree.dispose();
    myIntentionDescriptionPanel.dispose();
  }
}
