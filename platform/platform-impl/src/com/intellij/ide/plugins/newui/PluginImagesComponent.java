// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.plugins.marketplace.MarketplaceRequests;
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
import com.intellij.ui.JBColor;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.IOUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Alexander Lobas
 */
@ApiStatus.Internal
public final class PluginImagesComponent extends JPanel {
  private static final Color CURRENT_IMAGE_FILL_COLOR =
    JBColor.namedColor("Plugins.ScreenshotPagination.CurrentImage.fillColor", new JBColor(0x6C707E, 0xCED0D6));

  private static final int None = -1;
  private static final int NextImage = -2;
  private static final int PrevImage = -3;
  private static final int FullScreen = -4;

  private final Cursor myHandCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);

  private final Object myLock = new Object();
  private JComponent myParent;
  private final boolean myShowFullContent;
  private List<Image> myImages;
  private int myCurrentImage;
  private boolean myHovered;
  private Object myLoadingState;
  private Object myShowState;
  private JBPopup myFullScreenPopup;

  public PluginImagesComponent() {
    myShowFullContent = false;

    MouseAdapter listener = new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        handleClick(e);
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        handleMMove(e);
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
    };
    addMouseListener(listener);
    addMouseMotionListener(listener);
  }

  private PluginImagesComponent(@NotNull List<Image> images, int currentImage) {
    myShowFullContent = true;
    myHovered = true;
    myImages = images;
    myCurrentImage = currentImage;
    setOpaque(false);

    MouseAdapter listener = new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        handleClick(e);
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        handleMMove(e);
      }
    };
    addMouseListener(listener);
    addMouseMotionListener(listener);
  }

  public void setParent(@NotNull JComponent parent) {
    myParent = parent;
  }

  public void show(@NotNull PluginUiModel model) {
    Object state;

    synchronized (myLock) {
      myImages = null;
      state = myLoadingState = new Object();
      myShowState = null;
    }

    loadImages(model, state);
    fullRepaint();
  }

  private void handleImages(@NotNull Object state, @Nullable List<Image> images) {
    synchronized (myLock) {
      if (myLoadingState != state) {
        return;
      }

      myShowState = state;
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

  private void loadImages(@NotNull PluginUiModel model, @NotNull Object state) {
    if (!model.isFromMarketplace() ||
        model.getExternalPluginIdForScreenShots() == null ||
        ApplicationManager.getApplication().isHeadlessEnvironment()) {
      handleImages(state, null);
      return;
    }

    List<String> screenShots = model.getScreenShots();
    if (ContainerUtil.isEmpty(screenShots)) {
      handleImages(state, null);
      return;
    }

    ProcessIOExecutorService.INSTANCE.execute(() -> {
      List<Image> images = new ArrayList<>();
      File parentDir = new File(PathManager.getPluginTempPath(), "imageCache/" + model.getExternalPluginIdForScreenShots());

      for (String screenShot : screenShots) {
        try {
          String name = Objects.requireNonNull(StringUtil.substringAfterLast(screenShot, "/"));
          File imageFile = new File(parentDir, name);
          if (ApplicationManager.getApplication().isDisposed()) {
            return;
          }
          MarketplaceRequests.Companion.readOrUpdateFile(imageFile.toPath(), screenShot, null, "", stream -> {
            IOUtil.closeSafe(Logger.getInstance(PluginImagesComponent.class), stream);
            return new Object();
          });
          Image image = Toolkit.getDefaultToolkit().getImage(imageFile.getAbsolutePath());
          if (image == null) {
            Logger.getInstance(PluginImagesComponent.class)
              .info("=== Plugin: " + model.getPluginId() + " screenshot: " + name + " not loaded ===");
          }
          else {
            images.add(image);
            if (images.size() >= 10) break;
          }
        }
        catch (IOException e) {
          // IO errors such as image decoding problems are expected and must not be treated as IDE errors
          Logger.getInstance(PluginImagesComponent.class).warn(e);
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
        width = getFullWidth();
        height = (int)(width / 1.58) + JBUI.scale(28);
      }
    }

    return new Dimension(width, height);
  }

  private int getFullWidth() {
    Container parent = getParent();
    if (parent != null && myParent != null) {
      return myParent.getWidth() - parent.getInsets().left;
    }
    return getWidth();
  }

  private void handleMMove(@NotNull MouseEvent e) {
    int state = handleEvent(e, true);
    Cursor newCursor = state == None ? null : myHandCursor;

    if (getCursor() != newCursor) {
      setCursor(newCursor);
    }
  }

  private void handleClick(@NotNull MouseEvent e) {
    if (e.getButton() != MouseEvent.BUTTON1) {
      return;
    }

    int state = handleEvent(e, false);
    if (state == None) {
      return;
    }

    if (state >= 0) {
      synchronized (myLock) {
        if (myCurrentImage != state) {
          myCurrentImage = state;
          repaint();
        }
      }
    }
    else if (state == FullScreen) {
      handleFullScreen();
    }
    else {
      showNextImage(state == PrevImage);
    }
  }

  private int handleEvent(@NotNull MouseEvent e, boolean cutFullScreen) {
    Insets insets = getInsets();
    int x = insets.left;
    int y = insets.top;
    int width = getFullWidth() - insets.left - insets.right;
    int height = getHeight() - insets.top - insets.bottom;
    int offset = JBUI.scale(28);
    int offset2 = offset * 2;
    int actionOffset = JBUI.scale(8);
    int actionSize = getActionSize();
    int leftActionX = x + actionOffset;
    int rightActionX = x + width - actionOffset - actionSize;
    int actionY = y + (height - offset - actionSize) / 2;
    int mouseX = e.getX();
    int mouseY = e.getY();

    if (new Rectangle(leftActionX, actionY, actionSize, actionSize).contains(mouseX, mouseY)) {
      return PrevImage;
    }
    if (new Rectangle(rightActionX, actionY, actionSize, actionSize).contains(mouseX, mouseY)) {
      return NextImage;
    }
    if (new Rectangle(x + offset, y, width - offset2, height - offset).contains(mouseX, mouseY)) {

      if (cutFullScreen && !new Rectangle(x + offset2, y, width - 2 * offset2, height - offset).contains(mouseX, mouseY)) {
        return None;
      }
      return FullScreen;
    }

    int count;
    synchronized (myLock) {
      if (ContainerUtil.isEmpty(myImages)) {
        return None;
      }
      count = myImages.size();
    }

    if (count < 2) {
      return None;
    }

    int ovalSize = JBUI.scale(myShowFullContent ? 8 : 6);
    int ovalGap = JBUI.scale(14);
    int ovalsWidth = count * ovalSize + (count - 1) * ovalGap;
    int ovalX = x + (width - ovalsWidth) / 2;
    int ovalY = insets.top + height - (offset + ovalSize) / 2;
    Rectangle bounds = new Rectangle(ovalX, ovalY, ovalSize, ovalSize);

    for (int i = 0; i < count; i++) {
      if (bounds.contains(mouseX, mouseY)) {
        return i;
      }
      bounds.x += ovalSize + ovalGap;
    }

    return None;
  }

  private void handleFullScreen() {
    if (myFullScreenPopup != null) {
      myFullScreenPopup.cancel();
    }
    if (myShowFullContent) {
      return;
    }

    List<Image> images;
    int current;
    Object showState;
    synchronized (myLock) {
      if (ContainerUtil.isEmpty(myImages)) {
        return;
      }
      images = myImages;
      current = myCurrentImage;
      showState = myShowState;
    }

    PluginImagesComponent component = new PluginImagesComponent(images, current);
    JPanel panel = new Wrapper(component);
    panel.setPreferredSize(getGraphicsConfiguration().getBounds().getSize());

    myFullScreenPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, component).createPopup();
    component.myFullScreenPopup = myFullScreenPopup;
    component.myShowState = showState;

    myFullScreenPopup.addListener(new JBPopupListener() {
      @Override
      public void beforeShown(@NotNull LightweightWindowEvent event) {
        Window window = SwingUtilities.getWindowAncestor(event.asPopup().getContent());
        window.setBackground(Gray.TRANSPARENT);
        window.setOpacity(0.95f);
      }

      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        myFullScreenPopup = null;
        synchronized (myLock) {
          if (myShowState == component.myShowState && component.myCurrentImage != myCurrentImage) {
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
        g2d.setBackground(Gray.get(158, 158));
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
    Image image;

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

    int imageX = insets.left + offset;
    int imageY = insets.top;
    int imageWidth = image.getWidth(this);
    int imageHeight = image.getHeight(this);
    int paintWidth = width - 2 * offset;
    int paintHeight = height - offset;

    if (imageWidth <= paintWidth && imageHeight <= paintHeight) {
      StartupUiUtil.drawImage(g, image, imageX + (paintWidth - imageWidth) / 2, imageY + (paintHeight - imageHeight) / 2, this);
    }
    else {
      int zoomedHeight = imageHeight * paintWidth / imageWidth;
      int zoomedWidth = imageWidth * paintHeight / imageHeight;

      if (zoomedWidth <= paintWidth) {
        StartupUiUtil.drawImage(g, image, new Rectangle(imageX + (paintWidth - zoomedWidth) / 2, imageY, zoomedWidth, paintHeight), this);
      }
      else if (zoomedHeight <= paintHeight) {
        StartupUiUtil.drawImage(g, image, new Rectangle(imageX, imageY + (paintHeight - zoomedHeight) / 2, paintWidth, zoomedHeight), this);
      }
      else {
        StartupUiUtil.drawImage(g, image, new Rectangle(imageX, imageY, paintWidth, paintHeight), this);
      }
    }

    Graphics2D g2 = (Graphics2D)g.create();

    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

      if (!myShowFullContent) {
        g2.setColor(PluginManagerConfigurable.SEARCH_FIELD_BORDER_COLOR);
        g2.draw(new RoundRectangle2D.Float(x, y, width - 1, height - offset, 7, 7));
      }

      if (count < 2) {
        return;
      }

      int ovalSize = JBUI.scale(myShowFullContent ? 8 : 6);
      int ovalGap = JBUI.scale(14);
      int ovalsWidth = count * ovalSize + (count - 1) * ovalGap;
      int ovalX = x + (width - ovalsWidth) / 2;
      int ovalY = insets.top + height - (offset + ovalSize) / 2;

      for (int i = 0; i < count; i++) {
        if (i == current) {
          g2.setColor(CURRENT_IMAGE_FILL_COLOR);
          g2.fillOval(ovalX, ovalY, ovalSize, ovalSize);
        }
        else {
          g2.setColor(JBUI.CurrentTheme.Button.buttonOutlineColorStart(false));
          g2.drawOval(ovalX, ovalY, ovalSize - 1, ovalSize - 1);
        }
        ovalX += ovalSize + ovalGap;
      }

      if (myHovered) {
        paintAction(g2, x, y, width, height, offset, true);
        paintAction(g2, x, y, width, height, offset, false);
      }
    }
    finally {
      g2.dispose();
    }
  }

  private void paintAction(Graphics2D g2, int x, int y, int width, int height, int offset, boolean left) {
    int actionOffset = JBUI.scale(8);
    int actionSize = getActionSize();

    int actionX = left ? (x + actionOffset) : (x + width - actionOffset - actionSize);

    int actionY = y + (height - offset - actionSize) / 2;

    g2.setColor(JBUI.CurrentTheme.Button.buttonColorStart());
    g2.fillOval(actionX, actionY, actionSize, actionSize);
    g2.setColor(JBUI.CurrentTheme.Button.buttonOutlineColorStart(false));
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