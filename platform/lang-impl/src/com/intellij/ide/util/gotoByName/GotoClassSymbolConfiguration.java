// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;


@Service(Service.Level.PROJECT)
@State(name = "GotoClassSymbolConfiguration", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class GotoClassSymbolConfiguration extends ChooseByNameFilterConfiguration<LanguageRef> {
  public static GotoClassSymbolConfiguration getInstance(Project project) {
    return project.getService(GotoClassSymbolConfiguration.class);
  }

  @Override
  protected String nameForElement(LanguageRef type) {
    return type.getId();
  }
}
