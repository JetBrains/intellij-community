// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard;

import com.intellij.ide.actions.ImportModuleAction;
import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard;
import com.intellij.ide.util.newProjectWizard.SelectTemplateSettings;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.wizard.Step;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.LaterInvocator;
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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportProvider;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.Consumer;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
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
      if (myWizard != null) {
        Disposer.dispose(myWizard.getDisposable());
        myWizard = null;
      }
      if (myCreatedProject != null) {
        PlatformTestUtil.forceCloseProjectWithoutSaving(myCreatedProject);
        myCreatedProject = null;
      }
      ApplicationManager.getApplication().runWriteAction(() -> {
        LanguageLevelProjectExtensionImpl extension =
          LanguageLevelProjectExtensionImpl.getInstanceImpl(ProjectManager.getInstance().getDefaultProject());
        extension.resetDefaults();
        ProjectRootManager.getInstance(ProjectManager.getInstance().getDefaultProject()).setProjectSdk(myOldDefaultProjectSdk);
        JavaAwareProjectJdkTableImpl.removeInternalJdkInTests();
      });
      SelectTemplateSettings.getInstance().setLastTemplate(null, null);
      UIUtil.dispatchAllInvocationEvents(); // let vfs update pass
      LaterInvocator.dispatchPendingFlushes();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  protected Project createProjectFromTemplate(@NotNull String group, @Nullable String name, @Nullable Consumer<? super Step> adjuster) throws IOException {
    runWizard(group, name, null, adjuster);
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

    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    assertEquals(Arrays.asList(projects).toString(), 2, projects.length);
    return myCreatedProject;
  }

  @Nullable
  protected Module createModuleFromTemplate(String group, String name, @Nullable Consumer<? super Step> adjuster) throws IOException {
    return createModuleFromTemplate(group, name, getProject(), adjuster);
  }

  @Nullable
  protected Module createModuleFromTemplate(String group, String name, @NotNull Project project, @Nullable Consumer<? super Step> adjuster)
    throws IOException {
    runWizard(group, name, project, adjuster);
    return createModuleFromWizard(project);
  }

  private Module createModuleFromWizard(@NotNull Project project) {
    return new NewModuleAction().createModuleFromWizard(project, null, myWizard);
  }

  private void runWizard(@NotNull String group,
                         @Nullable final String name,
                         @Nullable Project project,
                         @Nullable final Consumer<? super Step> adjuster) throws IOException {
    createWizard(project);
    ProjectTypeStep step = (ProjectTypeStep)myWizard.getCurrentStepObject();
    if (!step.setSelectedTemplate(group, name)) {
      throw new IllegalArgumentException(group + '/' + name + " template not found. Available groups: " + step.availableTemplateGroupsToString());
    }

    runWizard(step1 -> {
      if (name != null && step1 instanceof ChooseTemplateStep) {
        ((ChooseTemplateStep)step1).setSelectedTemplate(name);
      }
      if (adjuster != null) {
        adjuster.consume(step1);
      }
    });
  }

  protected void cancelWizardRun() {
    throw new CancelWizardException();
  }

  private static class CancelWizardException extends RuntimeException {
  }

  private void runWizard(@Nullable java.util.function.Consumer<? super Step> adjuster) {
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
    myWizard.doFinishAction();
  }

  protected void createWizard(@Nullable Project project) throws IOException {
    File directory = FileUtil.createTempDirectory(getName(), "new", false);
    myFilesToDelete.add(directory);
    if (myWizard != null) {
      Disposer.dispose(myWizard.getDisposable());
      myWizard = null;
    }
    myWizard = createWizard(project, directory);
    UIUtil.dispatchAllInvocationEvents(); // to make default selection applied
  }

  protected Project createProject(java.util.function.Consumer<? super Step> adjuster) throws IOException {
    createWizard(null);
    runWizard(adjuster);
    myCreatedProject = NewProjectUtil.createFromWizard(myWizard);
    return myCreatedProject;
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

  protected Module importProjectFrom(String path, java.util.function.Consumer<? super Step> adjuster, ProjectImportProvider... providers) {
    Module module = importFrom(path, null, adjuster, providers);
    if (module != null) {
      myCreatedProject = module.getProject();
    }
    return module;
  }

  private Module importFrom(String path,
                            @Nullable Project project,
                            java.util.function.Consumer<? super Step> adjuster,
                            ProjectImportProvider... providers) {
    return computeInWriteSafeContext(() -> doImportModule(path, project, adjuster, providers));
  }

  private Module doImportModule(String path, @Nullable Project project, java.util.function.Consumer<? super Step> adjuster, ProjectImportProvider[] providers) {
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
    assertNotNull("Can't find " + path, file);
    assertTrue(providers[0].canImport(file, project));

    //noinspection unchecked
    myWizard = (T)ImportModuleAction.createImportWizard(project, null, file, providers);
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
