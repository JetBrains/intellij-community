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
package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.ProjectBundle;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author yole
 */
@State(
  name = "libraryTable",
  roamingType = RoamingType.DISABLED,
  storages = {
    @Storage( file = StoragePathMacros.APP_CONFIG + "/applicationLibraries.xml")
    }
)
public class ExportableApplicationLibraryTable extends ApplicationLibraryTable implements ExportableComponent {
  @Override
  @NotNull
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile(getExternalFileName())};
  }

  @Override
  @NotNull
  public String getPresentableName() {
    return ProjectBundle.message("library.global.settings");
  }
}
