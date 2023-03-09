// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.editor.impl.jcef;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.Magnificator;
import com.intellij.ui.components.ZoomableViewport;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.JBUI;
import org.intellij.images.editor.actionSystem.ImageEditorActions;
import org.intellij.images.ui.ImageComponentDecorator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class JCefImageViewerUI extends JPanel implements DataProvider {
  private final @NotNull JCefImageViewer myViewer;
  private final @NotNull JLabel myInfoLabel;

  public JCefImageViewerUI(@NotNull Component component, @NotNull JCefImageViewer viewer) {
    myViewer = viewer;
    setLayout(new BorderLayout());

    ActionManager actionManager = ActionManager.getInstance();
    ActionGroup actionGroup = (ActionGroup)actionManager.getAction(ImageEditorActions.GROUP_TOOLBAR);
    ActionToolbar actionToolbar = actionManager.createActionToolbar(ImageEditorActions.ACTION_PLACE, actionGroup, true);
    actionToolbar.setTargetComponent(this);

    JComponent toolbarPanel = actionToolbar.getComponent();
    toolbarPanel.setBackground(JBColor.lazy(() -> getBackground()));

    JPanel topPanel = new NonOpaquePanel(new BorderLayout());
    topPanel.add(toolbarPanel, BorderLayout.WEST);
    myInfoLabel = new JLabel((String)null, SwingConstants.RIGHT);
    myInfoLabel.setBorder(JBUI.Borders.emptyRight(2));
    topPanel.add(myInfoLabel, BorderLayout.EAST);
    add(topPanel, BorderLayout.NORTH);
    JPanel viewPort = new ViewPort();
    viewPort.add(component);
    add(viewPort, BorderLayout.CENTER);
  }

  @Override
  public @Nullable Object getData(@NotNull String dataId) {
    if (ImageComponentDecorator.DATA_KEY.is(dataId)) {
      return myViewer;
    }
    return null;
  }

  public void setInfo(@Nls String info) {
    myInfoLabel.setText(info);
  }

  private class ViewPort extends JPanel implements ZoomableViewport {
    ViewPort() {
      super(new BorderLayout());
    }

    @Override
    public @Nullable Magnificator getMagnificator() {
      return myViewer.getMagnificator();
    }

    @Override
    public void magnificationStarted(Point at) {
      myViewer.magnificationStarted(at);
    }

    @Override
    public void magnificationFinished(double magnification) {
      myViewer.magnificationFinished(magnification);
    }

    @Override
    public void magnify(double magnification) {
      myViewer.magnify(magnification);
    }
  }
}
