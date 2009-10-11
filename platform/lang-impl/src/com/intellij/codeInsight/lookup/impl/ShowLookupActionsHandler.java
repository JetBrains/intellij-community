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

    final Collection<LookupElementAction> actions = lookup.getActionsFor(element);
    if (actions.isEmpty()) {
      lookup.getEditor().getCaretModel().moveCaretRelatively(1, 0, false, false, true);
      return;
    }

    showItemActions(lookup, actions);
  }

  public static void showItemActions(LookupImpl lookup, Collection<LookupElementAction> actions) {
    final BaseListPopupStep<LookupElementAction> step = new LookupActionsStep(actions, lookup);

    final Rectangle bounds = lookup.getCurrentItemBounds();
    final ListPopup popup = JBPopupFactory.getInstance().createListPopup(step);
    final JComponent component = lookup.getComponent();
    popup.show(new RelativePoint(component, new Point(bounds.x + bounds.width,
                                                      bounds.y)));
  }

  private static class LookupActionsStep extends BaseListPopupStep<LookupElementAction> implements ClosableByLeftArrow {
    private final LookupImpl myLookup;

    public LookupActionsStep(Collection<LookupElementAction> actions, LookupImpl lookup) {
      super(null, new ArrayList<LookupElementAction>(actions));
      myLookup = lookup;
    }

    @Override
    public PopupStep onChosen(LookupElementAction selectedValue, boolean finalChoice) {
      selectedValue.performLookupAction();
      myLookup.hide();
      return FINAL_CHOICE;
    }

    @Override
    public Icon getIconFor(LookupElementAction aValue) {
      return aValue.getIcon();
    }

    @NotNull
    @Override
    public String getTextFor(LookupElementAction value) {
      return value.getText();
    }
  }
}