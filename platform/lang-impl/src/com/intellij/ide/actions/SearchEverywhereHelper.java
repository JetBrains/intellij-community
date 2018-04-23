// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
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

public class SearchEverywhereHelper {

  public static void showSearchEverywherePopup(AnActionEvent evnt, String selectedProviderID) {
    List<SearchEverywhereContributor> contributors = SearchEverywhereContributor.getProvidersSorted();
    SearchEverywhereContributor selected = contributors.stream()
                                                       .filter(contributor -> contributor.getSearchProviderId().equals(selectedProviderID))
                                                       .findAny()
                                                       .orElse(null);

    SearchEverywhereUI searchEverywhereUI = new SearchEverywhereUI(contributors, selected);
    JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(searchEverywhereUI, searchEverywhereUI.getSearchField())
                                  .setProject(evnt.getProject())
                                  .setMovable(false)
                                  .setResizable(false)
                                  .setMayBeParent(true)
                                  .setCancelOnClickOutside(true)
                                  .setRequestFocus(true)
                                  .setCancelKeyEnabled(false)
                                  .setCancelCallback(() -> true)
                                  .addUserData("SIMPLE_WINDOW")
                                  .createPopup();

    RelativePoint showingPoint = calculateShowingPoint(evnt, searchEverywhereUI);
    popup.show(showingPoint);
    AnAction escape = ActionManager.getInstance().getAction("EditorEscape");
    DumbAwareAction.create(__ -> popup.cancel())
                   .registerCustomShortcutSet(escape == null ? CommonShortcuts.ESCAPE : escape.getShortcutSet(), popup.getContent(), popup);
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
}
