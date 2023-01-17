// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.editor.impl.jcef;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.Magnificator;
import com.intellij.ui.components.ZoomableViewport;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.intellij.images.editor.actionSystem.ImageEditorActions;
import org.intellij.images.options.EditorOptions;
import org.intellij.images.options.Options;
import org.intellij.images.options.OptionsManager;
import org.intellij.images.options.ZoomOptions;
import org.intellij.images.ui.ImageComponentDecorator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;

public class JCefImageViewerUI extends JPanel implements DataProvider, Disposable {
  private final @NotNull JCefImageViewer myViewer;
  private final @NotNull JLabel myInfoLabel;
  private final @NotNull JComponent myContentComponent;
  private final @NotNull MouseWheelListener myMouseWheelListener = new ImageWheelAdapter();
  private final JPanel myViewPort;

  public JCefImageViewerUI(@NotNull JComponent component, @NotNull JCefImageViewer viewer) {
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
    myViewPort.addMouseWheelListener(myMouseWheelListener);
    myViewPort.add(component);
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
    myViewPort.removeMouseWheelListener(myMouseWheelListener);
  }

  private final class ImageWheelAdapter implements MouseWheelListener {
    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
      Options options = OptionsManager.getInstance().getOptions();
      EditorOptions editorOptions = options.getEditorOptions();
      ZoomOptions zoomOptions = editorOptions.getZoomOptions();
      if (zoomOptions.isWheelZooming() && e.isControlDown()) {
        int rotation = e.getWheelRotation();
        if (rotation > 0) {
          myViewer.magnify(1.2, new Point(e.getX(), e.getY()), null);
        }
        else if (rotation < 0) {
          myViewer.magnify(0.83, new Point(e.getX(), e.getY()), null);
        }

        e.consume();
      }
    }
  }
  public void setInfo(@Nls String info) {
    myInfoLabel.setText(info);
  }

  private class ViewPort extends JPanel implements ZoomableViewport {
    private Point myMagnificationPoint = null;
    private Double myMagnification = 0.0;
    private BufferedImage myCachedImage = null;

    ViewPort() {
      super(new BorderLayout());
    }

    private static double magnificationToScale(double magnification) {
      return magnification < 0 ? 1f / (1 - magnification) : (1 + magnification);
    }

    protected Point convertToContentCoordinates(Point point) {
      return SwingUtilities.convertPoint(this, point, myContentComponent);
    }

    @Override
    public Magnificator getMagnificator() {
      return new Magnificator() {
        @Override
        public Point magnify(double scale, Point at) {
          myViewer.magnify(scale, at, () -> {
            myMagnificationPoint = null;
            myCachedImage = null;
            SwingUtilities.invokeLater(() -> repaint());
            return null;
          });
          return at;
        }
      };
    }

    @Override
    public void magnificationStarted(Point at) {
      myMagnificationPoint = at;
    }

    @Override
    public void magnificationFinished(double magnification) {
      Magnificator magnificator = getMagnificator();
      Point p = myMagnificationPoint;
      if (Double.compare(magnification, 0) != 0 && p != null) {
        Point inContentPoint = convertToContentCoordinates(p);
        magnificator.magnify(magnificationToScale(magnification), inContentPoint);
      }
    }

    @Override
    public void magnify(double magnification) {
      if (magnification == myMagnification) return;
      myMagnification = magnification;
      if (myCachedImage == null) {
        int clientWidth = myViewer.getClientWidth();
        int clientHeight = myViewer.getClientHeight();
        if (clientWidth <= 0 || clientHeight <= 0) return;

        BufferedImage image = ImageUtil.createImage(getGraphics(), clientWidth, clientHeight, BufferedImage.TYPE_INT_RGB);
        Graphics graphics = image.getGraphics();
        graphics.clipRect(0, 0, clientWidth, clientHeight);
        super.paint(graphics);
        myCachedImage = image;
      }

      repaint();
    }

    @Override
    public void paint(Graphics g) {
      if (myMagnificationPoint != null) {
        BufferedImage image = myCachedImage;
        if (image != null) {
          double scale = magnificationToScale(myMagnification);
          int xOffset = (int)(myMagnificationPoint.x - myMagnificationPoint.x * scale);
          int yOffset = (int)(myMagnificationPoint.y - myMagnificationPoint.y * scale);

          Rectangle clip = g.getClipBounds();

          g.setColor(myContentComponent.getBackground());
          g.fillRect(clip.x, clip.y, clip.width, clip.height);

          Graphics2D translated = (Graphics2D)g.create();
          translated.translate(xOffset, yOffset);
          translated.scale(scale, scale);

          UIUtil.drawImage(translated, image, 0, 0, null);
        }
      }
      else {
        super.paint(g);
      }
    }
  }
}
