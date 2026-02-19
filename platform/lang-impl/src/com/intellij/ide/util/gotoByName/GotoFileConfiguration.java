// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.util.gotoByName;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;

/**
 * Configuration for file type filtering popup in "Go to | File" action.
 *
 * @author Constantine.Plotnikov
 */
@Service(Service.Level.PROJECT)
@State(name = "GotoFileConfiguration", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class GotoFileConfiguration extends ChooseByNameFilterConfiguration<FileTypeRef> {
  /**
   * Get configuration instance
   *
   * @param project a project instance
   * @return a configuration instance
   */
  public static GotoFileConfiguration getInstance(Project project) {
    return project.getService(GotoFileConfiguration.class);
  }

  @Override
  protected String nameForElement(FileTypeRef type) {
    return type.getName();
  }
}
