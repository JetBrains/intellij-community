package com.intellij.lang.javascript.boilerplate;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Pair;
import com.intellij.platform.WebProjectGenerator;
import com.intellij.platform.templates.github.GithubTagInfo;
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
  private JComboBox myComboBox;
  private JComponent myComponent;
  private JLabel myErrorMessage;
  private JPanel myVersionPanel;
  private JPanel myActionPanel;

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

    myTagListProvider = new GithubTagListProvider(ghUserName, ghRepoName);
    fillActionPanel();
    ImmutableSet<GithubTagInfo> cachedTags = myTagListProvider.getCachedTags();
    if (cachedTags != null) {
      tagsUpdated(cachedTags);
    }

    myErrorMessage.setText(null);
    reloadTagsInBackground();
  }

  void tagsUpdated(@NotNull ImmutableSet<GithubTagInfo> tags) {
    show(UpdateStatus.IDLE);
    if (!shouldUpdate(tags)) {
      return;
    }
    List<GithubTagInfo> sortedTags = createSortedTagList(tags);
    GithubTagInfo selectedItem = GithubTagInfo.tryCast(myComboBox.getSelectedItem());
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

  @Override
  public Pair<String, JComponent> getSettingsField() {
    return new Pair<String, JComponent>("\u001BVersion:", myVersionPanel);
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

  private void reloadTagsInBackground() {
    show(UpdateStatus.UPDATING);
    myTagListProvider.updateTagListAsynchronously(this);
  }

  private void show(@NotNull UpdateStatus status) {
    CardLayout cardLayout = (CardLayout) myActionPanel.getLayout();
    cardLayout.show(myActionPanel, status.name());
    if (status == UpdateStatus.UPDATING) {
      myLoadingVersionIcon.resume();
    }
  }

  private void fillActionPanel() {
    myActionPanel.add(createReloadButtonPanel(), UpdateStatus.IDLE.name());
    myActionPanel.add(createReloadInProgressPanel(), UpdateStatus.UPDATING.name());
    show(UpdateStatus.IDLE);
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
    JPanel panel = new JPanel(new BorderLayout(3, 0));
    myLoadingVersionIcon.suspend();
    panel.add(myLoadingVersionIcon, BorderLayout.CENTER);
    panel.add(new JLabel("Loading..."), BorderLayout.EAST);
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
