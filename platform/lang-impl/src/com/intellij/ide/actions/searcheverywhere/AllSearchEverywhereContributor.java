// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.IdeBundle;
import com.intellij.ui.IdeUICustomization;
import org.jetbrains.annotations.NotNull;

public class AllSearchEverywhereContributor implements SearchEverywhereContributor {

  @NotNull
  @Override
  public String getSearchProviderId() {
    return getClass().getSimpleName();
  }

  @NotNull
  @Override
  public String getGroupName() {
    return IdeBundle.message("searcheverywhere.allelements.tab.name");
  }

  @Override
  public int getSortWeight() {
    return 0;
  }

  @Override
  public String includeNonProjectItemsText() {
    return IdeBundle.message("checkbox.include.non.project.items", IdeUICustomization.getInstance().getProjectConceptName());
  }
}
