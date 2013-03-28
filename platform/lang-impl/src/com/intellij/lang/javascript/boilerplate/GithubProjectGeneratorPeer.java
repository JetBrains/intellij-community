/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.lang.javascript.boilerplate;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.WebProjectGenerator;
import com.intellij.platform.templates.github.GithubTagInfo;
import com.intellij.ui.JBColor;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * @author Sergey Simonchik
 */
public class GithubProjectGeneratorPeer implements WebProjectGenerator.GeneratorPeer<GithubTagInfo> {

  private static final String CONTROL_PLACE = "Github.Project.Generator.Reload";

  private enum UpdateStatus {
    UPDATING, IDLE
  }

  private final List<WebProjectGenerator.SettingsStateListener> myListeners = ContainerUtil.newArrayList();
  private final GithubTagInfo myMasterTag;
  private final GithubTagListProvider myTagListProvider;
  private final AsyncProcessIcon myLoadingVersionIcon = new AsyncProcessIcon("Getting github tags");
  private final JLabel myErrorMessage = new JLabel();
  private JComboBox myComboBox;
  private JComponent myComponent;
  private JPanel myVersionPanel;
  private JPanel myActionPanel;
  private UpdateStatus myUpdateStatus;

  public GithubProjectGeneratorPeer(@NotNull AbstractGithubTagDownloadedProjectGenerator generator) {
    myErrorMessage.setForeground(JBColor.RED);
    String ghUserName = generator.getGithubUserName();
    String ghRepoName = generator.getGithubRepositoryName();
    myMasterTag = new GithubTagInfo(
      "master",
      String.format("https://github.com/%s/%s/zipball/master", ghUserName, ghRepoName)
    );

    myComboBox.setRenderer(new ListCellRendererWrapper<GithubTagInfo>() {
      @Override
      public void customize(JList list, GithubTagInfo tag, int index, boolean selected, boolean hasFocus) {
        final String text;
        if (tag == null) {
          text = isBackgroundJobRunning() ? "Loading..." : "Unavailable";
        }
        else {
          text = tag.getName();
        }
        setText(text);
      }
    });

    myTagListProvider = new GithubTagListProvider(ghUserName, ghRepoName);
    fillActionPanel();
    ImmutableSet<GithubTagInfo> cachedTags = myTagListProvider.getCachedTags();
    if (cachedTags != null) {
      onTagsUpdated(cachedTags);
    }
    reloadTagsInBackground();
  }

  void onTagsUpdated(@NotNull ImmutableSet<GithubTagInfo> tags) {
    changeUpdateStatus(UpdateStatus.IDLE);
    if (!shouldUpdate(tags)) {
      return;
    }
    List<GithubTagInfo> sortedTags = createSortedTagList(tags);
    GithubTagInfo selectedItem = getSelectedTag();
    if (selectedItem == null && sortedTags.size() > 0) {
      selectedItem = sortedTags.get(0);
    }
    myComboBox.removeAllItems();
    myComboBox.addItem(myMasterTag);
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

  void onTagsUpdateError(@NotNull final String errorMessage) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (getSelectedTag() == null) {
          myErrorMessage.setText(errorMessage);
        }
        changeUpdateStatus(UpdateStatus.IDLE);
      }
    });
  }

  private boolean shouldUpdate(@NotNull ImmutableSet<GithubTagInfo> newTags) {
    if (myComboBox.getItemCount() == 0) {
      return true;
    }
    int count = myComboBox.getItemCount();
    Set<GithubTagInfo> oldTags = Sets.newHashSet();
    for (int i = 1; i < count; i++) {
      GithubTagInfo item = ObjectUtils.tryCast(myComboBox.getItemAt(i), GithubTagInfo.class);
      if (item != null) {
        oldTags.add(item);
      }
    }
    return !oldTags.equals(newTags);
  }

  @NotNull
  private static List<GithubTagInfo> createSortedTagList(@NotNull ImmutableCollection<GithubTagInfo> tags) {
    List<GithubTagInfo> sortedTags = ContainerUtil.newArrayList(tags);
    Collections.sort(sortedTags, new Comparator<GithubTagInfo>() {
      @Override
      public int compare(GithubTagInfo tag1, GithubTagInfo tag2) {
        GithubTagInfo.Version v1 = tag1.getVersion();
        GithubTagInfo.Version v2 = tag2.getVersion();
        return v2.compareTo(v1);
      }
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
    settingsStep.addSettingsField("\u001BVersion:", myVersionPanel);
    settingsStep.addSettingsComponent(myErrorMessage);
  }

  @NotNull
  @Override
  public GithubTagInfo getSettings() {
    GithubTagInfo tag = getSelectedTag();
    if (tag == null) {
      throw new RuntimeException("[internal error] No versions available.");
    }
    return tag;
  }

  @Override
  @Nullable
  public ValidationInfo validate() {
    GithubTagInfo tag = getSelectedTag();
    if (tag != null) {
      return null;
    }
    String errorMessage = StringUtil.notNullize(myErrorMessage.getText());
    if (errorMessage.isEmpty()) {
      errorMessage = "Versions have not been loaded yet.";
    }
    return new ValidationInfo(errorMessage);
  }

  @Override
  public boolean isBackgroundJobRunning() {
    return myUpdateStatus == UpdateStatus.UPDATING;
  }

  @Override
  public void addSettingsStateListener(@NotNull WebProjectGenerator.SettingsStateListener listener) {
    myListeners.add(listener);
  }

  @Nullable
  private GithubTagInfo getSelectedTag() {
    return GithubTagInfo.tryCast(myComboBox.getSelectedItem());
  }

  private void fireStateChanged() {
    GithubTagInfo tag = getSelectedTag();
    for (WebProjectGenerator.SettingsStateListener listener : myListeners) {
      listener.stateChanged(tag != null);
    }
  }

  private void reloadTagsInBackground() {
    changeUpdateStatus(UpdateStatus.UPDATING);
    myErrorMessage.setText(null);
    myTagListProvider.updateTagListAsynchronously(this);
  }

  private void changeUpdateStatus(@NotNull UpdateStatus status) {
    CardLayout cardLayout = (CardLayout) myActionPanel.getLayout();
    cardLayout.show(myActionPanel, status.name());
    if (status == UpdateStatus.UPDATING) {
      myLoadingVersionIcon.resume();
    }
    else {
      myLoadingVersionIcon.suspend();
    }
    myUpdateStatus = status;
  }

  private void fillActionPanel() {
    myActionPanel.add(createReloadButtonPanel(), UpdateStatus.IDLE.name());
    myActionPanel.add(createReloadInProgressPanel(), UpdateStatus.UPDATING.name());
    changeUpdateStatus(UpdateStatus.IDLE);
  }

  @NotNull
  private JPanel createReloadButtonPanel() {
    ReloadAction reloadAction = new ReloadAction();
    ActionButton reloadButton = new ActionButton(
      reloadAction,
      reloadAction.getTemplatePresentation().clone(),
      CONTROL_PLACE,
      ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
    );
    JPanel panel = new JPanel(new BorderLayout(0, 0));
    panel.add(reloadButton, BorderLayout.WEST);
    return panel;
  }

  @NotNull
  private JPanel createReloadInProgressPanel() {
    JPanel panel = new JPanel();
    panel.add(myLoadingVersionIcon);
    return panel;
  }

  private class ReloadAction extends AnAction {

    private ReloadAction() {
      super("Reload versions", null, AllIcons.Actions.Refresh);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      GithubProjectGeneratorPeer.this.reloadTagsInBackground();
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(true);
    }
  }

}
