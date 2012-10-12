package com.intellij.lang.javascript.boilerplate;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.platform.WebProjectGenerator;
import com.intellij.platform.templates.github.GithubTagInfo;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * @author Sergey Simonchik
 */
public class GithubProjectGeneratorPeer implements WebProjectGenerator.GeneratorPeer<GithubTagInfo> {

  private final List<WebProjectGenerator.SettingsStateListener> myListeners = ContainerUtil.newArrayList();
  private final GithubTagInfo myMasterTag;
  private JComboBox myComboBox;
  private final GithubTagListProvider myProvider;
  private JComponent myComponent;
  private JLabel myErrorMessage;
  private JButton myReloadButton;

  public GithubProjectGeneratorPeer(@NotNull AbstractGithubTagDownloadedProjectGenerator generator) {
    String ghUserName = generator.getGithubUserName();
    String ghRepoName = generator.getGithubRepositoryName();
    myMasterTag = new GithubTagInfo(
      "master",
      String.format("https://github.com/%s/%s/zipball/master", ghUserName, ghRepoName)
    );

    myComboBox.setRenderer(new ListCellRendererWrapper<GithubTagInfo>() {
      @Override
      public void customize(JList list, GithubTagInfo tag, int index, boolean selected, boolean hasFocus) {
        if (tag != null) {
          setText(tag.getName());
        }
      }
    });

    myProvider = new GithubTagListProvider(ghUserName, ghRepoName);
    ImmutableSet<GithubTagInfo> cachedTags = myProvider.getCachedTags();
    if (cachedTags != null) {
      updateTagList(cachedTags);
    }

    myErrorMessage.setText(null);
    myReloadButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        asyncUpdateTags();
      }
    });
    asyncUpdateTags();
  }

  private void asyncUpdateTags() {
    myProvider.updateTagListAsynchronously(this);
  }

  void updateTagList(@NotNull ImmutableSet<GithubTagInfo> tags) {
    if (!shouldUpdate(tags)) {
      return;
    }
    List<GithubTagInfo> sortedTags = createSortedTagList(tags);
    GithubTagInfo selectedItem = ObjectUtils.tryCast(myComboBox.getSelectedItem(), GithubTagInfo.class);
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
        GithubTagInfo
          item = ObjectUtils.tryCast(myComboBox.getItemAt(i), GithubTagInfo.class);
        if (item != null && item.getName().equals(selectedItem.getName())) {
          myComboBox.setSelectedIndex(i);
          break;
        }
      }
    }
    myComboBox.updateUI();
  }

  void setErrorMessage(@Nullable final String message) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        myErrorMessage.setText(message);
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
    return sortedTags;
  }


  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @NotNull
  @Override
  public GithubTagInfo getSettings() {
    Object obj = myComboBox.getSelectedItem();
    if (obj instanceof GithubTagInfo) {
      return (GithubTagInfo) obj;
    }
    throw new RuntimeException("Can't handle selected version: " + obj);
  }

  @Override
  @Nullable
  public ValidationInfo validate() {
    Object obj = myComboBox.getSelectedItem();
    if (obj instanceof GithubTagInfo) {
      return null;
    }
    return new ValidationInfo("Can't handle selected version: " + obj);
  }

  @Override
  public void addSettingsStateListener(@NotNull WebProjectGenerator.SettingsStateListener listener) {
    myListeners.add(listener);
  }

}
