// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ui;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.ide.DataManager;
import com.intellij.ide.navbar.ide.NavBarVmImpl;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.util.Consumer;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;

import javax.swing.*;
import java.awt.*;

public class FloatingModeHelper {

  private static Component myContextComponent;
  private static LightweightHint myHint = null;
  private static JComponent myHintContainer;
  private static RelativePoint myLocationCache;

  public static LightweightHint showHint(DataContext dataContext, CoroutineScope cs, NavBarVmImpl navigationBar, Project project) {
    final JPanel panel = new JPanel(new BorderLayout());
    NewNavBarPanel component = new NewNavBarPanel(cs, navigationBar);
    panel.add(component);
    panel.setOpaque(true);

    if (ExperimentalUI.isNewUI()) {
      panel.setBorder(new JBEmptyBorder(JBUI.CurrentTheme.StatusBar.Breadcrumbs.floatingBorderInsets()));
      panel.setBackground(JBUI.CurrentTheme.StatusBar.Breadcrumbs.FLOATING_BACKGROUND);
    }
    else {
      panel.setBackground(UIUtil.getListBackground());
    }

    myHint = new LightweightHint(panel) {
      @Override
      public void hide() {
        super.hide();
        CoroutineScopeKt.cancel(cs, null);
      }
    };
    myHint.setForceShowAsPopup(true);
    myHint.setFocusRequestor(component);
    final KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();

    Editor editor = dataContext.getData(CommonDataKeys.EDITOR);

    if (editor == null) {
      myContextComponent = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext);
      getHintContainerShowPoint(component, project).doWhenDone((Consumer<RelativePoint>)relativePoint -> {
        final Component owner = focusManager.getFocusOwner();
        final Component cmp = relativePoint.getComponent();
        if (cmp instanceof JComponent && cmp.isShowing()) {
          myHint.show((JComponent)cmp, relativePoint.getPoint().x, relativePoint.getPoint().y,
                      owner instanceof JComponent ? (JComponent)owner : null,
                      new HintHint(relativePoint.getComponent(), relativePoint.getPoint()));
        }
      });
    }
    else {
      myHintContainer = editor.getContentComponent();
      getHintContainerShowPoint(component, project).doWhenDone((Consumer<RelativePoint>)rp -> {
        Point p = rp.getPointOn(myHintContainer).getPoint();
        final HintHint hintInfo = new HintHint(editor, p);
        HintManagerImpl.getInstanceImpl().showEditorHint(myHint, editor, p, HintManager.HIDE_BY_ESCAPE, 0, true, hintInfo);
      });
    }

    component.setOnSizeChange(size -> {
      myHint.setSize(size);
    });

    return myHint;
  }

  static AsyncResult<RelativePoint> getHintContainerShowPoint(JComponent component, Project project) {
    AsyncResult<RelativePoint> result = new AsyncResult<>();
    if (myLocationCache == null) {
      if (myHintContainer != null) {
        final Point p = AbstractPopup.getCenterOf(myHintContainer, component);
        p.y -= myHintContainer.getVisibleRect().height / 4;
        myLocationCache = RelativePoint.fromScreen(p);
      }
      else {
        DataManager dataManager = DataManager.getInstance();
        if (myContextComponent != null) {
          DataContext ctx = dataManager.getDataContext(myContextComponent);
          myLocationCache = JBPopupFactory.getInstance().guessBestPopupLocation(ctx);
        }
        else {
          dataManager.getDataContextFromFocus().doWhenDone((Consumer<DataContext>)dataContext -> {
            myContextComponent = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext);
            DataContext ctx = dataManager.getDataContext(myContextComponent);
            myLocationCache = JBPopupFactory.getInstance().guessBestPopupLocation(ctx);
          });
        }
      }
    }
    final Component c = myLocationCache.getComponent();
    if (!(c instanceof JComponent && c.isShowing())) {
      //Yes. It happens sometimes.
      // 1. Empty frame. call nav bar, select some package and open it in Project View
      // 2. Call nav bar, then Esc
      // 3. Hide all tool windows (Ctrl+Shift+F12), so we've got empty frame again
      // 4. Call nav bar. NPE. ta da
      final JComponent ideFrame = WindowManager.getInstance().getIdeFrame(project).getComponent();
      final JRootPane rootPane = UIUtil.getRootPane(ideFrame);
      myLocationCache = JBPopupFactory.getInstance().guessBestPopupLocation(rootPane);
    }
    result.setDone(myLocationCache);
    return result;
  }

  public static void hideHint(boolean ok) {
    if (myHint != null) {
      myHint.hide(ok);
      myHint = null;
    }
  }

}
