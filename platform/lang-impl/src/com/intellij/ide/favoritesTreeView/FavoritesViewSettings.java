// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.PropertyName;

/**
 * @author Konstantin Bulenkov
 */
@Deprecated(forRemoval = true)
public class FavoritesViewSettings implements ViewSettings {

  @PropertyName("favorites.view.settings.show.members")
  public boolean myShowMembers = false;

  @PropertyName("favorites.view.settings.flatten.packages")
  public boolean myFlattenPackages = false;

  @PropertyName("favorites.view.settings.autoscroll.to.source")
  public boolean myAutoScrollToSource = false;

  @PropertyName("favorites.view.settings.autoscroll.from.source")
  public boolean myAutoScrollFromSource = false;

  @PropertyName("favorites.view.settings.hide.empty.middle.packages")
  public boolean myHideEmptyMiddlePackages = true;

  @PropertyName("favorites.view.settings.abbreviate.qualified.package.names")
  public boolean myAbbreviateQualifiedPackages = false;


  public FavoritesViewSettings() {
    PropertiesComponent.getInstance().loadFields(this);
  }

  @Override
  public boolean isShowMembers() {
    return myShowMembers;
  }

  public void setShowMembers(boolean showMembers) {
    myShowMembers = showMembers;
    save();
  }

  private void save() {
    PropertiesComponent.getInstance().saveFields(this);
  }

  public boolean isAutoScrollFromSource() {
    return myAutoScrollFromSource;
  }

  public void setAutoScrollFromSource(boolean autoScrollFromSource) {
    myAutoScrollFromSource = autoScrollFromSource;
    save();
  }

  @Override
  public boolean isFlattenPackages() {
    return myFlattenPackages;
  }

  public void setFlattenPackages(boolean flattenPackages) {
    myFlattenPackages = flattenPackages;
    save();
  }

  @Override
  public boolean isAbbreviatePackageNames() {
    return myAbbreviateQualifiedPackages;
  }

  @Override
  public boolean isHideEmptyMiddlePackages() {
    return myHideEmptyMiddlePackages;
  }

  public boolean isAutoScrollToSource() {
    return myAutoScrollToSource;
  }

  public void setAutoScrollToSource(boolean autoScrollToSource) {
    myAutoScrollToSource = autoScrollToSource;
    save();
  }

  public void setHideEmptyMiddlePackages(boolean hide) {
    myHideEmptyMiddlePackages = hide;
    save();
  }

  public void setAbbreviateQualifiedPackages(boolean abbreviate) {
    myAbbreviateQualifiedPackages = abbreviate;
    save();
  }
}
