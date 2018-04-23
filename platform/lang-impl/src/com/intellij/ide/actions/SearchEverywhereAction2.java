// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.gotoByName.ClassSearchEverywhereContributor;
import com.intellij.ide.util.gotoByName.FileSearchEverywhereContributor;
import com.intellij.ide.util.gotoByName.SymbolSearchEverywhereContributor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.impl.ModifierKeyDoubleClickHandler;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;

public class SearchEverywhereAction2 extends AnAction {

  static {
    ModifierKeyDoubleClickHandler.getInstance().registerAction(IdeActions.ACTION_SEARCH_EVERYWHERE, KeyEvent.VK_SHIFT, -1, false);
  }

  @Nullable
  protected final String mySelectedProviderID;
  private JBPopup myBalloon;
  private SearchEverywhereUI searchEverywhereUI;

  public SearchEverywhereAction2(@Nullable String selectedProviderID) {
    mySelectedProviderID = selectedProviderID;
  }

  public SearchEverywhereAction2() {
    this(null);
  }

  @Override
  public void actionPerformed(AnActionEvent evnt) {
    if (myBalloon != null && !myBalloon.isDisposed()) {
      searchEverywhereUI.setUseNonProjectItems(!searchEverywhereUI.isUseNonProjectItems());
      return;
    }

    IdeEventQueue.getInstance().getPopupManager().closeAllPopups(false);
    List<SearchEverywhereContributor> contributors = SearchEverywhereContributor.getProvidersSorted();
    SearchEverywhereContributor selected = contributors.stream()
                                                       .filter(contributor -> contributor.getSearchProviderId().equals(mySelectedProviderID))
                                                       .findAny()
                                                       .orElse(null);

    searchEverywhereUI = new SearchEverywhereUI(contributors, selected);
    myBalloon = JBPopupFactory.getInstance().createComponentPopupBuilder(searchEverywhereUI, searchEverywhereUI.getSearchField())
                              .setProject(evnt.getProject())
                              //.setMovable(false)
                              .setResizable(false)
                              //.setMayBeParent(true)
                              .setModalContext(false)
                              .setCancelOnClickOutside(true)
                              .setRequestFocus(true)
                              .setCancelKeyEnabled(false)
                              .setCancelCallback(() -> true)
                              .addUserData("SIMPLE_WINDOW")
                              .createPopup();

    RelativePoint showingPoint = calculateShowingPoint(evnt, searchEverywhereUI);
    myBalloon.show(showingPoint);
    AnAction escape = ActionManager.getInstance().getAction("EditorEscape");
    DumbAwareAction.create(__ -> myBalloon.cancel())
                   .registerCustomShortcutSet(escape == null ? CommonShortcuts.ESCAPE : escape.getShortcutSet(), myBalloon.getContent(),
                                              myBalloon);
    FeatureUsageTracker.getInstance().triggerFeatureUsed(IdeActions.ACTION_SEARCH_EVERYWHERE);
  }

  @NotNull
  private static RelativePoint calculateShowingPoint(AnActionEvent e, JComponent showingContent) {
    Project project = e.getProject();
    final Window window = project != null
                          ? WindowManager.getInstance().suggestParentWindow(project)
                          : KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
    Component parent = UIUtil.findUltimateParent(window);
    final RelativePoint showPoint;
    if (parent != null) {
      int height = UISettings.getInstance().getShowMainToolbar() ? 135 : 115;
      if (parent instanceof IdeFrameImpl && ((IdeFrameImpl)parent).isInFullScreen()) {
        height -= 20;
      }
      showPoint = new RelativePoint(parent, new Point((parent.getSize().width - showingContent.getPreferredSize().width) / 2, height));
    } else {
      showPoint = JBPopupFactory.getInstance().guessBestPopupLocation(e.getDataContext());
    }
    return showPoint;
  }

  public static class Class extends SearchEverywhereAction2 {
    public Class() {
      super(ClassSearchEverywhereContributor.class.getSimpleName());
    }
  }

  public static class File extends SearchEverywhereAction2 {
    public File() {
      super(FileSearchEverywhereContributor.class.getSimpleName());
    }
  }

  public static class Symbol extends SearchEverywhereAction2 {
    public Symbol() {
      super(SymbolSearchEverywhereContributor.class.getSimpleName());
    }
  }
}
