// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class SearchEverywhereManagerImpl implements SearchEverywhereManager {

  private final Project myProject;

  private JBPopup myBalloon; //todo appropriate names #UX-1
  private final SearchEverywhereUI mySearchEverywhereUI;

  public SearchEverywhereManagerImpl(Project project) {
    myProject = project;

    //SearchEverywhereContributor selected = contributors.stream()
    //                                                   .filter(contributor -> contributor.getSearchProviderId().equals(mySelectedProviderID))
    //                                                   .findAny()
    //                                                   .orElse(null);
    List<SearchEverywhereContributor> allContributors = SearchEverywhereContributor.getProvidersSorted();
    mySearchEverywhereUI = new SearchEverywhereUI(project, allContributors, null);
  }

  @Override
  public void show(@NotNull String selectedContributorID) {
    if (isShown()) {
      setShownContributor(selectedContributorID);
    }
    else {
      mySearchEverywhereUI.setShown(true);
      mySearchEverywhereUI.switchToContributor(selectedContributorID);
      myBalloon = JBPopupFactory.getInstance().createComponentPopupBuilder(mySearchEverywhereUI, getSearchField())
                                .setProject(myProject)
                                .setResizable(false)
                                .setModalContext(false)
                                .setCancelOnClickOutside(true)
                                .setRequestFocus(true)
                                .setCancelKeyEnabled(false)
                                .setCancelCallback(() -> {
                                  mySearchEverywhereUI.clear();
                                  mySearchEverywhereUI.setShown(false);
                                  return true;
                                })
                                .addUserData("SIMPLE_WINDOW")
                                .createPopup();
      mySearchEverywhereUI.setSearchFinishedHandler(() -> myBalloon.cancel());

      RelativePoint showingPoint = calculateShowingPoint();
      if (showingPoint != null) {
        myBalloon.show(showingPoint);
      }
      else {
        myBalloon.showInFocusCenter();
      }
    }

  }

  @Override
  public boolean isShown() {
    return myBalloon != null && !myBalloon.isDisposed();
  }

  @Override
  public String getShownContributorID() {
    return mySearchEverywhereUI.getSelectedContributorID();
  }

  @Override
  public void setShownContributor(@NotNull String contributorID) {
    if (!contributorID.equals(getShownContributorID())) {
      mySearchEverywhereUI.switchToContributor(contributorID);
    }
  }

  @Override
  public boolean isShowNonProjectItems() {
    return mySearchEverywhereUI.isUseNonProjectItems();
  }

  @Override
  public void setShowNonProjectItems(boolean show) {
    mySearchEverywhereUI.setUseNonProjectItems(show);
  }

  private RelativePoint calculateShowingPoint() {
    final Window window = myProject != null
                          ? WindowManager.getInstance().suggestParentWindow(myProject)
                          : KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
    Component parent = UIUtil.findUltimateParent(window);
    if (parent == null) {
      return null;
    }

    int height = UISettings.getInstance().getShowMainToolbar() ? 135 : 115;
    if (parent instanceof IdeFrameImpl && ((IdeFrameImpl)parent).isInFullScreen()) {
      height -= 20;
    }
    return new RelativePoint(parent, new Point((parent.getSize().width - mySearchEverywhereUI.getPreferredSize().width) / 2, height));
  }

  private JTextField getSearchField() {
    return mySearchEverywhereUI.getSearchField();
  }
}
