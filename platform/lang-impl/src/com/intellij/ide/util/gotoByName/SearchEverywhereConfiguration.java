// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.gotoByName;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import org.jetbrains.annotations.ApiStatus;

/**
 * Configuration for contributors filter in "Search Everywhere" popup.
 */
@ApiStatus.Internal
@Service
@State(name = "SearchEverywhereConfiguration", storages = @Storage(StoragePathMacros.CACHE_FILE))
public final class SearchEverywhereConfiguration extends ChooseByNameFilterConfiguration<String>  {

  public static SearchEverywhereConfiguration getInstance() {
    return ApplicationManager.getApplication().getService(SearchEverywhereConfiguration.class);
  }

  @Override
  protected String nameForElement(String type) {
    return type;
  }
}
