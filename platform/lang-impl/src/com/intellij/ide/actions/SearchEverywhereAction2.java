// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.actions.searcheverywhere.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.keymap.impl.ModifierKeyDoubleClickHandler;
import org.jetbrains.annotations.NotNull;

import java.awt.event.KeyEvent;

public abstract class SearchEverywhereAction2 extends AnAction {

  static {
    ModifierKeyDoubleClickHandler.getInstance().registerAction(IdeActions.ACTION_SEARCH_EVERYWHERE, KeyEvent.VK_SHIFT, -1, false);
  }

  @NotNull
  protected final String mySelectedProviderID; //todo change to contributor??? UX-1

  protected SearchEverywhereAction2(@NotNull String selectedProviderID) {
    mySelectedProviderID = selectedProviderID;
  }

  @Override
  public void actionPerformed(AnActionEvent evnt) {
    //todo same or different action triggered? #UX-1
    FeatureUsageTracker.getInstance().triggerFeatureUsed(IdeActions.ACTION_SEARCH_EVERYWHERE);

    SearchEverywhereManager seManager = SearchEverywhereManager.getInstance(evnt.getProject());
    if (seManager.isShown()) {
      if (mySelectedProviderID.equals(seManager.getShownContributor().getSearchProviderId())) {
        seManager.setShowNonProjectItems(!seManager.isShowNonProjectItems());
      }
      else {
        seManager.setShownContributor(mySelectedProviderID);
      }
      return;
    }

    IdeEventQueue.getInstance().getPopupManager().closeAllPopups(false);
    seManager.show(mySelectedProviderID);
  }

  public static class All extends SearchEverywhereAction2 {
    protected All() {
      super(AllSearchEverywhereContributor.class.getSimpleName());
    }
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
