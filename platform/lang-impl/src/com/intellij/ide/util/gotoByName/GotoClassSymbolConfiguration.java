// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.gotoByName;

import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;


@State(name = "GotoClassSymbolConfiguration", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class GotoClassSymbolConfiguration extends ChooseByNameFilterConfiguration<LanguageRef> {
  public static GotoClassSymbolConfiguration getInstance(Project project) {
    return project.getService(GotoClassSymbolConfiguration.class);
  }

  @Override
  protected String nameForElement(LanguageRef type) {
    return type.getId();
  }
}
