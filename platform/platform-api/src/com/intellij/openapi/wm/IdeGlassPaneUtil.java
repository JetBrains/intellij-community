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

package com.intellij.openapi.wm;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.Painter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public class IdeGlassPaneUtil {

  private IdeGlassPaneUtil() {
  }

  @NotNull
  public static IdeGlassPane find(@NotNull Component component) {
    if (!(component instanceof JComponent)) throw new IllegalArgumentException("Component must be instance of JComponent");

    final JRootPane root = ((JComponent)component).getRootPane();
    if (root == null) throw new IllegalArgumentException("Component must be visible in order to find glass pane for it");

    final Component gp = root.getGlassPane();
    if (!(gp instanceof IdeGlassPane)) {
      throw new IllegalArgumentException("Glass pane should be " + IdeGlassPane.class.getName());
    }
    return (IdeGlassPane)gp;
  }

  public static void installPainter(@NotNull JComponent target, @NotNull Painter painter, @NotNull Disposable parent) {
    final UiNotifyConnector connector = new UiNotifyConnector(target, new Activatable() {
      private IdeGlassPane myPane;
      private Disposable myPanePainterListeners = Disposer.newDisposable();

      @Override
      public void showNotify() {
        IdeGlassPane pane = find(target);
        if (myPane != null && myPane != pane) {
          Disposer.dispose(myPanePainterListeners);
        }
        myPane = pane;
        myPanePainterListeners = Disposer.newDisposable("PanePainterListeners");
        Disposer.register(parent, myPanePainterListeners);
        myPane.addPainter(target, painter, myPanePainterListeners);
      }

      @Override
      public void hideNotify() {
        Disposer.dispose(myPanePainterListeners);
      }
    });
    Disposer.register(parent, connector);
  }

  public static boolean canBePreprocessed(@NotNull MouseEvent e) {
    Component c = UIUtil.getDeepestComponentAt(e.getComponent(), e.getX(), e.getY());

    if (JBPopupFactory.getInstance().getParentBalloonFor(c) != null && e.getID() != MouseEvent.MOUSE_DRAGGED) {
      return false;
    }

    if (c instanceof IdeGlassPane.TopComponent) {
      return ((IdeGlassPane.TopComponent)c).canBePreprocessed(e);
    }

    return true;
  }

}
