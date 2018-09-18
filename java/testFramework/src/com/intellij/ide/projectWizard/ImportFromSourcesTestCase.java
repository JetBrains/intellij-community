// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard;

import com.intellij.ide.util.importProject.DetectedRootData;
import com.intellij.ide.util.importProject.RootDetectionProcessor;
import com.intellij.ide.util.projectWizard.AbstractStepWithProgress;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.util.projectWizard.importSources.DetectedProjectRoot;
import com.intellij.ide.util.projectWizard.importSources.ProjectStructureDetector;
import com.intellij.ide.util.projectWizard.importSources.impl.ProjectFromSourcesBuilderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * @author nik
 */
public abstract class ImportFromSourcesTestCase extends PlatformTestCase {
  private ProjectFromSourcesBuilderImpl myBuilder;
  private File myRootDir;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myBuilder = new ProjectFromSourcesBuilderImpl(new WizardContext(null, getTestRootDisposable()), ModulesProvider.EMPTY_MODULES_PROVIDER);
  }

  @Override
  protected void tearDown() throws Exception {
    myBuilder = null;
    super.tearDown();
  }

  @Override
  protected void setUpProject() {
  }

  protected Module assertOneModule(@NotNull ModuleType moduleType) {
    Module module = assertOneElement(ModuleManager.getInstance(myProject).getModules());
    assertEquals(moduleType, ModuleType.get(module));
    return module;
  }

  protected void assertOneContentRoot(@NotNull Module module, String relativePath) {
    File expected = new File(myRootDir, relativePath);
    String url = assertOneElement(ModuleRootManager.getInstance(module).getContentRootUrls());
    File actual = new File(VfsUtilCore.urlToPath(url));
    assertEquals(expected.getAbsolutePath(), actual.getAbsolutePath());
  }

  protected void importFromSources(File dir) {
    myRootDir = dir;
    try {
      myProject = doCreateProject(getProjectDirOrFile());
      myBuilder.setBaseProjectPath(dir.getAbsolutePath());
      List<DetectedRootData> list = RootDetectionProcessor.detectRoots(dir);
      MultiMap<ProjectStructureDetector,DetectedProjectRoot> map = RootDetectionProcessor.createRootsMap(list);
      myBuilder.setupProjectStructure(map);
      for (ProjectStructureDetector detector : map.keySet()) {
        List<ModuleWizardStep> steps = detector.createWizardSteps(myBuilder, myBuilder.getProjectDescriptor(detector), EmptyIcon.ICON_16);
        try {
          for (ModuleWizardStep step : steps) {
            if (step instanceof AbstractStepWithProgress<?>) {
              performStep((AbstractStepWithProgress<?>)step);
            }
          }
        }
        finally {
          for (ModuleWizardStep step : steps) {
            step.disposeUIResources();
          }
        }
      }
      myBuilder.commit(myProject, null, ModulesProvider.EMPTY_MODULES_PROVIDER);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static <Result> void performStep(AbstractStepWithProgress<Result> step) {
    step.performStep();
  }
}
