// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.editor.impl.jcef;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.JBColor;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.components.Magnificator;
import com.intellij.ui.components.ZoomableViewport;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.JBUI;
import org.intellij.images.ImagesBundle;
import org.intellij.images.editor.actionSystem.ImageEditorActions;
import org.intellij.images.options.EditorOptions;
import org.intellij.images.options.Options;
import org.intellij.images.options.OptionsManager;
import org.intellij.images.options.ZoomOptions;
import org.intellij.images.ui.ImageComponentDecorator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class JCefImageViewerUI extends JPanel implements DataProvider, Disposable {
  private final @NotNull JCefImageViewer myViewer;
  private final @NotNull JLabel myInfoLabel;
  private final @NotNull Component myContentComponent;

  @NonNls
  private static final String IMAGE_PANEL = "image";
  @NonNls
  private static final String ERROR_PANEL = "error";
  private final JPanel myViewPort;

  public JCefImageViewerUI(@NotNull Component component, @NotNull JCefImageViewer viewer) {
    myViewer = viewer;
    myContentComponent = component;
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
    myViewPort = new ViewPort();
    myViewPort.setLayout(new CardLayout());

    PopupHandler.installPopupMenu(myViewPort, ImageEditorActions.GROUP_POPUP, ImageEditorActions.ACTION_PLACE);

    myViewer.getPreferredFocusedComponent().addMouseWheelListener(MOUSE_WHEEL_LISTENER);

    myViewPort.add(component, IMAGE_PANEL);
    JLabel errorLabel = new JLabel(
      ImagesBundle.message("error.broken.image.file.format"),
      Messages.getErrorIcon(), SwingConstants.CENTER
    );

    JPanel errorPanel = new JPanel(new BorderLayout());
    errorPanel.add(errorLabel, BorderLayout.CENTER);
    myViewPort.add(component, IMAGE_PANEL);
    myViewPort.add(errorPanel, ERROR_PANEL);

    PopupHandler.installPopupMenu((JComponent)component, ImageEditorActions.GROUP_POPUP, ImageEditorActions.ACTION_PLACE);
    add(myViewPort, BorderLayout.CENTER);
  }

  @Override
  public @Nullable Object getData(@NotNull String dataId) {
    if (ImageComponentDecorator.DATA_KEY.is(dataId)) {
      return myViewer;
    }
    return null;
  }

  @Override
  public void dispose() {
    myViewer.getPreferredFocusedComponent().removeMouseWheelListener(MOUSE_WHEEL_LISTENER);
  }

  public void setInfo(@Nls String info) {
    myInfoLabel.setText(info);
  }

  private class ViewPort extends JPanel implements ZoomableViewport {
    private Point myMagnificationPoint = null;
    private Double myOriginalZoom = 1.0;

    ViewPort() {
      super(new BorderLayout());
    }

    protected Point convertToContentCoordinates(Point point) {
      return SwingUtilities.convertPoint(this, point, myContentComponent);
    }

    @Override
    public Magnificator getMagnificator() {
      return new Magnificator() {
        @Override
        public Point magnify(double scale, Point at) {
          myViewer.setZoom(scale, at);
          return at;
        }
      };
    }

    @Override
    public void magnificationStarted(Point at) {
      myMagnificationPoint = at;
      myOriginalZoom = myViewer.getZoom();
    }

    @Override
    public void magnificationFinished(double magnification) {
      myMagnificationPoint = null;
      myOriginalZoom = 1.0;
    }

    @Override
    public void magnify(double magnification) {
      Point p = myMagnificationPoint;
      if (Double.compare(magnification, 0) != 0 && p != null) {
        Magnificator magnificator = getMagnificator();
        Point inContentPoint = convertToContentCoordinates(p);
        double scale = magnification < 0 ? 1f / (1 - magnification) : (1 + magnification);
        magnificator.magnify(myOriginalZoom * scale, inContentPoint);
      }
    }
  }

  private final @NotNull MouseWheelListener MOUSE_WHEEL_LISTENER = new MouseWheelListener() {
    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
      Options options = OptionsManager.getInstance().getOptions();
      EditorOptions editorOptions = options.getEditorOptions();
      ZoomOptions zoomOptions = editorOptions.getZoomOptions();
      if (zoomOptions.isWheelZooming() && e.isControlDown()) {
        int rotation = e.getWheelRotation();
        if (rotation < 0) {
          myViewer.setZoom(myViewer.getZoom() * 1.2, new Point(e.getX(), e.getY()));
        }
        else if (rotation > 0) {
          myViewer.setZoom(myViewer.getZoom() / 1.2, new Point(e.getX(), e.getY()));
        }

        e.consume();
      }
    }
  };

  public void showError() {
    CardLayout layout = (CardLayout)myViewPort.getLayout();
    layout.show(myViewPort, ERROR_PANEL);
  }

  public void showImage() {
    CardLayout layout = (CardLayout)myViewPort.getLayout();
    layout.show(myViewPort, IMAGE_PANEL);
  }
}
