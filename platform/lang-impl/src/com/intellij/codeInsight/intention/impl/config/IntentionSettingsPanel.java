// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.openapi.options.MasterDetails;
import com.intellij.openapi.ui.DetailsComponent;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.GuiUtils;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.*;

public final class IntentionSettingsPanel implements MasterDetails {
  private JPanel myPanel;
  private final IntentionSettingsTree myIntentionSettingsTree;
  private final IntentionDescriptionPanel myIntentionDescriptionPanel = new IntentionDescriptionPanel();

  private JPanel myTreePanel;
  private JPanel myDescriptionPanel;
  private DetailsComponent myDetailsComponent;

  private final Alarm myResetAlarm = new Alarm();

  public IntentionSettingsPanel() {
    myIntentionSettingsTree = new IntentionSettingsTree() {
      @Override
      protected void selectionChanged(Object selected) {
        if (selected instanceof IntentionActionMetaData actionMetaData) {
          Runnable runnable = () -> {
            intentionSelected(actionMetaData);
            if (myDetailsComponent != null) {
              String[] text = ArrayUtil.append(actionMetaData.myCategory, actionMetaData.getFamily());
              myDetailsComponent.setText(text);
            }
          };
          myResetAlarm.cancelAllRequests();
          myResetAlarm.addRequest(runnable, 100);
        }
        else {
          categorySelected((String)selected);
          if (myDetailsComponent != null) {
            myDetailsComponent.setText((String)selected);
          }
        }
      }

      @Override
      protected Collection<IntentionActionMetaData> filterModel(String filter, boolean force) {
        Collection<@NotNull IntentionActionMetaData> list = IntentionManagerSettings.getInstance().getMetaData();
        if (filter == null || filter.isEmpty()) {
          return list;
        }

        Set<String> quoted = new HashSet<>();
        List<Set<String>> keySetList = SearchUtil.findKeys(filter, quoted);
        Collection<IntentionActionMetaData> result = new ArrayList<>();
        for (IntentionActionMetaData metaData : list) {
          if (isIntentionAccepted(metaData, filter, force, keySetList, quoted)) {
            result.add(metaData);
          }
        }
        Set<String> filters = SearchableOptionsRegistrar.getInstance().getProcessedWords(filter);
        if (force && result.isEmpty()) {
          if (filters.size() > 1) {
            result = filterModel(filter, false);
          }
        }
        return result;
      }
    };
    myTreePanel.setLayout(new BorderLayout());
    myTreePanel.add(myIntentionSettingsTree.getComponent(), BorderLayout.CENTER);

    GuiUtils.replaceJSplitPaneWithIDEASplitter(myPanel);

    myDescriptionPanel.setLayout(new BorderLayout());
    myDescriptionPanel.add(myIntentionDescriptionPanel.getComponent(), BorderLayout.CENTER);
  }

  private void intentionSelected(IntentionActionMetaData actionMetaData) {
    myIntentionDescriptionPanel.reset(actionMetaData, myIntentionSettingsTree.getFilter());
  }

  private void categorySelected(String intentionCategory) {
    myIntentionDescriptionPanel.reset(intentionCategory);
  }

  public void reset() {
    myIntentionSettingsTree.reset();
  }

  @Override
  public void initUi() {
    myDetailsComponent = new DetailsComponent();
    myDetailsComponent.setContent(myDescriptionPanel);
  }

  @Override
  public JComponent getToolbar() {
    return myIntentionSettingsTree.getToolbarPanel();
  }

  @Override
  public JComponent getMaster() {
    return myTreePanel;
  }

  @Override
  public DetailsComponent getDetails() {
    return myDetailsComponent;
  }

  public void apply() {
    myIntentionSettingsTree.apply();
  }

  public JPanel getComponent() {
    return myPanel;
  }

  public JTree getIntentionTree() {
    return myIntentionSettingsTree.getTree();
  }

  public boolean isModified() {
    return myIntentionSettingsTree.isModified();
  }

  public void dispose() {
    myIntentionSettingsTree.dispose();
    myIntentionDescriptionPanel.dispose();
  }

  public void selectIntention(String familyName) {
    myIntentionSettingsTree.selectIntention(familyName);
  }

  private static boolean isIntentionAccepted(IntentionActionMetaData metaData, @NonNls String filter, boolean forceInclude,
                                             List<? extends Set<String>> keySetList, @NotNull Set<String> quoted) {
    if (StringUtil.containsIgnoreCase(metaData.getFamily(), filter)) {
      return true;
    }

    for (String category : metaData.myCategory) {
      if (category != null && StringUtil.containsIgnoreCase(category, filter)) {
        return true;
      }
    }
    for (String stripped : quoted) {
      if (StringUtil.containsIgnoreCase(metaData.getFamily(), stripped)) {
        return true;
      }
      for (String category : metaData.myCategory) {
        if (category != null && StringUtil.containsIgnoreCase(category, stripped)) {
          return true;
        }
      }
      try {
        TextDescriptor description = metaData.getDescription();
        if (StringUtil.containsIgnoreCase(description.getText(), stripped)) {
          if (!forceInclude) return true;
        }
        else if (forceInclude) return false;
      }
      catch (IOException e) {
        //skip then
      }
    }
    for (Set<String> keySet : keySetList) {
      if (keySet.contains(metaData.getFamily())) {
        if (!forceInclude) {
          return true;
        }
      }
      else {
        if (forceInclude) {
          return false;
        }
      }
    }
    return forceInclude;
  }

  public Runnable showOption(String option) {
    return () -> {
      myIntentionSettingsTree.filter(myIntentionSettingsTree.filterModel(option, true));
      myIntentionSettingsTree.setFilter(option);
    };
  }
}
