// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.project;

import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;

public final class LibraryDependencyData extends AbstractDependencyData<LibraryData> implements Named {
  @NotNull
  private LibraryLevel level;

  @PropertyMapping({"ownerModule", "target", "level"})
  public LibraryDependencyData(@NotNull ModuleData ownerModule, @NotNull LibraryData library, @NotNull LibraryLevel level) {
    super(ownerModule, library);

    this.level = level;
  }

  @NotNull
  public LibraryLevel getLevel() {
    return level;
  }

  public void setLevel(@NotNull LibraryLevel level) {
    this.level = level;
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + level.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }
    return level.equals(((LibraryDependencyData)o).level);
  }
}
