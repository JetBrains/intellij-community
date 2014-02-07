/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
    myBuilder = new ProjectFromSourcesBuilderImpl(new WizardContext(null), ModulesProvider.EMPTY_MODULES_PROVIDER);
  }

  @Override
  protected void setUpProject() throws Exception {
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
      myProject = doCreateProject(getIprFile());
      myBuilder.setBaseProjectPath(dir.getAbsolutePath());
      List<DetectedRootData> list = RootDetectionProcessor.detectRoots(dir);
      MultiMap<ProjectStructureDetector,DetectedProjectRoot> map = RootDetectionProcessor.createRootsMap(list);
      myBuilder.setupProjectStructure(map);
      for (ProjectStructureDetector detector : map.keySet()) {
        List<ModuleWizardStep> steps = detector.createWizardSteps(myBuilder, myBuilder.getProjectDescriptor(detector), EmptyIcon.ICON_16);
        for (ModuleWizardStep step : steps) {
          if (step instanceof AbstractStepWithProgress<?>) {
            performStep((AbstractStepWithProgress<?>)step);
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
