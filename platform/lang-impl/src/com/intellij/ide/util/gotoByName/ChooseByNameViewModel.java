// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.gotoByName;

import com.intellij.openapi.project.Project;

/**
 * @author yole
 */
public interface ChooseByNameViewModel {
  Project getProject();

  ChooseByNameModel getModel();

  boolean isSearchInAnyPlace();

  String transformPattern(String pattern);

  boolean canShowListForEmptyPattern();

  int getMaximumListSizeLimit();
}
