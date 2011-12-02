/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.projectView.ViewSettings;

/**
 * @author Konstantin Bulenkov
 */
public class FavoritesViewSettings implements ViewSettings {
  private boolean myShowMembers = false;
  private boolean myFlattenPackages = false;
  private boolean myAutoScrollToSource = false;

  @Override
  public boolean isShowMembers() {
    return myShowMembers;
  }

  public void setShowMembers(boolean showMembers) {
    myShowMembers = showMembers;
  }

  @Override
  public boolean isStructureView() {
    return false;
  }

  @Override
  public boolean isShowModules() {
    return true;
  }

  @Override
  public boolean isFlattenPackages() {
    return myFlattenPackages;
  }

  public void setFlattenPackages(boolean flattenPackages) {
    myFlattenPackages = flattenPackages;
  }

  @Override
  public boolean isAbbreviatePackageNames() {
    return false;
  }

  @Override
  public boolean isHideEmptyMiddlePackages() {
    return false;
  }

  @Override
  public boolean isShowLibraryContents() {
    return false;
  }

  public boolean isAutoScrollToSource() {
    return myAutoScrollToSource;
  }

  public void setAutoScrollToSource(boolean autoScrollToSource) {
    myAutoScrollToSource = autoScrollToSource;
  }
}
