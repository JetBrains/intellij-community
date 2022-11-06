// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ui;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Ref;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class FloatingModeHelper {

  public static LightweightHint showHint(DataContext dataContext, CoroutineScope cs, Project project, NewNavBarPanel panel) {
    final JPanel component = new JPanel(new BorderLayout());
    component.add(panel);
    component.setOpaque(true);

    if (ExperimentalUI.isNewUI()) {
      component.setBorder(new JBEmptyBorder(JBUI.CurrentTheme.StatusBar.Breadcrumbs.floatingBorderInsets()));
      component.setBackground(JBUI.CurrentTheme.StatusBar.Breadcrumbs.FLOATING_BACKGROUND);
    }
    else {
      component.setBackground(UIUtil.getListBackground());
    }

    var hint = new LightweightHint(component) {
      @Override
      public void hide() {
        super.hide();
        CoroutineScopeKt.cancel(cs, null);
      }
    };
    hint.setForceShowAsPopup(true);
    hint.setFocusRequestor(panel);

    Editor editor = dataContext.getData(CommonDataKeys.EDITOR);
    if (editor == null) {
      Component contextComponent = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext);
      RelativePoint relativePoint = getHintContainerShowPoint(project, panel, null, contextComponent);
      final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      final Component cmp = relativePoint.getComponent();
      if (cmp instanceof JComponent && cmp.isShowing()) {
        hint.show(
          (JComponent)cmp, relativePoint.getPoint().x, relativePoint.getPoint().y,
          owner instanceof JComponent ? (JComponent)owner : null,
          new HintHint(relativePoint.getComponent(), relativePoint.getPoint())
        );
      }
    }
    else {
      var hintContainer = editor.getContentComponent();
      RelativePoint rp = getHintContainerShowPoint(project, panel, hintContainer, null);
      Point p = rp.getPointOn(hintContainer).getPoint();
      final HintHint hintInfo = new HintHint(editor, p);
      HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, p, HintManager.HIDE_BY_ESCAPE, 0, true, hintInfo);
    }

    panel.setOnSizeChange(() -> hint.setSize(component.getPreferredSize()));

    return hint;
  }

  private static RelativePoint getHintContainerShowPoint(
    @NotNull Project project,
    @NotNull JComponent panel,
    @Nullable JComponent hintContainer,
    @Nullable Component contextComponent
  ) {
    final Ref<RelativePoint> myLocationCache = new Ref<>();
    if (hintContainer != null) {
      final Point p = AbstractPopup.getCenterOf(hintContainer, panel);
      p.y -= hintContainer.getVisibleRect().height / 4;
      myLocationCache.set(RelativePoint.fromScreen(p));
    }
    else {
      DataManager dataManager = DataManager.getInstance();
      if (contextComponent != null) {
        DataContext ctx = dataManager.getDataContext(contextComponent);
        myLocationCache.set(JBPopupFactory.getInstance().guessBestPopupLocation(ctx));
      }
      else {
        dataManager.getDataContextFromFocus().doWhenDone((Consumer<DataContext>)dataContext -> {
          var myContextComponent = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext);
          DataContext ctx = dataManager.getDataContext(myContextComponent);
          myLocationCache.set(JBPopupFactory.getInstance().guessBestPopupLocation(ctx));
        });
      }
    }
    final Component c = myLocationCache.get().getComponent();
    if (!(c instanceof JComponent && c.isShowing())) {
      //Yes. It happens sometimes.
      // 1. Empty frame. call nav bar, select some package and open it in Project View
      // 2. Call nav bar, then Esc
      // 3. Hide all tool windows (Ctrl+Shift+F12), so we've got empty frame again
      // 4. Call nav bar. NPE. ta da
      final JComponent ideFrame = WindowManager.getInstance().getIdeFrame(project).getComponent();
      final JRootPane rootPane = UIUtil.getRootPane(ideFrame);
      myLocationCache.set(JBPopupFactory.getInstance().guessBestPopupLocation(rootPane));
    }
    return myLocationCache.get();
  }
}
