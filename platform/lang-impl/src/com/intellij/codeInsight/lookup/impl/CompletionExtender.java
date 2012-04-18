/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.ui.HeavyweightHint;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * @author Konstantin Bulenkov
 */
public class CompletionExtender extends HeavyweightHint {
  private LookupElement myElement;
  private LookupImpl myLookup;
  private int myIndex;

  public CompletionExtender(@NotNull LookupElement element, @NotNull LookupImpl lookup) {
    super(createComponent(element, lookup), false);
    myElement = element;
    myLookup = lookup;
    myIndex = myLookup.getList().getSelectedIndex();
    myLookup.getComponent().addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        recalculateLocation();
      }

      @Override
      public void componentMoved(ComponentEvent e) {
        recalculateLocation();
      }
    });
  }

  public LookupElement getLookupElement() {
    return myElement;
  }

  public boolean sameAsFor(LookupElement item) {
    return getLookupElement().equals(item)
           && myIndex == myLookup.getList().getSelectedIndex();
  }

  private static JComponent createComponent(LookupElement element, LookupImpl lookup) {
    final LookupCellRenderer renderer = new LookupCellRenderer(lookup);
    renderer.setFullSize(true);
    final JComponent component = (JComponent)renderer.getListCellRendererComponent(lookup.getList(), element,
                                                                                   lookup.getList().getSelectedIndex(),
                                                                                   true, false);
    component.setSize(component.getPreferredSize());
    return component;
  }

  public boolean show() {
    final JList list = myLookup.getList();
    if (getComponent().getWidth() > list.getWidth()) {
      final JComponent rootPane = UIUtil.getRootPane(myLookup.getEditor().getContentComponent());

      final Point p = list.getLocationOnScreen();
      p.y += list.indexToLocation(list.getSelectedIndex()).y;
      SwingUtilities.convertPointFromScreen(p, rootPane);

      if (rootPane != null) {
        show(rootPane, p);
        return true;
      }
    }
    return false;
  }

  void recalculateLocation() {
    if (!isVisible()) return;
    final JList list = myLookup.getList();
    final Point p = list.getLocationOnScreen();
    p.y += list.indexToLocation(list.getSelectedIndex()).y;
    final JComponent rootPane = UIUtil.getRootPane(myLookup.getEditor().getContentComponent());
    if (rootPane != null) {
      SwingUtilities.convertPointFromScreen(p, rootPane);
      setLocation(new RelativePoint(rootPane, p));
    }
  }

  @Override
  public void hide() {
    super.hide();
    myLookup = null;
    myElement = null;
  }
}
