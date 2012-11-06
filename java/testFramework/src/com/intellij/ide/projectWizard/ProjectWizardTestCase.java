package com.intellij.ide.projectWizard;

import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.ide.util.newProjectWizard.SelectTemplateStep;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.wizard.Step;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.actions.NewModuleAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.ProjectTemplate;
import com.intellij.projectImport.ProjectImportProvider;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * @author Dmitry Avdeev
 *         Date: 10/29/12
 */
public abstract class ProjectWizardTestCase extends PlatformTestCase {

  protected TestWizard myWizard;
  @Nullable
  private Project myCreatedProject;

  protected Project createProjectFromTemplate(String group, String name, @Nullable Consumer<ModuleWizardStep> adjuster) {
    runWizard(group, name, adjuster);
    try {
      myCreatedProject = NewProjectUtil.doCreate(myWizard, null);
    }
    catch (IOException e) {
      fail(e.getMessage());
    }
    assertNotNull(myCreatedProject);

    Project[] projects = myProjectManager.getOpenProjects();
    assertEquals(2, projects.length);
    System.out.println(myCreatedProject.getBasePath());
    return myCreatedProject;
  }

  @Nullable
  protected Module createModuleFromTemplate(String group, String name, @Nullable Consumer<ModuleWizardStep> adjuster) {
    runWizard(group, name, adjuster);
    return createModuleFromWizard();
  }

  protected Module createModuleFromWizard() {
    return new NewModuleAction().createModuleFromWizard(myProject, null, myWizard);
  }

  protected void runWizard(String group, String name, @Nullable Consumer<ModuleWizardStep> adjuster) {
    SelectTemplateStep step = (SelectTemplateStep)myWizard.getCurrentStepObject();
    assertTrue(step.setSelectedTemplate(group, name));
    ProjectTemplate template = step.getSelectedTemplate();
    assertNotNull(template);
    System.out.println(template.getName());

    if (adjuster != null) {
      adjuster.consume(step);
    }

    runWizard(adjuster);
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
  public void setUp() throws Exception {
    super.setUp();
    File directory = FileUtil.createTempDirectory(getName(), "new", false);
    myFilesToDelete.add(directory);

    myWizard = createWizard(directory.getPath());

    if (myWizard != null) {
      myWizard.navigateToStep(new Function<Step, Boolean>() {
        @Override
        public Boolean fun(Step step) {
          return step instanceof SelectTemplateStep;
        }
      });
      UIUtil.dispatchAllInvocationEvents(); // to make default selection applied
    }
  }

  @Nullable
  protected TestWizard createWizard(String directory) {
    return new TestWizard(null, directory);
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
    super.tearDown();
  }

  protected Module importFrom(ProjectImportProvider provider, String path) {
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
    assertNotNull("Can't find " + path, file);
    assertTrue(provider.canImport(file, getProject()));
    myWizard = new TestWizard(getProject(), path, provider);
    runWizard(null);
    return createModuleFromWizard();
  }

  protected static class TestWizard extends AddModuleWizard {

    public TestWizard(@Nullable Project project, String defaultPath) {
      super(project, DefaultModulesProvider.createForProject(project), defaultPath);
    }

    public TestWizard(Project project, String filePath, ProjectImportProvider... importProvider) {
      super(null, project, filePath, importProvider);
    }

    void doOk() {
      doOKAction();
    }

    boolean isLast() {
      return isLastStep();
    }

    void commit() {
      commitStepData(getCurrentStepObject());
    }
  }

}
