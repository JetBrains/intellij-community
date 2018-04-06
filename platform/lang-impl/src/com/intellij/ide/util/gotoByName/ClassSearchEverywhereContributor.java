// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.gotoByName;

import com.intellij.ide.actions.SearchEverywhereContributor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class ClassSearchEverywhereContributor implements SearchEverywhereContributor {
  @NotNull
  @Override
  public String getSearchProviderId() {
    return "Class";
  }

  @NotNull
  @Override
  public String getGroupName() {
    return "Classes";
  }

  @Override
  public int getSortWeight() {
    return 100;
  }
}
