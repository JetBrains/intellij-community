// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionAdapter;

/**
* @author Konstantin Bulenkov
*/
@Internal
public final class NavBarListWrapper extends JBScrollPane implements DataProvider {
  private static final int MAX_SIZE = 20;
  private final JList myList;

  public NavBarListWrapper(final JList list) {
    super(list);
    list.addMouseMotionListener(new MouseMotionAdapter() {
      boolean myIsEngaged = false;
      @Override
      public void mouseMoved(MouseEvent e) {
        if (myIsEngaged && !UIUtil.isSelectionButtonDown(e)) {
          final Point point = e.getPoint();
          final int index = list.locationToIndex(point);
          list.setSelectedIndex(index);
        } else {
          myIsEngaged = true;
        }
      }
    });

    ScrollingUtil.installActions(list);
    myList = list;

    if (isPopupHeightStatic()) {
      list.setVisibleRowCount(0);
      updateViewportPreferredSizeIfNeeded();
    } else {
      list.setVisibleRowCount(MAX_SIZE);
    }
  }

  public void updateViewportPreferredSizeIfNeeded() {
    if (isPopupHeightStatic()) {
      getViewport().setPreferredSize(myList.getPreferredSize());
    }
  }

  private boolean isPopupHeightStatic() {
    if (ExperimentalUI.isNewUI()) return false;
    final int modelSize = myList.getModel().getSize();
    return modelSize > 0 && modelSize <= MAX_SIZE;
  }

  @Override
  @Nullable
  public Object getData(@NotNull @NonNls String dataId) {
    if (PlatformCoreDataKeys.SELECTED_ITEM.is(dataId)){
      return myList.getSelectedValue();
    }
    if (PlatformCoreDataKeys.SELECTED_ITEMS.is(dataId)){
      return myList.getSelectedValues();
    }
    return null;
  }

  @Override
  public void setBorder(Border border) {
    if (myList != null){
      myList.setBorder(border);
    }
  }

  @Override
  public void requestFocus() {
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myList, true));
  }

  @Override
  public synchronized void addMouseListener(MouseListener l) {
    myList.addMouseListener(l);
  }
}
