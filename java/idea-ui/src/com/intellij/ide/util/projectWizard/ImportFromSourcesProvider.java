/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.util.projectWizard;

import com.intellij.ide.util.newProjectWizard.StepSequence;
import com.intellij.ide.util.newProjectWizard.modes.CreateModuleFromSourcesMode;
import com.intellij.ide.util.projectWizard.importSources.impl.ProjectFromSourcesBuilderImpl;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.projectImport.ProjectImportProvider;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class ImportFromSourcesProvider extends ProjectImportProvider {

  public ImportFromSourcesProvider() {
    super(new ProjectFromSourcesBuilderImpl(null, null)); // fake
  }

  @Override
  public void addSteps(StepSequence sequence, WizardContext context, String id) {
    CreateModuleFromSourcesMode mode = new CreateModuleFromSourcesMode() {
      @Override
      public ProjectBuilder getModuleBuilder() {
        return myProjectBuilder;
      }
    };
    mode.addSteps(context, DefaultModulesProvider.createForProject(context.getProject()), sequence, getName());
    myBuilder = (ProjectImportBuilder)mode.getModuleBuilder();
  }

  @Nullable
  @Override
  public String getFileSample() {
    return "directory with <b>existing sources</b>";
  }
}
