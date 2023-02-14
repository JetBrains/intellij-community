// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.javascript.boilerplate;

import com.intellij.BundleBase;
import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.WebProjectGenerator;
import com.intellij.platform.templates.github.GithubTagInfo;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.ReloadableComboBoxPanel;
import com.intellij.util.ui.ReloadablePanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class GithubProjectGeneratorPeer implements WebProjectGenerator.GeneratorPeer<GithubTagInfo> {

  public static String getGithubZipballUrl(String ghUserName,String ghRepoName, String branch) {
    return String.format("https://github.com/%s/%s/zipball/%s", ghUserName, ghRepoName, branch);
  }

  private void createUIComponents() {
    myReloadableComboBoxPanel = new ReloadableComboBoxPanel<>() {

      @Override
      protected void doUpdateValues(@NotNull Set<GithubTagInfo> tags) {
        if (!shouldUpdate(tags)) {
          return;
        }

        List<GithubTagInfo> sortedTags = createSortedTagList(tags);
        GithubTagInfo selectedItem = getSelectedValue();
        if (selectedItem == null && sortedTags.size() > 0) {
          selectedItem = sortedTags.get(0);
        }
        myComboBox.removeAllItems();
        if (myDefaultBranchTag != null) {
          myComboBox.addItem(myDefaultBranchTag);
        }
        for (GithubTagInfo tag : sortedTags) {
          myComboBox.addItem(tag);
        }
        if (selectedItem != null) {
          // restore previously selected item
          for (int i = 0; i < myComboBox.getItemCount(); i++) {
            GithubTagInfo item = GithubTagInfo.tryCast(myComboBox.getItemAt(i));
            if (item != null && item.getName().equals(selectedItem.getName())) {
              myComboBox.setSelectedIndex(i);
              break;
            }
          }
        }
        myComboBox.updateUI();
        fireStateChanged();
      }

      private boolean shouldUpdate(Set<GithubTagInfo> newTags) {
        if (myComboBox.getItemCount() == 0) {
          return true;
        }
        int count = myComboBox.getItemCount();
        Set<GithubTagInfo> oldTags = new HashSet<>();
        for (int i = 1; i < count; i++) {
          GithubTagInfo item = ObjectUtils.tryCast(myComboBox.getItemAt(i), GithubTagInfo.class);
          if (item != null) {
            oldTags.add(item);
          }
        }
        return !oldTags.equals(newTags);
      }

      @NotNull
      @Override
      protected JComboBox<GithubTagInfo> createValuesComboBox() {
        JComboBox<GithubTagInfo> box = super.createValuesComboBox();
        box.setRenderer(SimpleListCellRenderer.create((label, tag, index) -> {
          final String text;
          if (tag == null) {
            text = isBackgroundJobRunning() ? CommonBundle.getLoadingTreeNodeText() : LangBundle.message("label.unavailable");
          }
          else {
            text = tag.getName();
          }
          label.setText(text);
        }));

        return box;
      }
    };

    myVersionPanel = myReloadableComboBoxPanel.getMainPanel();
  }

  private final List<WebProjectGenerator.SettingsStateListener> myListeners = new ArrayList<>();
  private final GithubTagInfo myDefaultBranchTag;
  private final GithubTagListProvider myTagListProvider;
  private JComponent myComponent;
  private JPanel myVersionPanel;
  private ReloadablePanel<GithubTagInfo> myReloadableComboBoxPanel;

  public GithubProjectGeneratorPeer(@NotNull AbstractGithubTagDownloadedProjectGenerator generator) {
    String ghUserName = generator.getGithubUserName();
    String ghRepoName = generator.getGithubRepositoryName();
    String defaultBranchName = generator.getDefaultBranchName();
    myDefaultBranchTag = defaultBranchName != null ? new GithubTagInfo(
      defaultBranchName,
      getGithubZipballUrl(ghUserName, ghRepoName, defaultBranchName)
    ) : null;

    myTagListProvider = new GithubTagListProvider(ghUserName, ghRepoName);

    myReloadableComboBoxPanel.setDataProvider(new ReloadableComboBoxPanel.DataProvider<>() {
      @Override
      public Set<GithubTagInfo> getCachedValues() {
        return myTagListProvider.getCachedTags();
      }

      @Override
      public void updateValuesAsynchronously() {
        myTagListProvider.updateTagListAsynchronously(GithubProjectGeneratorPeer.this);
      }
    });

    myReloadableComboBoxPanel.reloadValuesInBackground();
  }

  void onTagsUpdated(@NotNull Set<GithubTagInfo> tags) {
    myReloadableComboBoxPanel.onUpdateValues(tags);
  }

  void onTagsUpdateError(@NotNull final @NlsContexts.DialogMessage String errorMessage) {
    myReloadableComboBoxPanel.onValuesUpdateError(errorMessage);
  }

  @NotNull
  private static List<GithubTagInfo> createSortedTagList(@NotNull Collection<? extends GithubTagInfo> tags) {
    List<GithubTagInfo> sortedTags = new ArrayList<>(tags);
    sortedTags.sort((tag1, tag2) -> {
      GithubTagInfo.Version v1 = tag1.getVersion();
      GithubTagInfo.Version v2 = tag2.getVersion();
      return v2.compareTo(v1);
    });
    for (GithubTagInfo tag : sortedTags) {
      tag.setRecentTag(false);
    }
    if (!sortedTags.isEmpty()) {
      sortedTags.get(0).setRecentTag(true);
    }
    return sortedTags;
  }


  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @Override
  public void buildUI(@NotNull SettingsStep settingsStep) {
    settingsStep.addSettingsField(BundleBase.replaceMnemonicAmpersand(IdeBundle.message("github.project.generator.version")), myVersionPanel);
    settingsStep.addSettingsComponent(myReloadableComboBoxPanel.getErrorComponent());
  }

  @NotNull
  @Override
  public GithubTagInfo getSettings() {
    GithubTagInfo tag = myReloadableComboBoxPanel.getSelectedValue();
    if (tag == null) {
      throw new RuntimeException("[internal error] No versions available.");
    }
    return tag;
  }

  @Override
  @Nullable
  public ValidationInfo validate() {
    GithubTagInfo tag = myReloadableComboBoxPanel.getSelectedValue();
    if (tag != null) {
      return null;
    }
    String errorMessage = StringUtil.notNullize(myReloadableComboBoxPanel.getErrorComponent().getText());
    if (errorMessage.isEmpty()) {
      errorMessage = IdeBundle.message("github.project.generator.versions.not.loaded.error");
    }
    return new ValidationInfo(errorMessage);
  }

  @Override
  public boolean isBackgroundJobRunning() {
    return myReloadableComboBoxPanel.isBackgroundJobRunning();
  }

  @Override
  public void addSettingsStateListener(@NotNull WebProjectGenerator.SettingsStateListener listener) {
    myListeners.add(listener);
  }


  private void fireStateChanged() {
    GithubTagInfo tag = myReloadableComboBoxPanel.getSelectedValue();
    for (WebProjectGenerator.SettingsStateListener listener : myListeners) {
      listener.stateChanged(tag != null);
    }
  }
}
