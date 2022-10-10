// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.plugins.PluginNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.Urls;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Alexander Lobas
 */
public class PluginImagesComponent extends JPanel {
  private final Object myLock = new Object();
  private JComponent myParent;
  private final boolean myShowFullContent;
  private List<BufferedImage> myImages;
  private int myCurrentImage;
  private boolean myHovered;
  private Object myLoadingState;
  private JBPopup myFullScreenPopup;

  public PluginImagesComponent(@NotNull JComponent parent) {
    myParent = parent;
    myShowFullContent = false;

    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        handleClick(e);
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        myHovered = true;
        repaint();
      }

      @Override
      public void mouseExited(MouseEvent e) {
        myHovered = false;
        repaint();
      }
    });
  }

  private PluginImagesComponent(@NotNull List<BufferedImage> images, int currentImage) {
    myShowFullContent = true;
    myHovered = true;
    myImages = images;
    myCurrentImage = currentImage;
    setOpaque(false);

    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        handleClick(e);
      }
    });
  }

  public void show(@NotNull IdeaPluginDescriptor descriptor) {
    Object state;

    synchronized (myLock) {
      myImages = null;
      state = myLoadingState = new Object();
    }

    loadImages(descriptor, state);
    fullRepaint();
  }

  private void handleImages(@NotNull Object state, @Nullable List<BufferedImage> images) {
    synchronized (myLock) {
      if (myLoadingState != state) {
        return;
      }

      myLoadingState = null;
      myImages = images;
      myCurrentImage = 0;
    }

    if (myFullScreenPopup != null) {
      myFullScreenPopup.cancel();
      myFullScreenPopup = null;
    }

    fullRepaint();
  }

  private void fullRepaint() {
    Container parent = getParent();
    if (parent != null) {
      parent.doLayout();
      parent.revalidate();
      parent.repaint();
    }
  }

  private void loadImages(@NotNull IdeaPluginDescriptor descriptor, @NotNull Object state) {
    if (!(descriptor instanceof PluginNode node) || node.getExternalPluginIdForScreenShots() == null) {
      handleImages(state, null);
      return;
    }

    List<String> screenShots = node.getScreenShots();
    if (ContainerUtil.isEmpty(screenShots)) {
      handleImages(state, null);
      return;
    }

    ProcessIOExecutorService.INSTANCE.execute(() -> {
      List<BufferedImage> images = new ArrayList<>();
      File parentDir = new File(PathManager.getPluginTempPath(), "imageCache/" + node.getExternalPluginIdForScreenShots());

      for (String screenShot : screenShots) {
        try {
          String name = Objects.requireNonNull(StringUtil.substringAfterLast(screenShot, "/"));
          if (!name.contains(".")) {
            name += ".png";
          }
          File imageFile = new File(parentDir, name);
          if (!imageFile.exists()) {
            if (ApplicationManager.getApplication().isDisposed()) {
              return;
            }
            HttpRequests.request(Urls.newFromEncoded(screenShot)).productNameAsUserAgent().saveToFile(imageFile, null);
          }
          try (InputStream stream = new FileInputStream(imageFile)) {
            images.add(ImageIO.read(stream));
          }
        }
        catch (IOException e) {
          Logger.getInstance(PluginImagesComponent.class).error(e);
        }
      }

      ApplicationManager.getApplication().invokeLater(() -> handleImages(state, images), ModalityState.stateForComponent(this));
    });
  }

  @Override
  public Dimension getPreferredSize() {
    int width = 0;
    int height = 0;

    Container parent = getParent();
    if (parent != null) {
      boolean isImages;
      synchronized (myLock) {
        isImages = !ContainerUtil.isEmpty(myImages);
      }
      if (isImages) {
        int fullWidth = parent.getWidth();
        if (fullWidth > 0) {
          if (myParent != null) {
            fullWidth = Math.min(fullWidth, myParent.getWidth());
          }
          width = fullWidth;
          //height = (int)(width / 1.58) + JBUI.scale(28);
          height = (int)(width / 1.33) + JBUI.scale(28);
          //height = width / 2 + JBUI.scale(28);
        }
      }
    }

    return new Dimension(width, height);
  }

  private void handleClick(@NotNull MouseEvent e) {
    if (e.getButton() != MouseEvent.BUTTON1) {
      return;
    }

    Insets insets = getInsets();
    int x = insets.left;
    int y = insets.top;
    int width = getFullWidth() - insets.left - insets.right;
    int height = getHeight() - insets.top - insets.bottom;
    int offset = JBUI.scale(28);
    int actionOffset = JBUI.scale(8);
    int actionSize = getActionSize();
    int leftActionX = x + actionOffset;
    int rightActionX = x + width - actionOffset - actionSize;
    int actionY = y + (height - offset - actionSize) / 2;
    int mouseX = e.getX();
    int mouseY = e.getY();

    if (new Rectangle(leftActionX, actionY, actionSize, actionSize).contains(mouseX, mouseY)) {
      showNextImage(true);
    }
    else if (new Rectangle(rightActionX, actionY, actionSize, actionSize).contains(mouseX, mouseY)) {
      showNextImage(false);
    }
    else if (new Rectangle(x + offset, y, width - offset, height - offset).contains(mouseX, mouseY)) {
      handleFullScreen();
    }
    else {
      int count;
      synchronized (myLock) {
        if (ContainerUtil.isEmpty(myImages)) {
          return;
        }
        count = myImages.size();
      }

      int ovalSize = JBUI.scale(myShowFullContent ? 8 : 6);
      int ovalGap = JBUI.scale(14);
      int ovalsWidth = count * ovalSize + (count - 1) * ovalGap;
      int ovalX = x + (width - ovalsWidth) / 2;
      int ovalY = insets.top + height - (offset + ovalSize) / 2;
      Rectangle bounds = new Rectangle(ovalX, ovalY, ovalSize, ovalSize);

      for (int i = 0; i < count; i++) {
        if (bounds.contains(mouseX, mouseY)) {
          synchronized (myLock) {
            if (myCurrentImage != i) {
              myCurrentImage = i;
              repaint();
            }
          }
          return;
        }
        bounds.x += ovalSize + ovalGap;
      }
    }
  }

  private void handleFullScreen() {
    if (myFullScreenPopup != null) {
      myFullScreenPopup.cancel();
    }
    if (myShowFullContent) {
      return;
    }

    List<BufferedImage> images;
    int current;
    synchronized (myLock) {
      if (ContainerUtil.isEmpty(myImages)) {
        return;
      }
      images = myImages;
      current = myCurrentImage;
    }

    PluginImagesComponent component = new PluginImagesComponent(images, current);
    JPanel panel = new Wrapper(component);
    panel.setPreferredSize(getGraphicsConfiguration().getBounds().getSize());

    myFullScreenPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, component).createPopup();
    component.myFullScreenPopup = myFullScreenPopup;

    myFullScreenPopup.addListener(new JBPopupListener() {
      @Override
      public void beforeShown(@NotNull LightweightWindowEvent event) {
        Window window = SwingUtilities.getWindowAncestor(event.asPopup().getContent());
        window.setBackground(Gray.TRANSPARENT);
        window.setOpacity(0);
      }

      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        myFullScreenPopup = null;
        synchronized (myLock) {
          if (component.myCurrentImage != myCurrentImage) {
            myCurrentImage = component.myCurrentImage;
          }
        }
        repaint();
      }
    });

    myFullScreenPopup.showInScreenCoordinates(this, new Point());
  }

  private void showNextImage(boolean left) {
    synchronized (myLock) {
      if (myImages == null) {
        return;
      }
      int count = myImages.size();
      if (count < 2) {
        return;
      }
      if (left) {
        if (myCurrentImage > 0) {
          myCurrentImage--;
        }
        else {
          myCurrentImage = count - 1;
        }
      }
      else if (myCurrentImage < count - 1) {
        myCurrentImage++;
      }
      else {
        myCurrentImage = 0;
      }
    }
    repaint();
  }

  @Override
  public void paint(Graphics g) {
    if (myShowFullContent) {
      Graphics2D g2d = (Graphics2D)g.create();

      try {
        g2d.setBackground(Gray.get(128, 128));
        g2d.clearRect(0, 0, getWidth(), getHeight());
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.95f));

        paintComponent(g);
        return;
      }
      finally {
        g2d.dispose();
      }
    }
    super.paint(g);
  }

  @Override
  protected void paintComponent(Graphics g) {
    int count;
    int current;
    BufferedImage image;

    synchronized (myLock) {
      if (myImages == null) {
        return;
      }
      count = myImages.size();
      if (count == 0) {
        return;
      }
      current = myCurrentImage;
      image = myImages.get(current);
    }

    Insets insets = getInsets();
    int x = insets.left;
    int y = insets.top;
    int width = getFullWidth() - insets.left - insets.right;
    int height = getHeight() - insets.top - insets.bottom;
    int offset = JBUI.scale(28);

    if (!myShowFullContent) {
      g.setColor(PluginManagerConfigurable.MAIN_BG_COLOR);
      g.fillRect(x, y, width, height);
    }

    int imageWidth = image.getWidth();
    int imageHeight = image.getHeight();

    if (myShowFullContent) {
      int paintWidth = width - x - 2 * offset;
      int paintHeight = height - y - offset;

      if (imageWidth <= paintWidth && imageHeight <= paintHeight) {
        g.drawImage(image, x + offset + (paintWidth - imageWidth) / 2, y + (paintHeight - imageHeight) / 2, null);
      }
      else {
        int newHeight = paintWidth / 2;

        if (newHeight < paintHeight) {
          int newY = y + (paintHeight - newHeight) / 2;
          g.drawImage(image, x + offset, newY, x + offset + paintWidth, newY + newHeight, 0, 0, imageWidth, imageHeight, null);
        }
        else {
          g.drawImage(image, x + offset, y, x + offset + paintWidth, y + imageHeight, 0, 0, imageWidth, imageHeight, null);
        }
      }
    }
    else {
      g.drawImage(image, x + offset, y, width - offset, height - offset, 0, 0, imageWidth, imageHeight, null);
    }

    Graphics2D g2 = (Graphics2D)g.create();

    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

      if (!myShowFullContent) {
        g2.setColor(Gray.xD1);
        g2.draw(new RoundRectangle2D.Float(x, y, width - 1, height - offset, 7, 7));
      }

      int ovalSize = JBUI.scale(myShowFullContent ? 8 : 6);
      int ovalGap = JBUI.scale(14);
      int ovalsWidth = count * ovalSize + (count - 1) * ovalGap;
      int ovalX = x + (width - ovalsWidth) / 2;
      int ovalY = insets.top + height - (offset + ovalSize) / 2;

      for (int i = 0; i < count; i++) {
        if (i == current) {
          g2.setColor(Gray.x6E);
          g2.fillOval(ovalX, ovalY, ovalSize, ovalSize);
        }
        else {
          g2.setColor(Gray.xD1);
          g2.drawOval(ovalX, ovalY, ovalSize - 1, ovalSize - 1);
        }
        ovalX += ovalSize + ovalGap;
      }

      if (myHovered && count > 1) {
        paintAction(g2, x, y, width, height, offset, true);
        paintAction(g2, x, y, width, height, offset, false);
      }
    }
    finally {
      g2.dispose();
    }
  }

  private int getFullWidth() {
    int fullWidth = getWidth();
    if (myParent != null) {
      fullWidth = Math.min(fullWidth, myParent.getWidth());
    }
    return fullWidth;
  }

  private void paintAction(Graphics2D g2, int x, int y, int width, int height, int offset, boolean left) {
    int actionOffset = JBUI.scale(8);
    int actionSize = getActionSize();

    int actionX = left ? (x + actionOffset) : (x + width - actionOffset - actionSize);

    int actionY = y + (height - offset - actionSize) / 2;

    g2.setColor(Gray.xF8);
    g2.fillOval(actionX, actionY, actionSize, actionSize);
    g2.setColor(Gray.xD1);
    g2.drawOval(actionX, actionY, actionSize, actionSize);

    Icon icon = left ? AllIcons.Actions.ArrowCollapse : AllIcons.Actions.ArrowExpand;
    int iconWidth = icon.getIconWidth();
    int iconHeight = icon.getIconHeight();

    actionX += (actionSize - iconWidth) / 2;
    actionY += (actionSize - iconHeight) / 2;
    icon.paintIcon(this, g2, actionX, actionY);
  }

  private int getActionSize() {
    return JBUI.scale(myShowFullContent ? 48 : 28);
  }
}