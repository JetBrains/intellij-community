// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ui.popup;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.lookup.*;
import com.intellij.ide.util.gotoByName.QuickSearchComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.psi.PsiElement;
import com.intellij.ui.ComponentUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;


public abstract class PopupUpdateProcessor extends PopupUpdateProcessorBase {

  private final Project myProject;

  protected PopupUpdateProcessor(Project project) {
    myProject = project;
  }

  @Override
  public void beforeShown(final @NotNull LightweightWindowEvent windowEvent) {
    final Lookup activeLookup = LookupManager.getInstance(myProject).getActiveLookup();
    if (activeLookup != null) {
      activeLookup.addLookupListener(new LookupListener() {
        @Override
        public void currentItemChanged(@NotNull LookupEvent event) {
          if (windowEvent.asPopup().isVisible()) { //was not canceled yet
            final LookupElement item = event.getItem();
            if (item != null) {
              PsiElement targetElement =
                DocumentationManager.getInstance(myProject).getElementFromLookup(activeLookup.getEditor(), activeLookup.getPsiFile());

              updatePopup(targetElement); //open next
            }
          } else {
            activeLookup.removeLookupListener(this);
          }
        }
      });
    }
    else {
      final Component focusedComponent = WindowManagerEx.getInstanceEx().getFocusedComponent(myProject);
      QuickSearchComponent quickSearch = findQuickSearchComponent(focusedComponent);
      if (quickSearch != null) {
        quickSearch.registerHint(windowEvent.asPopup());
      }
      else if (focusedComponent instanceof JComponent) {
        HintUpdateSupply supply = HintUpdateSupply.getSupply((JComponent)focusedComponent);
        if (supply != null) supply.registerHint(windowEvent.asPopup());
      }
    }
  }

  private static QuickSearchComponent findQuickSearchComponent(Component c) {
    return ComponentUtil.getParentOfType(QuickSearchComponent.class, c);
  }
}
