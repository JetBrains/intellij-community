// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.gotoByName;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public abstract class ChooseByNameFactory {
  public static ChooseByNameFactory getInstance(Project project){
    return project.getService(ChooseByNameFactory.class);
  }

  public abstract ChooseByNamePopupComponent createChooseByNamePopupComponent(@NotNull ChooseByNameModel model);
}
