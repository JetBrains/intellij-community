// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

import static com.intellij.ide.actions.SearchEverywhereAction.SEARCH_EVERYWHERE_POPUP;

public class SearchEverywhereManagerImpl implements SearchEverywhereManager {

  private final Project myProject;
  private final List<SearchEverywhereContributor> mySupportedContributors = SearchEverywhereContributor.getProvidersSorted();

  private JBPopup myBalloon; //todo appropriate names #UX-1
  private SearchEverywhereUI mySearchEverywhereUI;

  public SearchEverywhereManagerImpl(Project project) {
    myProject = project;
  }

  @Override
  public void show(@NotNull String selectedContributorID) {
    if (isShown()) {
      setShownContributor(selectedContributorID);
    }
    else {
      mySearchEverywhereUI = createView(myProject, mySupportedContributors);
      mySearchEverywhereUI.switchToContributor(selectedContributorID);
      myBalloon = JBPopupFactory.getInstance().createComponentPopupBuilder(mySearchEverywhereUI, mySearchEverywhereUI.getSearchField())
                                .setProject(myProject)
                                .setResizable(false)
                                .setModalContext(false)
                                .setCancelOnClickOutside(true)
                                .setRequestFocus(true)
                                .setCancelKeyEnabled(false)
                                //.setCancelCallback(() -> true)
                                .addUserData("SIMPLE_WINDOW")
                                .createPopup();
      Disposer.register(myBalloon, mySearchEverywhereUI);

      myProject.putUserData(SEARCH_EVERYWHERE_POPUP, myBalloon);
      Disposer.register(myBalloon, () -> myProject.putUserData(SEARCH_EVERYWHERE_POPUP, null));

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
    checkIsShown();
    return mySearchEverywhereUI.getSelectedContributorID();
  }

  @Override
  public void setShownContributor(@NotNull String contributorID) {
    checkIsShown();
    if (!contributorID.equals(getShownContributorID())) {
      mySearchEverywhereUI.switchToContributor(contributorID);
    }
  }

  @Override
  public boolean isShowNonProjectItems() {
    checkIsShown();
    return mySearchEverywhereUI.isUseNonProjectItems();
  }

  @Override
  public void setShowNonProjectItems(boolean show) {
    checkIsShown();
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

  private SearchEverywhereUI createView(Project project, List<SearchEverywhereContributor> allContributors) {
    SearchEverywhereUI view = new SearchEverywhereUI(project, allContributors, null);
    view.addPropertyChangeListener("preferredSize", evt -> {
      if (isShown()) {
        myBalloon.pack(true, true);
      }
    });

    view.setSearchFinishedHandler(() -> {
      if (isShown()) {
        myBalloon.cancel();
      }
    });

    return view;
  }

  private void checkIsShown() {
    if (!isShown()) {
      throw new IllegalStateException("Method should be called only when search popup is shown");
    }
  }
}
