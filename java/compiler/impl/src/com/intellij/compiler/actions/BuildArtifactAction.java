// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.actions;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.idea.ActionsBundle;
import com.intellij.lang.IdeLanguageCustomization;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.impl.compiler.ArtifactsWorkspaceSettings;
import com.intellij.task.ProjectTaskManager;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.*;

public final class BuildArtifactAction extends DumbAwareAction {
  private static final class Holder {
    private static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.balloonGroup("Clean artifact");
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Project project = getEventProject(e);
    final Presentation presentation = e.getPresentation();
    boolean enabled = project != null && !ArtifactUtil.getArtifactWithOutputPaths(project).isEmpty();
    if (IdeLanguageCustomization.getInstance().getPrimaryIdeLanguages().contains(JavaFileType.INSTANCE.getLanguage())
        && ActionPlaces.MAIN_MENU.equals(e.getPlace())) {
      //building artifacts is a valuable functionality for Java IDEs, let's not hide 'Build Artifacts' item from the main menu
      presentation.setEnabled(enabled);
    }
    else {
      presentation.setEnabledAndVisible(enabled);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = getEventProject(e);
    if (project == null) return;

    final List<Artifact> artifacts = ArtifactUtil.getArtifactWithOutputPaths(project);
    if (artifacts.isEmpty()) return;

    List<ArtifactPopupItem> items = new ArrayList<>();
    if (artifacts.size() > 1) {
      items.add(0, new ArtifactPopupItem(null, JavaCompilerBundle.message("artifacts.menu.item.all"), EmptyIcon.ICON_16));
    }
    Set<Artifact> selectedArtifacts = new HashSet<>(ArtifactsWorkspaceSettings.getInstance(project).getArtifactsToBuild());
    IntList selectedIndices = new IntArrayList();
    if (Comparing.haveEqualElements(artifacts, selectedArtifacts) && selectedArtifacts.size() > 1) {
      selectedIndices.add(0);
      selectedArtifacts.clear();
    }

    for (Artifact artifact : artifacts) {
      final ArtifactPopupItem item = new ArtifactPopupItem(artifact, artifact.getName(), artifact.getArtifactType().getIcon());
      if (selectedArtifacts.contains(artifact)) {
        selectedIndices.add(items.size());
      }
      items.add(item);
    }

    final ProjectSettingsService projectSettingsService = ProjectSettingsService.getInstance(project);
    final ArtifactAwareProjectSettingsService settingsService = projectSettingsService instanceof ArtifactAwareProjectSettingsService ? (ArtifactAwareProjectSettingsService)projectSettingsService : null;

    final ChooseArtifactStep step = new ChooseArtifactStep(items, artifacts.get(0), project, settingsService);
    step.setDefaultOptionIndices(selectedIndices.toIntArray());

    ListPopup popup = JBPopupFactory.getInstance().createListPopup(step);
    KeyStroke editKeyStroke = KeymapUtil.getKeyStroke(CommonShortcuts.getEditSource());
    if (popup instanceof ListPopupImpl && settingsService != null && editKeyStroke != null) {
      ListPopupImpl popupImpl = (ListPopupImpl)popup;
      popupImpl.registerAction("editArtifact", editKeyStroke, new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          Object[] values = popupImpl.getSelectedValues();
          popupImpl.cancel();
          settingsService.openArtifactSettings(values.length > 0 ? ((ArtifactPopupItem)values[0]).getArtifact() : null);
        }
      });
    }
    popup.showCenteredInCurrentWindow(project);
  }

  private static void doBuild(@NotNull Project project, final @NotNull List<ArtifactPopupItem> items, boolean rebuild) {
    final Artifact[] artifacts = getArtifacts(items, project);
    if (rebuild) {
      ProjectTaskManager.getInstance(project).rebuild(artifacts);
    }
    else {
      ProjectTaskManager.getInstance(project).build(artifacts);
    }
  }

  private static Artifact[] getArtifacts(final List<ArtifactPopupItem> items, final Project project) {
    Set<Artifact> artifacts = new LinkedHashSet<>();
    for (ArtifactPopupItem item : items) {
      artifacts.addAll(item.getArtifacts(project));
    }
    return artifacts.toArray(new Artifact[0]);
  }

  private static final class BuildArtifactItem extends ArtifactActionItem {
    private BuildArtifactItem(List<ArtifactPopupItem> item, Project project) {
      super(item, project, JavaCompilerBundle.message("artifacts.menu.item.build"));
    }

    @Override
    public void run() {
      doBuild(myProject, myArtifactPopupItems, false);
    }
  }

  private static final class CleanArtifactItem extends ArtifactActionItem {
    private CleanArtifactItem(@NotNull List<ArtifactPopupItem> item, @NotNull Project project) {
      super(item, project, JavaCompilerBundle.message("artifacts.menu.item.clean"));
    }

    @Override
    public void run() {
      Set<VirtualFile> parents = new HashSet<>();
      final VirtualFile[] roots = ProjectRootManager.getInstance(myProject).getContentSourceRoots();
      for (VirtualFile root : roots) {
        VirtualFile parent = root;
        while (parent != null && !parents.contains(parent)) {
          parents.add(parent);
          parent = parent.getParent();
        }
      }

      Map<String, String> outputPathContainingSourceRoots = new HashMap<>();
      final List<Pair<File, Artifact>> toClean = new ArrayList<>();
      Artifact[] artifacts = getArtifacts(myArtifactPopupItems, myProject);
      for (Artifact artifact : artifacts) {
        String outputPath = artifact.getOutputFilePath();
        if (outputPath != null) {
          toClean.add(Pair.create(new File(FileUtil.toSystemDependentName(outputPath)), artifact));
          final VirtualFile outputFile = LocalFileSystem.getInstance().findFileByPath(outputPath);
          if (parents.contains(outputFile)) {
            outputPathContainingSourceRoots.put(artifact.getName(), outputPath);
          }
        }
      }

      if (!outputPathContainingSourceRoots.isEmpty()) {
        final String message;
        if (outputPathContainingSourceRoots.size() == 1) {
          final String name = ContainerUtil.getFirstItem(outputPathContainingSourceRoots.keySet());
          final String output = outputPathContainingSourceRoots.get(name);
          message = JavaCompilerBundle.message("dialog.message.output.dir.contains.source.roots", output, name);
        }
        else {
          StringBuilder info = new StringBuilder();
          for (String name : outputPathContainingSourceRoots.keySet()) {
            info.append(JavaCompilerBundle.message("dialog.message.output.dir.artifact", name, outputPathContainingSourceRoots.get(name)))
              .append("\n");
          }
          message = JavaCompilerBundle.message("dialog.message.output.dirs.contain.source.roots", info);
        }
        final int answer = Messages.showYesNoDialog(myProject, message, JavaCompilerBundle.message("clean.artifacts"), null);
        if (answer != Messages.YES) {
          return;
        }
      }

      new Task.Backgroundable(myProject, JavaCompilerBundle.message("cleaning.artifacts"), true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          List<File> deleted = new ArrayList<>();
          for (Pair<File, Artifact> pair : toClean) {
            indicator.checkCanceled();
            File file = pair.getFirst();
            if (!FileUtil.delete(file)) {
              Holder.NOTIFICATION_GROUP
                .createNotification(JavaCompilerBundle.message("cannot.clean.0.artifact", pair.getSecond().getName()),
                                    JavaCompilerBundle.message("cannot.delete.0", file.getAbsolutePath()),
                                    NotificationType.ERROR)
                .notify(myProject);
            }
            else {
              deleted.add(file);
            }
          }
          LocalFileSystem.getInstance().refreshIoFiles(deleted, true, true, null);
        }
      }.queue();
    }
  }

  private static final class RebuildArtifactItem extends ArtifactActionItem {
    private RebuildArtifactItem(List<ArtifactPopupItem> item, Project project) {
      super(item, project, JavaCompilerBundle.message("artifacts.menu.item.rebuild"));
    }

    @Override
    public void run() {
      doBuild(myProject, myArtifactPopupItems, true);
    }
  }

  private static final class EditArtifactItem extends ArtifactActionItem {
    private final ArtifactAwareProjectSettingsService mySettingsService;

    private EditArtifactItem(List<ArtifactPopupItem> item, Project project, final ArtifactAwareProjectSettingsService projectSettingsService) {
      super(item, project, JavaCompilerBundle.message("artifacts.menu.item.edit"));
      mySettingsService = projectSettingsService;
    }

    @Override
    public void run() {
      mySettingsService.openArtifactSettings(myArtifactPopupItems.get(0).getArtifact());
    }
  }

  private static abstract class ArtifactActionItem implements Runnable {
    protected final List<ArtifactPopupItem> myArtifactPopupItems;
    protected final Project myProject;
    @Nls
    private final String myActionName;

    protected ArtifactActionItem(@NotNull List<ArtifactPopupItem> item, @NotNull Project project, @NotNull @Nls String name) {
      myArtifactPopupItems = item;
      myProject = project;
      myActionName = name;
    }

    public @Nls String getActionName() {
      return myActionName;
    }
  }

  private static final class ArtifactPopupItem {
    @Nullable private final Artifact myArtifact;
    @Nls
    private final String myText;
    private final Icon myIcon;

    private ArtifactPopupItem(@Nullable Artifact artifact, @Nls String text, Icon icon) {
      myArtifact = artifact;
      myText = text;
      myIcon = icon;
    }

    @Nullable
    public Artifact getArtifact() {
      return myArtifact;
    }

    @Nls
    public String getText() {
      return myText;
    }

    public Icon getIcon() {
      return myIcon;
    }

    public List<Artifact> getArtifacts(Project project) {
      final Artifact artifact = getArtifact();
      return artifact != null ? Collections.singletonList(artifact) : ArtifactUtil.getArtifactWithOutputPaths(project);
    }
  }

  private static class ChooseArtifactStep extends MultiSelectionListPopupStep<ArtifactPopupItem> {
    private final Artifact myFirst;
    private final Project myProject;
    private final ArtifactAwareProjectSettingsService mySettingsService;

    ChooseArtifactStep(List<ArtifactPopupItem> artifacts,
                              Artifact first,
                              Project project, final ArtifactAwareProjectSettingsService settingsService) {
      super(ActionsBundle.message("group.BuildArtifactsGroup.text"), artifacts);
      myFirst = first;
      myProject = project;
      mySettingsService = settingsService;
    }

    @Override
    public boolean isSpeedSearchEnabled() {
      return true;
    }

    @Override
    public Icon getIconFor(ArtifactPopupItem aValue) {
      return aValue.getIcon();
    }

    @NotNull
    @Override
    public String getTextFor(ArtifactPopupItem value) {
      return value.getText();
    }

    @Override
    public boolean hasSubstep(List<? extends ArtifactPopupItem> selectedValues) {
      return true;
    }

    @Override
    public ListSeparator getSeparatorAbove(ArtifactPopupItem value) {
      return myFirst.equals(value.getArtifact()) ? new ListSeparator() : null;
    }

    @Override
    public PopupStep<?> onChosen(final List<ArtifactPopupItem> selectedValues, boolean finalChoice) {
      if (finalChoice) {
        return doFinalStep(() -> doBuild(myProject, selectedValues, false));
      }
      final List<ArtifactActionItem> actions = new ArrayList<>();
      actions.add(new BuildArtifactItem(selectedValues, myProject));
      actions.add(new RebuildArtifactItem(selectedValues, myProject));
      actions.add(new CleanArtifactItem(selectedValues, myProject));
      if (mySettingsService != null) {
        actions.add(new EditArtifactItem(selectedValues, myProject, mySettingsService));
      }
      String title = JavaCompilerBundle.message("popup.title.chosen.artifact.action", selectedValues.size());
      return new BaseListPopupStep<>(title, actions) {
        @NotNull
        @Override
        public String getTextFor(ArtifactActionItem value) {
          return value.getActionName();
        }

        @Override
        public PopupStep onChosen(ArtifactActionItem selectedValue, boolean finalChoice) {
          return doFinalStep(selectedValue);
        }
      };
    }
  }
}
