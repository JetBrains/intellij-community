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
package com.intellij.ide.util.projectWizard.importSources;

import com.intellij.openapi.module.ModuleType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public final class DetectedContentRoot extends DetectedProjectRoot {
  @NotNull private final @Nls(capitalization = Nls.Capitalization.Sentence) String myRootTypeName;
  @NotNull private final ModuleType myModuleType;
  private final ModuleType @NotNull [] myTypesToReplace;

  public DetectedContentRoot(@NotNull File directory,
                             @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String rootTypeName,
                             @NotNull ModuleType moduleType,
                             ModuleType @NotNull ... typesToReplace) {
    super(directory);
    myRootTypeName = rootTypeName;
    myModuleType = moduleType;
    myTypesToReplace = typesToReplace;
  }

  @NotNull
  @Override
  public String getRootTypeName() {
    return myRootTypeName;
  }

  @NotNull
  public ModuleType getModuleType() {
    return myModuleType;
  }

  public ModuleType @NotNull [] getTypesToReplace() {
    return myTypesToReplace;
  }
}
