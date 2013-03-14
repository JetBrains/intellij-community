package com.intellij.ide.projectWizard;

import com.intellij.ide.actions.ImportModuleAction;
import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.ide.util.newProjectWizard.SelectTemplateSettings;
import com.intellij.ide.util.newProjectWizard.SelectTemplateStep;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.actions.NewModuleAction;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.ProjectTemplate;
import com.intellij.projectImport.ProjectImportProvider;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 10/29/12
 */
public abstract class ProjectWizardTestCase extends PlatformTestCase {

  protected final List<Sdk> mySdks = new ArrayList<Sdk>();
  protected AddModuleWizard myWizard;
  @Nullable
  private Project myCreatedProject;

  protected Project createProjectFromTemplate(String group, String name, @Nullable Consumer<ModuleWizardStep> adjuster) throws IOException {
    runWizard(group, name, null, adjuster);
    try {
      myCreatedProject = NewProjectUtil.createFromWizard(myWizard, null);
    }
    catch (Throwable e) {
      myCreatedProject = ContainerUtil.find(myProjectManager.getOpenProjects(), new Condition<Project>() {
        @Override
        public boolean value(Project project) {
          return myWizard.getProjectName().equals(project.getName());
        }
      });
      throw new RuntimeException(e);
    }
    assertNotNull(myCreatedProject);
    UIUtil.dispatchAllInvocationEvents();

    Project[] projects = myProjectManager.getOpenProjects();
    assertEquals(Arrays.asList(projects).toString(), 2, projects.length);
    return myCreatedProject;
  }

  @Nullable
  protected Module createModuleFromTemplate(String group, String name, @Nullable Consumer<ModuleWizardStep> adjuster) throws IOException {
    runWizard(group, name, getProject(), adjuster);
    return createModuleFromWizard();
  }

  protected Module createModuleFromWizard() {
    return new NewModuleAction().createModuleFromWizard(myProject, null, myWizard);
  }

  protected void runWizard(String group, String name, Project project, @Nullable Consumer<ModuleWizardStep> adjuster) throws IOException {

    createWizard(project);
    SelectTemplateStep step = (SelectTemplateStep)myWizard.getCurrentStepObject();
    boolean condition = step.setSelectedTemplate(group, name);
    if (!condition) {
      throw new IllegalArgumentException(group + "/" + name + " template not found");
    }
    ProjectTemplate template = step.getSelectedTemplate();
    assertNotNull(template);

    if (adjuster != null) {
      adjuster.consume(step);
    }

    runWizard(adjuster);
  }

  protected void createWizard(Project project) throws IOException {
    File directory = FileUtil.createTempDirectory(getName(), "new", false);
    myFilesToDelete.add(directory);
    myWizard = new AddModuleWizard(project, DefaultModulesProvider.createForProject(project), directory.getPath());
    UIUtil.dispatchAllInvocationEvents(); // to make default selection applied
  }

  protected void runWizard(Consumer<ModuleWizardStep> adjuster) {
    while (!myWizard.isLast()) {
      myWizard.doNextAction();
      if (adjuster != null) {
        adjuster.consume(myWizard.getCurrentStepObject());
      }
    }
    myWizard.doOk();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Sdk projectSdk = ProjectRootManager.getInstance(getProject()).getProjectSdk();
    Sdk[] jdks = ProjectJdkTable.getInstance().getAllJdks();
    for (final Sdk jdk : jdks) {
      if (projectSdk != jdk) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            ProjectJdkTable.getInstance().removeJdk(jdk);
          }
        });
      }
    }
  }

  @Override
  public void tearDown() throws Exception {
    if (myWizard != null) {
      Disposer.dispose(myWizard.getDisposable());
    }
    if (myCreatedProject != null) {
      myProjectManager.closeProject(myCreatedProject);
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          Disposer.dispose(myCreatedProject);
        }
      });
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (Sdk sdk : mySdks) {
          ProjectJdkTable.getInstance().removeJdk(sdk);
        }
      }
    });
    SelectTemplateSettings.getInstance().setLastTemplate(null, null);
    super.tearDown();
  }

  protected Module importModuleFrom(ProjectImportProvider provider, String path) {
    return importFrom(path, getProject(), null, provider);
  }

  protected Module importProjectFrom(String path, Consumer<ModuleWizardStep> adjuster, ProjectImportProvider... providers) {
    Module module = importFrom(path, null, adjuster, providers);
    if (module != null) {
      myCreatedProject = module.getProject();
    }
    return module;
  }

  private Module importFrom(String path,
                            @Nullable Project project, Consumer<ModuleWizardStep> adjuster,
                            final ProjectImportProvider... providers) {
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
    assertNotNull("Can't find " + path, file);
    assertTrue(providers[0].canImport(file, project));

    myWizard = ImportModuleAction.createImportWizard(project, null, file, providers);
    if (myWizard.getStepCount() > 0) {
      runWizard(adjuster);
    }
    List<Module> modules = ImportModuleAction.createFromWizard(project, myWizard);
    return modules == null || modules.isEmpty() ? null : modules.get(0);
  }

  protected Sdk createSdk(String name, SdkTypeId sdkType) {
    final Sdk sdk = ProjectJdkTable.getInstance().createSdk(name, sdkType);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        ProjectJdkTable.getInstance().addJdk(sdk);
      }
    });
    mySdks.add(sdk);
    return sdk;
  }
}
