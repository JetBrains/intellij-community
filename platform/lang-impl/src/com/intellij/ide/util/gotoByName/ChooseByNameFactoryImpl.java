// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.util.gotoByName;

import com.intellij.ide.actions.GotoActionBase;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class ChooseByNameFactoryImpl extends ChooseByNameFactory {
  private final Project myProject;

  public ChooseByNameFactoryImpl(final Project project) {
    myProject = project;
  }

  @Override
  public ChooseByNamePopup createChooseByNamePopupComponent(final @NotNull ChooseByNameModel model) {
    return ChooseByNamePopup.createPopup(myProject, model, GotoActionBase.getPsiContext(myProject));  
  }
}
