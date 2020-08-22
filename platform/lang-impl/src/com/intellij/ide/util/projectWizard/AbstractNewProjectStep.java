// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard;

import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.ide.util.projectWizard.actions.ProjectSpecificAction;
import com.intellij.idea.ActionsBundle;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.impl.welcomeScreen.NewWelcomeScreen;
import com.intellij.platform.*;
import com.intellij.platform.templates.ArchivedTemplatesFactory;
import com.intellij.platform.templates.LocalArchivedTemplate;
import com.intellij.platform.templates.TemplateProjectDirectoryGenerator;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.intellij.platform.ProjectTemplatesFactory.CUSTOM_GROUP;

public abstract class AbstractNewProjectStep<T> extends DefaultActionGroup implements DumbAware {
  static final ExtensionPointName<DirectoryProjectGenerator<?>> EP_NAME = new ExtensionPointName<>("com.intellij.directoryProjectGenerator");

  private static final Logger LOG = Logger.getInstance(AbstractNewProjectStep.class);
  private final Customization<T> myCustomization;

  protected AbstractNewProjectStep(@NotNull Customization<T> customization) {
    super(Presentation.NULL_STRING, true);
    myCustomization = customization;
    updateActions();
    EP_NAME.addChangeListener(() -> updateActions(), null);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    NewWelcomeScreen.updateNewProjectIconIfWelcomeScreen(e);
    updateActions();
  }

  protected void updateActions() {
    removeAll();
    AbstractCallback<T> callback = myCustomization.createCallback();
    ProjectSpecificAction projectSpecificAction = myCustomization.createProjectSpecificAction(callback);
    addProjectSpecificAction(projectSpecificAction);

    List<DirectoryProjectGenerator<?>> generators = myCustomization.getProjectGenerators();
    addAll(myCustomization.getActions(generators, callback));
    if (!myCustomization.showUserDefinedProjects()) {
      return;
    }

    ProjectTemplate[] templates = new ArchivedTemplatesFactory().createTemplates(CUSTOM_GROUP, null);
    List<DirectoryProjectGenerator<?>> projectGenerators;
    if (templates.length == 0) {
      projectGenerators = Collections.emptyList();
    }
    else {
      projectGenerators = new ArrayList<>(templates.length);
      for (ProjectTemplate template : templates) {
        projectGenerators.add(new TemplateProjectDirectoryGenerator<>((LocalArchivedTemplate)template));
      }
    }

    addAll(myCustomization.getActions(projectGenerators, callback));
  }

  protected void addProjectSpecificAction(@NotNull final ProjectSpecificAction projectSpecificAction) {
    addAll(projectSpecificAction.getChildren(null));
  }

  protected static abstract class Customization<T> {
    @NotNull
    protected ProjectSpecificAction createProjectSpecificAction(@NotNull final AbstractCallback<T> callback) {
      DirectoryProjectGenerator<T> emptyProjectGenerator = createEmptyProjectGenerator();
      return new ProjectSpecificAction(emptyProjectGenerator, createProjectSpecificSettingsStep(emptyProjectGenerator, callback));
    }

    @NotNull
    protected abstract AbstractCallback<T> createCallback();

    @NotNull
    protected abstract DirectoryProjectGenerator<T> createEmptyProjectGenerator();

    @NotNull
    protected abstract ProjectSettingsStepBase<T> createProjectSpecificSettingsStep(@NotNull DirectoryProjectGenerator<T> projectGenerator,
                                                                                    @NotNull AbstractCallback<T> callback);

    protected @NotNull List<DirectoryProjectGenerator<?>> getProjectGenerators() {
      return EP_NAME.getExtensionList();
    }

    public AnAction[] getActions(@NotNull List<DirectoryProjectGenerator<?>> generators, @NotNull AbstractCallback<T> callback) {
      List<AnAction> actions = new ArrayList<>();
      for (DirectoryProjectGenerator<?> projectGenerator : generators) {
        try {
          //noinspection unchecked
          actions.addAll(Arrays.asList(getActions((DirectoryProjectGenerator<T>)projectGenerator, callback)));
        }
        catch (Throwable throwable) {
          LOG.error("Broken project generator " + projectGenerator, throwable);
        }
      }
      return actions.toArray(AnAction.EMPTY_ARRAY);
    }

    public AnAction @NotNull [] getActions(@NotNull DirectoryProjectGenerator<T> generator, @NotNull AbstractCallback<T> callback) {
      if (shouldIgnore(generator)) {
        return AnAction.EMPTY_ARRAY;
      }

      ProjectSettingsStepBase<T> step;
      if (generator instanceof CustomStepProjectGenerator) {
        //noinspection unchecked,CastConflictsWithInstanceof
        step = (ProjectSettingsStepBase<T>)((CustomStepProjectGenerator<T>)generator).createStep(generator, callback);
      }
      else {
        //noinspection unchecked
        step = createProjectSpecificSettingsStep(generator, callback);
      }

      ProjectSpecificAction projectSpecificAction = new ProjectSpecificAction(generator, step);
      return projectSpecificAction.getChildren(null);
    }

    protected boolean shouldIgnore(@NotNull DirectoryProjectGenerator<?> generator) {
      return generator instanceof HideableProjectGenerator && ((HideableProjectGenerator)generator).isHidden();
    }

    public boolean showUserDefinedProjects() {
      return false;
    }
  }

  public static class AbstractCallback<T> implements PairConsumer<ProjectSettingsStepBase<T>, ProjectGeneratorPeer<T>> {
    @Override
    public void consume(@Nullable ProjectSettingsStepBase<T> settings, @NotNull ProjectGeneratorPeer<T> projectGeneratorPeer) {
      if (settings == null) {
        return;
      }

      // todo projectToClose should be passed from calling action, this is just a quick workaround
      IdeFrame frame = IdeFocusManager.getGlobalInstance().getLastFocusedFrame();
      Project projectToClose = frame != null ? frame.getProject() : null;
      DirectoryProjectGenerator<T> generator = settings.getProjectGenerator();
      T actualSettings = projectGeneratorPeer.getSettings();
      doGenerateProject(projectToClose, settings.getProjectLocation(), generator, actualSettings);
    }
  }

  public static <T> Project doGenerateProject(@Nullable Project projectToClose,
                                              @NotNull String locationString,
                                              @Nullable DirectoryProjectGenerator<T> generator,
                                              @NotNull T settings) {
    Path location = Paths.get(locationString);
    try {
      Files.createDirectories(location);
    }
    catch (IOException e) {
      LOG.warn(e);
      String message = ActionsBundle.message("action.NewDirectoryProject.cannot.create.dir", location.toString());
      Messages.showErrorDialog(projectToClose, message, ActionsBundle.message("action.NewDirectoryProject.title"));
      return null;
    }

    VirtualFile baseDir = WriteAction.compute(() -> LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(location.toString())));
    if (baseDir == null) {
      LOG.error("Couldn't find '" + location + "' in VFS");
      return null;
    }
    VfsUtil.markDirtyAndRefresh(false, true, true, baseDir);

    if (baseDir.getChildren().length > 0) {
      String title = ActionsBundle.message("action.NewDirectoryProject.not.empty.dialog.title");
      String message = ActionsBundle.message("action.NewDirectoryProject.not.empty.dialog.text", location.toString());
      String yesText = ActionsBundle.message("action.NewDirectoryProject.not.empty.dialog.create.new");
      String noText = ActionsBundle.message("action.NewDirectoryProject.not.empty.dialog.open.existing");
      int result = Messages.showYesNoDialog(projectToClose, message, title, yesText, noText, Messages.getQuestionIcon());
      if (result == Messages.NO) {
        return PlatformProjectOpenProcessor.doOpenProject(location, new OpenProjectTask());
      }
    }

    RecentProjectsManager.getInstance().setLastProjectCreationLocation(location.getParent());

    if (generator instanceof TemplateProjectDirectoryGenerator) {
      ((TemplateProjectDirectoryGenerator<?>)generator).generateProject(baseDir.getName(), locationString);
    }

    OpenProjectTask options = OpenProjectTask.newProjectFromWizardAndRunConfigurators(projectToClose, /* isRefreshVfsNeeded = */ false);
    Project project = ProjectManagerEx.getInstanceEx().openProject(location, options);
    if (project != null && generator != null) {
      generator.generateProject(project, baseDir, settings, ModuleManager.getInstance(project).getModules()[0]);
    }
    logProjectGeneratedEvent(generator);

    return project;
  }

  private static void logProjectGeneratedEvent(@Nullable DirectoryProjectGenerator<?> generator) {
    FeatureUsageData data = new FeatureUsageData();
    if (generator != null) {
      data.addData("generator_id", generator.getClass().getName());
      data.addPluginInfo(PluginInfoDetectorKt.getPluginInfo(generator.getClass()));
    }

    FUCounterUsageLogger.getInstance().logEvent("new.project.wizard", "project.generated", data);
  }
}