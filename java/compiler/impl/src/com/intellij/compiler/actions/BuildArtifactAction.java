/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.compiler.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.impl.compiler.ArtifactCompileScope;
import com.intellij.packaging.impl.compiler.ArtifactsWorkspaceSettings;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.*;

/**
 * @author nik
 */
public class BuildArtifactAction extends AnAction {
  public BuildArtifactAction() {
    super("Build Artifacts...", "Select and build artifacts configured in the project", null);
  }
  @Override
  public void update(AnActionEvent e) {
    final Project project = getEventProject(e);
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(project != null && !ArtifactUtil.getArtifactWithOutputPaths(project).isEmpty());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = getEventProject(e);
    if (project == null) return;

    final List<Artifact> artifacts = ArtifactUtil.getArtifactWithOutputPaths(project);
    if (artifacts.isEmpty()) return;

    final Artifact first = artifacts.get(0);
    List<ArtifactPopupItem> items = new ArrayList<ArtifactPopupItem>();
    if (artifacts.size() > 1) {
      items.add(0, new ArtifactPopupItem(null, "All Artifacts", EmptyIcon.ICON_16));
    }
    final Artifact selectedArtifact = ContainerUtil.getFirstItem(ArtifactsWorkspaceSettings.getInstance(project).getArtifactsToBuild());
    ArtifactPopupItem selectedItem = null;
    for (Artifact artifact : artifacts) {
      final ArtifactPopupItem item = new ArtifactPopupItem(artifact, artifact.getName(), artifact.getArtifactType().getIcon());
      if (artifact.equals(selectedArtifact)) {
        selectedItem = item;
      }
      items.add(item);
    }
    
    final ProjectSettingsService projectSettingsService = ProjectSettingsService.getInstance(project);
    final ArtifactAwareProjectSettingsService settingsService = projectSettingsService instanceof ArtifactAwareProjectSettingsService ? (ArtifactAwareProjectSettingsService)projectSettingsService : null;
    
    final ChooseArtifactStep step = new ChooseArtifactStep(items, first, project, settingsService);
    if (selectedItem != null) {
      step.setDefaultOptionIndex(items.indexOf(selectedItem));
    }
    
    final ListPopupImpl popup = (ListPopupImpl)JBPopupFactory.getInstance().createListPopup(step);
    final KeyStroke editKeyStroke = KeymapUtil.getKeyStroke(CommonShortcuts.getEditSource());
    if (settingsService != null && editKeyStroke != null) {
      popup.registerAction("editArtifact", editKeyStroke, new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          final ArtifactPopupItem item = (ArtifactPopupItem)popup.getSelectedValue();
          popup.cancel();
          settingsService.openArtifactSettings(item.getArtifact());
        }
      });
    }
    popup.showCenteredInCurrentWindow(project);
  }

  protected static void doBuild(@NotNull Project project, final @NotNull ArtifactPopupItem item, boolean rebuild) {
    final List<Artifact> artifacts = item.getArtifacts(project);
    final CompileScope scope = ArtifactCompileScope.createArtifactsScope(project, artifacts);
    ArtifactsWorkspaceSettings.getInstance(project).setArtifactsToBuild(ContainerUtil.createMaybeSingletonList(item.getArtifact()));
    if (!rebuild) {
      CompilerManager.getInstance(project).make(scope, null);
    }
    else {
      CompilerManager.getInstance(project).compile(scope, null);
    }
  }

  private static class BuildArtifactItem extends ArtifactActionItem {
    private BuildArtifactItem(ArtifactPopupItem item, Project project) {
      super(item, project, "Build");
    }

    @Override
    public void run() {
      doBuild(myProject, myArtifactPopupItem, false);
    }
  }

  private static class CleanArtifactItem extends ArtifactActionItem {
    private CleanArtifactItem(@NotNull ArtifactPopupItem item, @NotNull Project project) {
      super(item, project, "Clean");
    }

    @Override
    public void run() {
      Set<VirtualFile> parents = new HashSet<VirtualFile>();
      final VirtualFile[] roots = ProjectRootManager.getInstance(myProject).getContentSourceRoots();
      for (VirtualFile root : roots) {
        VirtualFile parent = root;
        while (parent != null && !parents.contains(parent)) {
          parents.add(parent);
          parent = parent.getParent();
        }
      }

      Map<String, String> outputPathContainingSourceRoots = new HashMap<String, String>();
      final List<File> files = new ArrayList<File>();
      for (Artifact artifact : myArtifactPopupItem.getArtifacts(myProject)) {
        String outputPath = artifact.getOutputFilePath();
        if (outputPath != null) {
          files.add(new File(FileUtil.toSystemDependentName(outputPath)));
          final VirtualFile outputFile = LocalFileSystem.getInstance().findFileByPath(outputPath);
          if (parents.contains(outputFile)) {
            outputPathContainingSourceRoots.put(artifact.getName(), outputPath);
          }
        }
      }

      if (!outputPathContainingSourceRoots.isEmpty()) {
        final String message;
        if (outputPathContainingSourceRoots.size() == 1 && outputPathContainingSourceRoots.values().size() == 1) {
          final String name = ContainerUtil.getFirstItem(outputPathContainingSourceRoots.keySet());
          final String output = outputPathContainingSourceRoots.get(name);
          message = "The output directory '" + output + "' of '" + name + "' artifact contains source roots of the project. Do you want to continue and clear it?";
        }
        else {
          StringBuilder info = new StringBuilder();
          for (String name : outputPathContainingSourceRoots.keySet()) {
            info.append(" '").append(name).append("' artifact ('").append(outputPathContainingSourceRoots.get(name)).append("')\n");
          }
          message = "The output directories of the following artifacts contains source roots:\n" +
                    info + "Do you want to continue and clear these directories?";
        }
        final int answer = Messages.showYesNoDialog(myProject, message, "Clean Artifacts", null);
        if (answer != 0) {
          return;
        }
      }

      new Task.Backgroundable(myProject, "Cleaning artifacts...", true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          for (File file : files) {
            indicator.checkCanceled();
            FileUtil.delete(file);
          }
          LocalFileSystem.getInstance().refreshIoFiles(files, true, true, null);
        }
      }.queue();
    }
  }

  private static class RebuildArtifactItem extends ArtifactActionItem {
    private RebuildArtifactItem(ArtifactPopupItem item, Project project) {
      super(item, project, "Rebuild");
    }

    @Override
    public void run() {
      doBuild(myProject, myArtifactPopupItem, true);
    }
  }

  private static class EditArtifactItem extends ArtifactActionItem {
    private final ArtifactAwareProjectSettingsService mySettingsService;

    private EditArtifactItem(ArtifactPopupItem item, Project project, final ArtifactAwareProjectSettingsService projectSettingsService) {
      super(item, project, "Edit...");
      mySettingsService = projectSettingsService;
    }

    @Override
    public void run() {
      mySettingsService.openArtifactSettings(myArtifactPopupItem.getArtifact());
    }
  }

  private static abstract class ArtifactActionItem implements Runnable {
    protected final ArtifactPopupItem myArtifactPopupItem;
    protected final Project myProject;
    private String myActionName;

    protected ArtifactActionItem(@NotNull ArtifactPopupItem item, @NotNull Project project, @NotNull String name) {
      myArtifactPopupItem = item;
      myProject = project;
      myActionName = name;
    }

    public String getActionName() {
      return myActionName;
    }
  }

  private static class ArtifactPopupItem {
    @Nullable private final Artifact myArtifact;
    private final String myText;
    private final Icon myIcon;

    private ArtifactPopupItem(Artifact artifact, String text, Icon icon) {
      myArtifact = artifact;
      myText = text;
      myIcon = icon;
    }

    @Nullable
    public Artifact getArtifact() {
      return myArtifact;
    }

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
  
  private static class ChooseArtifactStep extends BaseListPopupStep<ArtifactPopupItem> {
    private final Artifact myFirst;
    private final Project myProject;
    private ArtifactAwareProjectSettingsService mySettingsService;

    public ChooseArtifactStep(List<ArtifactPopupItem> artifacts,
                              Artifact first,
                              Project project, final ArtifactAwareProjectSettingsService settingsService) {
      super("Build Artifact", artifacts);
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
    public boolean hasSubstep(ArtifactPopupItem selectedValue) {
      return true;
    }

    @Override
    public ListSeparator getSeparatorAbove(ArtifactPopupItem value) {
      return myFirst.equals(value.getArtifact()) ? new ListSeparator() : null;
    }

    @Override
    public PopupStep onChosen(final ArtifactPopupItem selectedValue, boolean finalChoice) {
      if (finalChoice) {
        return doFinalStep(new Runnable() {
          @Override
          public void run() {
            doBuild(myProject, selectedValue, false);
          }
        });
      }
      final List<ArtifactActionItem> actions = new ArrayList<ArtifactActionItem>();
      actions.add(new BuildArtifactItem(selectedValue, myProject));
      actions.add(new RebuildArtifactItem(selectedValue, myProject));
      actions.add(new CleanArtifactItem(selectedValue, myProject));
      if (mySettingsService != null) {
        actions.add(new EditArtifactItem(selectedValue, myProject, mySettingsService));
      }
      return new BaseListPopupStep<ArtifactActionItem>("Action", actions) {
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
