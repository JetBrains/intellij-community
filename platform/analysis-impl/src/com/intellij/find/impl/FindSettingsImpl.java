// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl;

import com.intellij.find.FindBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;

@State(name = "FindSettings", storages = @Storage("find.xml"))
@ApiStatus.Internal
public class FindSettingsImpl extends FindSettingsBase {

  @State(name = "FindRecents", storages = @Storage(value = "find.recents.xml", roamingType = RoamingType.DISABLED))
  static final class FindRecents extends FindInProjectSettingsBase {
    public static FindRecents getInstance() {
      return ApplicationManager.getApplication().getService(FindRecents.class);
    }
  }

  @ApiStatus.Internal
  public static @Nls String getDefaultSearchScope() {
    return FindBundle.message("find.scope.all.project.classes");
  }
}
