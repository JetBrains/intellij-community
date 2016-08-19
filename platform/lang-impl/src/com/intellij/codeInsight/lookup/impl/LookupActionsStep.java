/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.ui.popup.ClosableByLeftArrow;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;

/**
* @author peter
*/
public class LookupActionsStep extends BaseListPopupStep<LookupElementAction> implements ClosableByLeftArrow {
  private final LookupImpl myLookup;
  private final LookupElement myLookupElement;
  private final Icon myEmptyIcon;

  public LookupActionsStep(Collection<LookupElementAction> actions, LookupImpl lookup, LookupElement lookupElement) {
    super(null, new ArrayList<>(actions));
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
      myLookup.hideLookup(true);
    } else if (result == LookupElementAction.Result.REFRESH_ITEM) {
      myLookup.updateLookupWidth(myLookupElement);
      myLookup.requestResize();
      myLookup.refreshUi(false, true);
    } else if (result instanceof LookupElementAction.Result.ChooseItem) {
      myLookup.setCurrentItem(((LookupElementAction.Result.ChooseItem)result).item);
      CommandProcessor.getInstance().executeCommand(myLookup.getProject(), () -> myLookup.finishLookup(Lookup.AUTO_INSERT_SELECT_CHAR), null, null);
    }
    return FINAL_CHOICE;
  }

  @Override
  public Icon getIconFor(LookupElementAction aValue) {
    return LookupCellRenderer.augmentIcon(myLookup.getEditor(), aValue.getIcon(), myEmptyIcon);
  }

  @NotNull
  @Override
  public String getTextFor(LookupElementAction value) {
    return value.getText();
  }
}
