/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.utils.library;

import com.intellij.framework.FrameworkTypeEx;
import com.intellij.framework.addSupport.FrameworkSupportInModuleConfigurable;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModel;
import com.intellij.jarRepository.RepositoryLibrarySupportInModuleConfigurable;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.ModuleType;
import org.jetbrains.annotations.NotNull;

public class RepositoryLibrarySupportInModuleProvider extends FrameworkSupportInModuleProvider {
  @NotNull private final RepositoryLibraryDescription libraryDescription;
  @NotNull private final FrameworkTypeEx myFrameworkType;

  public RepositoryLibrarySupportInModuleProvider(@NotNull FrameworkTypeEx type,
                                                  @NotNull RepositoryLibraryDescription libraryDescription) {
    myFrameworkType = type;
    this.libraryDescription = libraryDescription;
  }

  @NotNull
  @Override
  public FrameworkTypeEx getFrameworkType() {
    return myFrameworkType;
  }

  @NotNull
  @Override
  public FrameworkSupportInModuleConfigurable createConfigurable(@NotNull FrameworkSupportModel model) {
    return new RepositoryLibrarySupportInModuleConfigurable(model.getProject(), libraryDescription);
  }

  @Override
  public boolean isEnabledForModuleType(@NotNull ModuleType moduleType) {
    return moduleType instanceof JavaModuleType;
  }
}
