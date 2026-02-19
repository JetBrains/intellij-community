// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.projectWizard.importSources;

import com.intellij.openapi.module.ModuleType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public final class DetectedContentRoot extends DetectedProjectRoot {
  private final @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String myRootTypeName;
  private final @NotNull ModuleType myModuleType;
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

  @Override
  public @NotNull String getRootTypeName() {
    return myRootTypeName;
  }

  public @NotNull ModuleType getModuleType() {
    return myModuleType;
  }

  public ModuleType @NotNull [] getTypesToReplace() {
    return myTypesToReplace;
  }
}
