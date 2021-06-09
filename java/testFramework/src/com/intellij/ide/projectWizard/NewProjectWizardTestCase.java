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

import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author Dmitry Avdeev
 */
public abstract class NewProjectWizardTestCase extends ProjectWizardTestCase<AbstractProjectWizard> {
  @Override
  protected AbstractProjectWizard createWizard(@Nullable Project project, @Nullable File directory) {
    ModulesProvider modulesProvider = project == null ? ModulesProvider.EMPTY_MODULES_PROVIDER : new DefaultModulesProvider(project);
    return new NewProjectWizard(project, modulesProvider, ObjectUtils.doIfNotNull(directory, it -> it.getPath()));
  }
}
