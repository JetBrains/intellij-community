package com.intellij.codeInsight.intention.impl.config;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.GuiUtils;
import com.intellij.util.ResourceUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

public class IntentionSettingsPanel {
  private JPanel myPanel;
  private final IntentionSettingsTree myIntentionSettingsTree;
  private final IntentionDescriptionPanel myIntentionDescriptionPanel = new IntentionDescriptionPanel();

  private JPanel myTreePanel;
  private JPanel myDescriptionPanel;

  private HashMap<String, ArrayList<String>> myWords2DescriptorsMap;
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.config.IntentionSettingsPanel");
  private static final Pattern HTML_PATTERN = Pattern.compile("<[^<>]*>");

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

      protected List<IntentionActionMetaData> filterModel(String filter) {
        final List<IntentionActionMetaData> list = IntentionManagerSettings.getInstance().getMetaData();
        if (filter == null || filter.length() == 0) return list;
        cacheWordsToDescriptions(filter, list);
        final List<IntentionActionMetaData> result = new ArrayList<IntentionActionMetaData>();
        for (IntentionActionMetaData metaData : list) {
          if (isIntentionAccepted(metaData, filter)){
            result.add(metaData);
          }
        }
        return result;
      }
    };
    myTreePanel.setLayout(new BorderLayout());
    myTreePanel.add(myIntentionSettingsTree.getComponent(), BorderLayout.CENTER);

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
    SwingUtilities.invokeLater(new Runnable(){
      public void run() {
        myIntentionDescriptionPanel.init(myPanel.getWidth()/2);
      }
    });
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

  private void cacheWordsToDescriptions(String filter, List<IntentionActionMetaData> list){
    if (myWords2DescriptorsMap == null && filter != null && filter.length() > 0) {
      myWords2DescriptorsMap = new HashMap<String, ArrayList<String>>();
      try {
        for (IntentionActionMetaData metaData : list) {
          final URL description = metaData.getDescription();
          if (description != null) {
            @NonNls String descriptionText = ResourceUtil.loadText(description).toLowerCase();
            descriptionText = HTML_PATTERN.matcher(descriptionText).replaceAll(" ");
            final String[] words = descriptionText.split("[\\W]");
            for (String word : words) {
              if (word == null || word.length() == 0) continue;
              ArrayList<String> descriptors = myWords2DescriptorsMap.get(word);
              if (descriptors == null) {
                descriptors = new ArrayList<String>();
                myWords2DescriptorsMap.put(word, descriptors);
              }
              descriptors.add(metaData.myFamily);
            }
          }
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  private boolean isIntentionAccepted(IntentionActionMetaData metaData, @NonNls String filter) {
    filter = filter.toLowerCase();
    if (metaData.myFamily.toLowerCase().contains(filter)) {
      return true;
    }
    for (String category : metaData.myCategory) {
      if (category != null && category.toLowerCase().contains(filter)) {
        return true;
      }
    }

    final String[] filters = filter.split("[\\W]");
    for (String filtString : filters) {
      final ArrayList<String> descriptors = myWords2DescriptorsMap.get(filtString);
      if (descriptors == null || !descriptors.contains(metaData.myFamily)) return false;
    }
    return true;
  }
}