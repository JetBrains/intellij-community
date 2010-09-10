/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.ClosableByLeftArrow;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;

public class ShowLookupActionsHandler extends LookupActionHandler {
  public ShowLookupActionsHandler(EditorActionHandler originalHandler){
    super(originalHandler);
  }

  protected void executeInLookup(final LookupImpl lookup) {
    final LookupElement element = lookup.getCurrentItem();
    if (element == null) {
      return;
    }

    if (!showItemActions(lookup, element)) {
      lookup.getEditor().getCaretModel().moveCaretRelatively(1, 0, false, false, true);
    }
  }

  public static boolean showItemActions(LookupImpl lookup, LookupElement element) {
    final Collection<LookupElementAction> actions = lookup.getActionsFor(element);
    if (actions.isEmpty()) {
      return false;
    }

    final BaseListPopupStep<LookupElementAction> step = new LookupActionsStep(actions, lookup, element);

    final Rectangle bounds = lookup.getCurrentItemBounds();
    final ListPopup popup = JBPopupFactory.getInstance().createListPopup(step);
    final JComponent component = lookup.getComponent();
    popup.show(new RelativePoint(component, new Point(bounds.x + bounds.width,
                                                      bounds.y)));
    return true;
  }

  private static class LookupActionsStep extends BaseListPopupStep<LookupElementAction> implements ClosableByLeftArrow {
    private final LookupImpl myLookup;
    private final LookupElement myLookupElement;
    private final Icon myEmptyIcon;

    public LookupActionsStep(Collection<LookupElementAction> actions, LookupImpl lookup, LookupElement lookupElement) {
      super(null, new ArrayList<LookupElementAction>(actions));
      myLookup = lookup;
      myLookupElement = lookupElement;

      int w = 0, h = 0;
      for (LookupElementAction action : actions) {
        final Icon icon = action.getIcon();
        if (icon != null) {
          w = Math.max(w, icon.getIconWidth());
          h = Math.max(h, icon.getIconHeight());
        }
      }
      myEmptyIcon = new EmptyIcon(w, h);
    }

    @Override
    public PopupStep onChosen(LookupElementAction selectedValue, boolean finalChoice) {
      final LookupElementAction.Result result = selectedValue.performLookupAction();
      if (result == LookupElementAction.Result.HIDE_LOOKUP) {
        myLookup.hide();
      } else {
        myLookup.updateItemActions(myLookupElement);
        myLookup.updateLookupWidth(myLookupElement);
        myLookup.refreshUi();
      }
      return FINAL_CHOICE;
    }

    @Override
    public Icon getIconFor(LookupElementAction aValue) {
      return LookupCellRenderer.augmentIcon(aValue.getIcon(), myEmptyIcon);
    }

    @NotNull
    @Override
    public String getTextFor(LookupElementAction value) {
      return value.getText();
    }
  }
}