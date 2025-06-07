// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.ui;

import com.intellij.dvcs.DvcsImplIconsExt;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.ui.EmptyIcon;
import icons.DvcsImplIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class BranchActionGroup extends ActionGroup implements DumbAware, CustomIconProvider {

  private boolean myIsFavorite;
  private LayeredIcon myIcon;
  private LayeredIcon myHoveredIcon;

  public BranchActionGroup() {
    super("", true);
    setIcons(AllIcons.Nodes.Favorite, EmptyIcon.ICON_16, AllIcons.Nodes.Favorite, AllIcons.Nodes.NotFavoriteOnHover);
    getTemplatePresentation().putClientProperty(ActionUtil.ALWAYS_VISIBLE_GROUP, true);
  }

  protected void setIcons(@NotNull Icon favorite,
                          @NotNull Icon notFavorite,
                          @NotNull Icon favoriteOnHover,
                          @NotNull Icon notFavoriteOnHover) {
    myIcon = LayeredIcon.layeredIcon(new Icon[]{favorite, notFavorite});
    myHoveredIcon = LayeredIcon.layeredIcon(new Icon[]{favoriteOnHover, notFavoriteOnHover});
    getTemplatePresentation().setIcon(myIcon);
    getTemplatePresentation().setSelectedIcon(myHoveredIcon);
    updateIcons();
  }

  private void updateIcons() {
    myIcon.setLayerEnabled(0, myIsFavorite);
    myHoveredIcon.setLayerEnabled(0, myIsFavorite);

    myIcon.setLayerEnabled(1, !myIsFavorite);
    myHoveredIcon.setLayerEnabled(1, !myIsFavorite);
  }

  public boolean isFavorite() {
    return myIsFavorite;
  }

  public void setFavorite(boolean favorite) {
    myIsFavorite = favorite;
    updateIcons();
  }

  public void toggle() {
    setFavorite(!myIsFavorite);
  }

  public boolean hasIncomingCommits() { return false; }

  public boolean hasOutgoingCommits() { return false; }

  @Override
  public @Nullable Icon getRightIcon() {
    if (hasIncomingCommits()) {
      return hasOutgoingCommits() ? DvcsImplIconsExt.getIncomingOutgoingIcon() : DvcsImplIcons.Incoming;
    }
    return hasOutgoingCommits() ? DvcsImplIcons.Outgoing : null;
  }
}
