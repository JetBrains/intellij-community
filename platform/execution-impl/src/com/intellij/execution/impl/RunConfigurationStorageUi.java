// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl;

import com.intellij.configurationStore.Scheme_implKt;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfigurationVcsSupport;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.ui.panel.ComponentPanelBuilder;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.popup.PopupState;
import com.intellij.util.*;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import com.intellij.util.ui.UIUtil;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.*;

public final class RunConfigurationStorageUi {
  private static final Logger LOG = Logger.getInstance(RunConfigurationStorageUi.class);

  private static final Icon GEAR_WITH_DROPDOWN_ICON = LayeredIcon.layeredIcon(() -> new Icon[]{AllIcons.General.GearPlain, AllIcons.General.Dropdown});
  private static final Icon GEAR_WITH_DROPDOWN_DISABLED_ICON = LayeredIcon.layeredIcon(() -> new Icon[]{IconLoader.getDisabledIcon(AllIcons.General.GearPlain), IconLoader.getDisabledIcon(AllIcons.General.Dropdown)});
  private static final Icon GEAR_WITH_DROPDOWN_ERROR_ICON = LayeredIcon.layeredIcon(() -> new Icon[]{AllIcons.General.Error, AllIcons.General.Dropdown});

  private final JBCheckBox myStoreAsFileCheckBox;
  private final ActionButton myStoreAsFileGearButton;

  private final @NotNull Project myProject;
  private final @Nullable Runnable myOnModifiedRunnable;

  private RCStorageType myRCStorageTypeInitial;
  private @Nullable @SystemIndependent @NonNls String myFolderPathIfStoredInArbitraryFileInitial;

  private RCStorageType myRCStorageType;
  private @Nullable @SystemIndependent @NonNls String myFolderPathIfStoredInArbitraryFile;

  private @Nullable Boolean myDotIdeaStorageVcsIgnored = null; // used as cache; null means not initialized yet

  public RunConfigurationStorageUi(@NotNull Project project, @Nullable Runnable onModifiedRunnable) {
    if (project.isDefault()) LOG.error("Don't use RunConfigurationStorageUi for default project");

    myProject = project;
    myOnModifiedRunnable = onModifiedRunnable;

    myStoreAsFileCheckBox = new JBCheckBox(ExecutionBundle.message("run.configuration.store.as.project.file"));
    myStoreAsFileGearButton = createStoreAsFileGearButton();

    myStoreAsFileCheckBox.addActionListener(e -> {
      if (myStoreAsFileCheckBox.isSelected()) {
        setStorageTypeAndPathToTheBestPossibleState();
      }
      else {
        myRCStorageType = RCStorageType.Workspace;
        myFolderPathIfStoredInArbitraryFile = null;
      }

      if (myOnModifiedRunnable != null) {
        myOnModifiedRunnable.run();
      }

      myStoreAsFileGearButton.setEnabled(myStoreAsFileCheckBox.isSelected());
      if (myStoreAsFileCheckBox.isSelected()) {
        manageStorageFileLocation(null);
      }
    });
  }

  private @NotNull ActionButton createStoreAsFileGearButton() {
    PopupState<Balloon> state = PopupState.forBalloon();
    AnAction showStoragePathAction = new DumbAwareAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (!state.isRecentlyHidden()) manageStorageFileLocation(state);
      }
    };
    Presentation presentation = new Presentation(ExecutionBundle.message("run.configuration.manage.file.location"));
    presentation.setIcon(GEAR_WITH_DROPDOWN_ICON);
    presentation.setDisabledIcon(GEAR_WITH_DROPDOWN_DISABLED_ICON);
    return new ActionButton(showStoragePathAction, presentation, ActionPlaces.TOOLBAR, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
  }

  private void manageStorageFileLocation(@Nullable PopupState<Balloon> state) {
    Disposable balloonDisposable = Disposer.newDisposable();

    Function<String, String> pathToErrorMessage = path -> getErrorIfBadFolderPathForStoringInArbitraryFile(myProject, path);
    RunConfigurationStoragePopup popup =
      new RunConfigurationStoragePopup(myProject, getDotIdeaStoragePath(myProject), pathToErrorMessage, balloonDisposable);

    Balloon balloon = JBPopupFactory.getInstance().createBalloonBuilder(popup.getMainPanel())
      .setDialogMode(true)
      .setBorderInsets(JBUI.insets(20, 15, 10, 15))
      .setFillColor(UIUtil.getPanelBackground())
      .setHideOnAction(false)
      .setHideOnLinkClick(false)
      .setHideOnKeyOutside(false) // otherwise any keypress in file chooser hides the underlying balloon
      .setBlockClicksThroughBalloon(true)
      .setRequestFocus(true)
      .createBalloon();
    balloon.setAnimationEnabled(false);

    String path = myRCStorageType == RCStorageType.DotIdeaFolder
                  ? getDotIdeaStoragePath(myProject)
                  : StringUtil.notNullize(myFolderPathIfStoredInArbitraryFile);

    Set<String> pathsToSuggest = new LinkedHashSet<>();
    if (getErrorIfBadFolderPathForStoringInArbitraryFile(myProject, path) == null) {
      pathsToSuggest.add(path);
    }
    if (myRCStorageTypeInitial == RCStorageType.ArbitraryFileInProject && myFolderPathIfStoredInArbitraryFileInitial != null) {
      pathsToSuggest.add(myFolderPathIfStoredInArbitraryFileInitial);
    }
    pathsToSuggest.add(getDotIdeaStoragePath(myProject));
    pathsToSuggest.addAll(getFolderPathsWithinProjectWhereRunConfigurationsStored(myProject));

    popup.reset(path, pathsToSuggest, () -> balloon.hide());

    balloon.addListener(new JBPopupListener() {
      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        Disposer.dispose(balloonDisposable);

        String newPath = popup.getPath();
        if (!newPath.equals(path)) {
          applyChangedStoragePath(newPath);

          if (myOnModifiedRunnable != null) {
            myOnModifiedRunnable.run();
          }
        }
      }
    });

    if (state != null) state.prepareToShow(balloon);
    balloon.show(RelativePoint.getSouthOf(myStoreAsFileCheckBox), Balloon.Position.below);
  }

  private void applyChangedStoragePath(String newPath) {
    if (newPath.equals(getDotIdeaStoragePath(myProject))) {
      myRCStorageType = RCStorageType.DotIdeaFolder;
      myFolderPathIfStoredInArbitraryFile = null;
    }
    else {
      myRCStorageType = RCStorageType.ArbitraryFileInProject;
      myFolderPathIfStoredInArbitraryFile = newPath;
    }
    validatePath();
  }

  private void validatePath() {
    ReadAction.nonBlocking(this::checkPathAndGetErrorIcon)
      .expireWhen(() -> !myStoreAsFileGearButton.isShowing())
      .finishOnUiThread(ModalityState.defaultModalityState(), myStoreAsFileGearButton::setIcon)
      .submit(NonUrgentExecutor.getInstance());
  }

  private Icon checkPathAndGetErrorIcon() {
    if (myStoreAsFileCheckBox.isSelected() &&
        myRCStorageType == RCStorageType.ArbitraryFileInProject &&
        getErrorIfBadFolderPathForStoringInArbitraryFile(myProject, myFolderPathIfStoredInArbitraryFile) != null) {
      return GEAR_WITH_DROPDOWN_ERROR_ICON;
    }
    return GEAR_WITH_DROPDOWN_ICON;
  }

  private static @NonNls @NotNull String getFileNameByRCName(@NotNull String rcName) {
    return Scheme_implKt.getMODERN_NAME_CONVERTER().invoke(rcName) + ".run.xml";
  }

  @Contract("_,null -> !null")
  private static @Nullable String getErrorIfBadFolderPathForStoringInArbitraryFile(@NotNull Project project,
                                                                                   @Nullable @NonNls @SystemIndependent String path) {
    if (getDotIdeaStoragePath(project).equals(path)) return null; // that's ok

    if (StringUtil.isEmpty(path)) return ExecutionBundle.message("run.configuration.storage.folder.path.not.specified");
    if (path.endsWith("/.idea") || path.contains("/.idea/")) {
      return ExecutionBundle.message("run.configuration.storage.folder.dot.idea.forbidden", File.separator);
    }

    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
    if (file != null && !file.isDirectory()) return ExecutionBundle.message("run.configuration.storage.folder.path.expected");

    String folderName = PathUtil.getFileName(path);
    String parentPath = PathUtil.getParentPath(path);
    while (file == null && !parentPath.isEmpty()) {
      if (!PathUtil.isValidFileName(folderName)) {
        return ExecutionBundle.message("run.configuration.storage.folder.path.expected");
      }
      file = LocalFileSystem.getInstance().findFileByPath(parentPath);
      folderName = PathUtil.getFileName(parentPath);
      parentPath = PathUtil.getParentPath(parentPath);
    }

    if (file == null) return ExecutionBundle.message("run.configuration.storage.folder.not.within.project");
    if (!file.isDirectory()) return ExecutionBundle.message("run.configuration.storage.folder.path.expected");

    boolean isInContent = WorkspaceFileIndex.getInstance(project).isUrlInContent(VfsUtilCore.pathToUrl(path)) != ThreeState.NO;
    if (!isInContent) {
      if (ProjectFileIndex.getInstance(project).getContentRootForFile(file, false) == null) {
        return ExecutionBundle.message("run.configuration.storage.folder.not.within.project");
      }
      else {
        return ExecutionBundle.message("run.configuration.storage.folder.in.excluded.root");
      }
    }

    return null; // ok
  }

  /**
   * @return full path to .idea/runConfigurations folder (for directory-based projects) or full path to the project.ipr file (for file-based projects)
   */
  private static @NonNls @NotNull String getDotIdeaStoragePath(@NotNull Project project) {
    // notNullize is to make inspections happy. Paths can't be null for non-default project
    return ProjectKt.isDirectoryBased(project)
           ? RunManagerImpl.getInstanceImpl(project).getDotIdeaRunConfigurationsPath$intellij_platform_execution_impl()
           : StringUtil.notNullize(project.getProjectFilePath());
  }

  private void setStorageTypeAndPathToTheBestPossibleState() {
    // all that tricky logic, see the flowchart from the issue description https://youtrack.jetbrains.com/issue/UX-1126

    // 1. If this RC had been shared before Run Configurations dialog was opened - use the state that was used before.
    // This handles the case when user opens shared RC for editing and clicks the 'Save to file' check box two times.
    if (myRCStorageTypeInitial == RCStorageType.DotIdeaFolder) {
      myRCStorageType = RCStorageType.DotIdeaFolder;
      myFolderPathIfStoredInArbitraryFile = null;
      return;
    }

    if (myRCStorageTypeInitial == RCStorageType.ArbitraryFileInProject) {
      myRCStorageType = RCStorageType.ArbitraryFileInProject;
      myFolderPathIfStoredInArbitraryFile = StringUtil.notNullize(myFolderPathIfStoredInArbitraryFileInitial);
      return;
    }

    // 2. For IPR-based projects keep using project.ipr file to store RCs by default
    if (!ProjectKt.isDirectoryBased(myProject)) {
      myRCStorageType = RCStorageType.DotIdeaFolder;
      myFolderPathIfStoredInArbitraryFile = null;
      return;
    }

    // Rider prefers project_base_dir/.run/ folder to .idea/runConfigurations/
    if (!PlatformUtils.isRider()) {
      // 3. If the project is not under VCS, keep using .idea/runConfigurations
      RunConfigurationVcsSupport vcsSupport = myProject.getService(RunConfigurationVcsSupport.class);
      if (!vcsSupport.hasActiveVcss(myProject)) {
        myRCStorageType = RCStorageType.DotIdeaFolder;
        myFolderPathIfStoredInArbitraryFile = null;
        return;
      }

      // 4. If .idea/runConfigurations is not excluded from VCS (e.g. not in .gitignore), then use it
      if (!isDotIdeaStorageVcsIgnored(vcsSupport)) {
        myRCStorageType = RCStorageType.DotIdeaFolder;
        myFolderPathIfStoredInArbitraryFile = null;
        return;
      }
    }

    // notNullize is to make inspections happy. Paths can't be null for non-default project
    VirtualFile baseDir = LocalFileSystem.getInstance().findFileByPath(StringUtil.notNullize(myProject.getBasePath()));
    LOG.assertTrue(baseDir != null);

    // 5. If project base dir is not within project content, use .idea/runConfigurations
    // In Rider baseDir always not in the project content by design after migration to the new workspace model.
    if (!PlatformUtils.isRider() && !ProjectFileIndex.getInstance(myProject).isInContent(baseDir)) {
      myRCStorageType = RCStorageType.DotIdeaFolder;
      myFolderPathIfStoredInArbitraryFile = null;
      return;
    }

    // 6. If there are other RCs stored in arbitrary files (and all in the same folder) - suggest that folder
    Collection<String> otherFolders = getFolderPathsWithinProjectWhereRunConfigurationsStored(myProject);
    if (otherFolders.size() == 1) {
      myRCStorageType = RCStorageType.ArbitraryFileInProject;
      myFolderPathIfStoredInArbitraryFile = otherFolders.iterator().next();
      return;
    }

    // default is .../project_base_dir/.run/ folder
    myRCStorageType = RCStorageType.ArbitraryFileInProject;
    myFolderPathIfStoredInArbitraryFile = baseDir.getPath() + "/.run";
  }

  private boolean isDotIdeaStorageVcsIgnored(RunConfigurationVcsSupport vcsSupport) {
    if (myDotIdeaStorageVcsIgnored == null) {
      myDotIdeaStorageVcsIgnored = vcsSupport.isDirectoryVcsIgnored(myProject, getDotIdeaStoragePath(myProject));
    }
    return myDotIdeaStorageVcsIgnored.booleanValue();
  }

  private static Collection<String> getFolderPathsWithinProjectWhereRunConfigurationsStored(@NotNull Project project) {
    Set<String> result = new HashSet<>();
    for (RunnerAndConfigurationSettings settings : RunManager.getInstance(project).getAllSettings()) {
      String filePath = settings.getPathIfStoredInArbitraryFileInProject();
      // two conditions on the next line are effectively equivalent, this is to make inspections happy
      if (settings.isStoredInArbitraryFileInProject() && filePath != null) {
        result.add(PathUtil.getParentPath(filePath));
      }
    }
    return result;
  }

  public JPanel createComponent() {
    return FormBuilder.createFormBuilder().setFormLeftIndent(10).setHorizontalGap(0)
      .addLabeledComponent(myStoreAsFileCheckBox, myStoreAsFileGearButton)
      .getPanel();
  }

  public void addStoreAsFileCheckBoxListener(ActionListener listener) {
    myStoreAsFileCheckBox.addActionListener(listener);
  }

  public boolean isStoredInFile() {
    return myRCStorageType == RCStorageType.DotIdeaFolder || myRCStorageType == RCStorageType.ArbitraryFileInProject;
  }

  public boolean isModified() {
    if (myRCStorageType != myRCStorageTypeInitial) return true;
    if (myRCStorageType == RCStorageType.ArbitraryFileInProject &&
        !Objects.equals(myFolderPathIfStoredInArbitraryFileInitial, myFolderPathIfStoredInArbitraryFile)) {
      return true;
    }
    return false;
  }

  public void reset(@NotNull RunnerAndConfigurationSettings settings) {
    boolean isManagedRunConfiguration = settings.getConfiguration().getType().isManaged();

    myRCStorageType = settings.isStoredInArbitraryFileInProject()
                      ? RCStorageType.ArbitraryFileInProject
                      : settings.isStoredInDotIdeaFolder()
                        ? RCStorageType.DotIdeaFolder
                        : RCStorageType.Workspace;
    myFolderPathIfStoredInArbitraryFile = PathUtil.getParentPath(StringUtil.notNullize(settings.getPathIfStoredInArbitraryFileInProject()));

    myRCStorageTypeInitial = myRCStorageType;
    myFolderPathIfStoredInArbitraryFileInitial = myFolderPathIfStoredInArbitraryFile;

    myStoreAsFileCheckBox.setEnabled(isManagedRunConfiguration);
    myStoreAsFileCheckBox.setSelected(myRCStorageType == RCStorageType.DotIdeaFolder ||
                                      myRCStorageType == RCStorageType.ArbitraryFileInProject);
    myStoreAsFileGearButton.setVisible(isManagedRunConfiguration);
    myStoreAsFileGearButton.setEnabled(myStoreAsFileCheckBox.isSelected());
    validatePath();
  }

  public void apply(@NotNull RunnerAndConfigurationSettings settings) {
    apply(settings, true);
  }

  public void apply(@NotNull RunnerAndConfigurationSettings settings, boolean checkPathValidity) {
    switch (myRCStorageType) {
      case Workspace -> settings.storeInLocalWorkspace();
      case DotIdeaFolder -> settings.storeInDotIdeaFolder();
      case ArbitraryFileInProject -> {
        if (checkPathValidity && getErrorIfBadFolderPathForStoringInArbitraryFile(myProject, myFolderPathIfStoredInArbitraryFile) != null) {
          // don't apply incorrect UI to the model
        }
        else {
          // not sure the 'Template' prefix of the 'Template XXX.run.xml' file name should be localized.
          String name = settings.isTemplate() ? "Template " + settings.getType().getDisplayName() : settings.getName();
          String fileName = getFileNameByRCName(name);
          settings.storeInArbitraryFileInProject(myFolderPathIfStoredInArbitraryFile + "/" + fileName);
        }
      }
      default -> throw new IllegalStateException("Unexpected value: " + myRCStorageType);
    }
  }

  private static final class RunConfigurationStoragePopup {
    private final JPanel myMainPanel;
    private final ComboBox<String> myPathComboBox;

    private final @NonNls @SystemIndependent String myDotIdeaStoragePath;

    private Runnable myClosePopupAction;

    RunConfigurationStoragePopup(@NotNull Project project,
                                 @NotNull String dotIdeaStoragePath,
                                 @NotNull Function<? super String, @NlsContexts.DialogMessage String> pathToErrorMessage,
                                 @NotNull Disposable uiDisposable) {
      myDotIdeaStoragePath = dotIdeaStoragePath;
      myPathComboBox = createPathComboBox(project, uiDisposable);

      ComponentValidator validator = new ComponentValidator(uiDisposable);
      JTextComponent comboBoxEditorComponent = (JTextComponent)myPathComboBox.getEditor().getEditorComponent();
      validator.withValidator(() -> {
        String errorMessage = pathToErrorMessage.fun(getPath());
        return errorMessage != null ? new ValidationInfo(errorMessage, myPathComboBox) : null;
      })
        .andRegisterOnDocumentListener(comboBoxEditorComponent)
        .installOn(comboBoxEditorComponent);

      ComponentPanelBuilder builder = UI.PanelFactory.panel(myPathComboBox)
        .withLabel(ExecutionBundle.message("run.configuration.store.in")).moveLabelOnTop();
      JPanel comboBoxPanel = builder.createPanel();

      JButton doneButton = new JButton(ExecutionBundle.message("run.configuration.done.button"));
      doneButton.addActionListener(e -> myClosePopupAction.run());
      JPanel doneButtonPanel = new JPanel(new BorderLayout());
      doneButtonPanel.add(doneButton, BorderLayout.EAST);

      myMainPanel = FormBuilder.createFormBuilder()
        .addComponent(comboBoxPanel)
        .addComponent(doneButtonPanel)
        .getPanel();

      myMainPanel.setFocusCycleRoot(true);
      myMainPanel.setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());

      // need to handle Enter keypress, otherwise Enter closes the main Run Configurations dialog.
      // Escape should also be handled manually because setHideOnKeyOutside(false) is set for this balloon.
      DumbAwareAction.create(e -> {
        if (myPathComboBox.isPopupVisible()) {
          myPathComboBox.setPopupVisible(false);
        }
        else {
          validator.updateInfo(null);
          myClosePopupAction.run();
        }
      }).registerCustomShortcutSet(new CompositeShortcutSet(CommonShortcuts.ENTER, CommonShortcuts.ESCAPE), myMainPanel, uiDisposable);
    }

    private @NotNull ComboBox<String> createPathComboBox(@NotNull Project project, @NotNull Disposable uiDisposable) {
      var comboBox = new ComboBox<String>(JBUI.scale(500));
      comboBox.setEditable(true);

      // `chooseFiles` is set to `true` to be able to select 'project.ipr' file in IPR-based projects; other files are not selectable
      var descriptor = new FileChooserDescriptor(true, true, false, false, false, false) {
        @Override
        public boolean isFileSelectable(@Nullable VirtualFile file) {
          if (file == null) return false;
          if (file.getPath().equals(myDotIdeaStoragePath)) return true;
          return file.isDirectory() &&
                 super.isFileSelectable(file) &&
                 !file.getPath().endsWith("/.idea") &&
                 !file.getPath().contains("/.idea/") &&
                 ReadAction.compute(() -> ProjectFileIndex.getInstance(project).isInContent(file));
        }
      };

      var selectFolderAction = new BrowseFolderRunnable<>(project, descriptor, comboBox, TextComponentAccessor.STRING_COMBOBOX_WHOLE_TEXT);
      comboBox.initBrowsableEditor(selectFolderAction, uiDisposable);
      return comboBox;
    }

    JPanel getMainPanel() {
      return myMainPanel;
    }

    void reset(@NotNull @SystemIndependent String folderPath, Collection<String> pathsToSuggest, @NotNull Runnable closePopupAction) {
      myPathComboBox.setSelectedItem(FileUtil.toSystemDependentName(folderPath));

      for (String s : pathsToSuggest) {
        myPathComboBox.addItem(FileUtil.toSystemDependentName(s));
      }

      myClosePopupAction = closePopupAction;
    }

    @NotNull @SystemIndependent String getPath() {
      return UriUtil.trimTrailingSlashes(FileUtil.toSystemIndependentName(myPathComboBox.getEditor().getItem().toString().trim()));
    }
  }

  private enum RCStorageType {Workspace, DotIdeaFolder, ArbitraryFileInProject}
}
