// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard;

import com.intellij.ide.actions.ImportModuleAction;
import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard;
import com.intellij.ide.util.newProjectWizard.SelectTemplateSettings;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.wizard.NewProjectWizardStep;
import com.intellij.ide.wizard.Step;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.projectRoots.SimpleJavaSdkType;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.LanguageLevelProjectExtensionImpl;
import com.intellij.openapi.roots.ui.configuration.actions.NewModuleAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportProvider;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.UIBundle;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author Dmitry Avdeev
 */
public abstract class ProjectWizardTestCase<T extends AbstractProjectWizard> extends HeavyPlatformTestCase {
  protected static final String DEFAULT_SDK = "default";
  protected T myWizard;
  @Nullable
  private Project myCreatedProject;
  private Sdk myOldDefaultProjectSdk;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    Project defaultProject = ProjectManager.getInstance().getDefaultProject();
    myOldDefaultProjectSdk = ProjectRootManager.getInstance(defaultProject).getProjectSdk();
    Sdk projectSdk = ProjectRootManager.getInstance(getProject()).getProjectSdk();
    for (final Sdk jdk : ProjectJdkTable.getInstance().getAllJdks()) {
      if (projectSdk != jdk) {
        ApplicationManager.getApplication().runWriteAction(() -> ProjectJdkTable.getInstance().removeJdk(jdk));
      }
    }
    ProjectTypeStep.resetGroupForTests();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      setWizard(null);
      if (myCreatedProject != null) {
        PlatformTestUtil.forceCloseProjectWithoutSaving(myCreatedProject);
        myCreatedProject = null;
      }
      ApplicationManager.getApplication().invokeAndWait(() -> {
        ApplicationManager.getApplication().runWriteAction(() -> {
          LanguageLevelProjectExtensionImpl extension =
            LanguageLevelProjectExtensionImpl.getInstanceImpl(ProjectManager.getInstance().getDefaultProject());
          extension.resetDefaults();
          ProjectRootManager.getInstance(ProjectManager.getInstance().getDefaultProject()).setProjectSdk(myOldDefaultProjectSdk);
          JavaAwareProjectJdkTableImpl.removeInternalJdkInTests();
        });
        SelectTemplateSettings.getInstance().setLastTemplate(null, null);
        // let vfs update pass
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
      });
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      ApplicationManager.getApplication().invokeAndWait(() -> {
        try {
          super.tearDown();
        }
        catch (Exception e) {
          addSuppressedException(e);
        }
      });
    }
  }

  void setWizard(@Nullable T wizard) {
    if (myWizard != null) {
      Disposer.dispose(myWizard.getDisposable());
    }
    myWizard = wizard;
  }

  protected Project createProjectFromTemplate(@NotNull String group, @Nullable String name, @Nullable Consumer<? super Step> adjuster)
    throws IOException {
    return createProject(step -> {
      setSelectedTemplate(step, group, name);
      if (adjuster != null) {
        adjuster.accept(step);
      }
    });
  }

  private Project createProjectFromWizard() {
    try {
      myCreatedProject = NewProjectUtil.createFromWizard(myWizard);
    }
    catch (Throwable e) {
      myCreatedProject = ContainerUtil.find(ProjectManager.getInstance().getOpenProjects(), project -> {
        return myWizard.getProjectName().equals(project.getName());
      });
      throw new RuntimeException(e);
    }
    assertNotNull(myCreatedProject);

    UIUtil.dispatchAllInvocationEvents();

    return myCreatedProject;
  }

  protected Project getCreatedProject() {
    return myCreatedProject;
  }

  protected @Nullable Module createModuleFromTemplate(String group, String name, @NotNull Project project, @Nullable Consumer<? super Step> adjuster)
    throws IOException {
    return createModule(project, step -> {
      setSelectedTemplate(step, group, name);
      if (adjuster != null) {
        adjuster.accept(step);
      }
    });
  }

  private Module createModuleFromWizard(@NotNull Project project) {
    return new NewModuleAction().createModuleFromWizard(project, null, myWizard);
  }

  private static void setSelectedTemplate(@NotNull Step step, @NotNull String group, @Nullable String name) {
    if (step instanceof ProjectTypeStep projectTypeStep && !projectTypeStep.setSelectedTemplate(group, name)) {
      throw new IllegalArgumentException(
        group + '/' + name + " template not found. " +
        "Available groups: " + projectTypeStep.availableTemplateGroupsToString()
      );
    }
    if (step instanceof ChooseTemplateStep) {
      if (name != null) {
        ChooseTemplateStep chooseTemplateStep = (ChooseTemplateStep)step;
        if (!chooseTemplateStep.setSelectedTemplate(name)) {
          throw new IllegalArgumentException(
            group + '/' + name + " template not found. " +
            "Available groups: " + chooseTemplateStep.getTemplateList().availableProjectTemplatesToString()
          );
        }
      }
    }
  }

  protected void cancelWizardRun() {
    throw new CancelWizardException();
  }

  private static class CancelWizardException extends RuntimeException {
  }

  private void runWizard(@Nullable Consumer<? super Step> adjuster) {
    while (true) {
      ModuleWizardStep currentStep = myWizard.getCurrentStepObject();
      if (adjuster != null) {
        try {
          adjuster.accept(currentStep);
        }
        catch (CancelWizardException e) {
          myWizard.doCancelAction();
          return;
        }
      }
      if (myWizard.isLast()) {
        break;
      }
      myWizard.doNextAction();
      if (currentStep == myWizard.getCurrentStepObject()) {
        throw new RuntimeException(currentStep + " is not validated");
      }
    }
    if (!myWizard.doFinishAction()) {
      throw new RuntimeException(myWizard.getCurrentStepObject() + " is not validated");
    }
  }

  protected void createWizard(@Nullable Project project) throws IOException {
    setWizard(createWizard(project, createTempDirectoryWithSuffix("new").toFile()));
    UIUtil.dispatchAllInvocationEvents(); // to make default selection applied
  }

  protected Project createProject(Consumer<? super Step> adjuster) throws IOException {
    createWizard(null);
    runWizard(adjuster);
    myWizard.disposeIfNeeded();
    return createProjectFromWizard();
  }

  protected Project createProjectFromTemplate(@NotNull Consumer<? super NewProjectWizardStep> adjuster) throws IOException {
    return createProjectFromTemplate(UIBundle.message("label.project.wizard.project.generator.name"), adjuster);
  }

  protected Project createProjectFromTemplate(@NotNull String group, @NotNull Consumer<? super NewProjectWizardStep> adjuster) throws IOException {
    return createProjectFromTemplate(group, null, step -> {
      var npwStep = getNewProjectWizardStep(step);
      if (npwStep != null) {
        adjuster.accept(npwStep);
      }
    });
  }

  protected Module createModule(@NotNull Project project, @NotNull Consumer<? super Step> adjuster) throws IOException {
    createWizard(project);
    runWizard(adjuster);
    myWizard.disposeIfNeeded();
    return createModuleFromWizard(project);
  }

  protected Module createModuleFromTemplate(@NotNull Project project, @NotNull Consumer<? super NewProjectWizardStep> adjuster) throws IOException {
    return createModuleFromTemplate(project, UIBundle.message("label.project.wizard.module.generator.name"), adjuster);
  }

  protected Module createModuleFromTemplate(
    @NotNull Project project,
    @NotNull String group,
    @NotNull Consumer<? super NewProjectWizardStep> adjuster
  ) throws IOException {
    return createModuleFromTemplate(group, null, project, step -> {
      var npwStep = getNewProjectWizardStep(step);
      if (npwStep != null) {
        adjuster.accept(npwStep);
      }
    });
  }

  protected @Nullable NewProjectWizardStep getNewProjectWizardStep(@NotNull Step step) {
    if (step instanceof ProjectTypeStep) {
      var moduleWizardStep = ((ProjectTypeStep)step).getCustomStep();
      assertInstanceOf(moduleWizardStep, NewProjectWizardStep.class);
      return (NewProjectWizardStep)moduleWizardStep;
    }
    return null;
  }

  protected T createWizard(Project project, File directory) {
    throw new RuntimeException();
  }

  protected void configureJdk() {
    ApplicationManager.getApplication().runWriteAction(() -> {
      addSdk(new SimpleJavaSdkType().createJdk(DEFAULT_SDK, SystemProperties.getJavaHome()));
      addSdk(new SimpleJavaSdkType().createJdk("_other", SystemProperties.getJavaHome()));

      LOG.debug("ProjectWizardTestCase.configureJdk:");
      LOG.debug(String.valueOf(Arrays.asList(ProjectJdkTable.getInstance().getAllJdks())));
    });
  }

  protected void addSdk(final Sdk sdk) {
    ApplicationManager.getApplication().runWriteAction(() -> ProjectJdkTable.getInstance().addJdk(sdk, getTestRootDisposable()));
  }

  protected Module importModuleFrom(ProjectImportProvider provider, String path) {
    return importFrom(path, getProject(), null, provider);
  }

  protected Module importProjectFrom(String path, Consumer<? super Step> adjuster, ProjectImportProvider... providers) {
    Module module = importFrom(path, null, adjuster, providers);
    if (module != null) {
      myCreatedProject = module.getProject();
    }
    return module;
  }

  private Module importFrom(String path,
                            @Nullable Project project,
                            Consumer<? super Step> adjuster,
                            ProjectImportProvider... providers) {
    return computeInWriteSafeContext(() -> doImportModule(path, project, adjuster, providers));
  }

  private Module doImportModule(String path, @Nullable Project project, Consumer<? super Step> adjuster, ProjectImportProvider[] providers) {
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
    assertNotNull("Can't find " + path, file);
    assertTrue(providers[0].canImport(file, project));

    //noinspection unchecked
    setWizard((T)ImportModuleAction.createImportWizard(project, null, file, providers));
    assertNotNull(myWizard);
    if (myWizard.getStepCount() > 0) {
      runWizard(adjuster);
    }
    return ContainerUtil.getFirstItem(ImportModuleAction.createFromWizard(project, myWizard));
  }

  private static <T> T computeInWriteSafeContext(Supplier<? extends T> supplier) {
    Ref<T> module = Ref.create();
    ApplicationManager.getApplication().invokeLater(() -> module.set(supplier.get()));
    UIUtil.dispatchAllInvocationEvents();
    return module.get();
  }

  protected Sdk createSdk(String name, SdkTypeId sdkType) {
    final Sdk sdk = ProjectJdkTable.getInstance().createSdk(name, sdkType);
    ApplicationManager.getApplication().runWriteAction(() -> ProjectJdkTable.getInstance().addJdk(sdk, getTestRootDisposable()));
    return sdk;
  }
}
