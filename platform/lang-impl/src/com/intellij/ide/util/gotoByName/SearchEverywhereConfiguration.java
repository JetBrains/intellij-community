// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.gotoByName;

import com.intellij.openapi.components.*;

/**
 * Configuration for contributors filter in "Search Everywhere" popup.
 */
@Service
@State(name = "SearchEverywhereConfiguration", storages = @Storage(StoragePathMacros.CACHE_FILE))
public final class SearchEverywhereConfiguration extends ChooseByNameFilterConfiguration<String>  {

  public static SearchEverywhereConfiguration getInstance() {
    return ServiceManager.getService(SearchEverywhereConfiguration.class);
  }

  @Override
  protected String nameForElement(String type) {
    return type;
  }
}
