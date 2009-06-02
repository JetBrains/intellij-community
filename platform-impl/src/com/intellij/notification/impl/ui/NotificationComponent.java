package com.intellij.notification.impl.ui;

import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.impl.*;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.BalloonLayout;
import com.intellij.util.ui.UIUtil;
import com.intellij.concurrency.JobScheduler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.TimeUnit;

/**
 * @author spleaner
 */
public class NotificationComponent extends JLabel implements NotificationModelListener {
  private static final Icon EMPTY_ICON = IconLoader.getIcon("/ide/notifications.png");
  private NotificationModel myModel;

  private NotificationsListPanel myPopup;

  private BlinkIconWrapper myCurrentIcon;
  private boolean myBlinkIcon = false;

  public NotificationComponent(@NotNull final IdeNotificationArea area) {
    myModel = area.getModel();

    myPopup = new NotificationsListPanel(this);

    setFont(UIManager.getFont("Label.font").deriveFont(Font.PLAIN, 10));
    setIconTextGap(3);

    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(final MouseEvent e) {
        if (UIUtil.isActionClick(e)) {
          showList();
        }
      }
    });

    setText("");

    myCurrentIcon = new BlinkIconWrapper(EMPTY_ICON, false);
    setIcon(myCurrentIcon);

    JobScheduler.getScheduler().scheduleAtFixedRate(new IconBlinker(), (long)1, (long)1, TimeUnit.SECONDS);
  }

  @Override
  public void removeNotify() {
    myModel.removeListener(this); // clean up
    myPopup.clear();

    super.removeNotify();
  }

  public void addNotify() {
    super.addNotify();

    myModel.addListener(this);
  }

  private void showList() {
    if (myPopup.showOrHide()) {
      myBlinkIcon = false;
    }
  }

  public void update(@Nullable final NotificationImpl notification, final int size, final boolean add) {
    if (notification != null) {
      final NotificationSettings settings = NotificationsConfiguration.getSettings(notification);
      myCurrentIcon = new BlinkIconWrapper(notification.getIcon(), true);
      setIcon(myCurrentIcon);

      if (add) {
        myBlinkIcon = !myPopup.isShowing();
        notifyByBaloon(notification, settings);
      }
    } else {
      myCurrentIcon = new BlinkIconWrapper(EMPTY_ICON, false);
      setIcon(myCurrentIcon);
    }

    if (size == 0) {
      setForeground(getBackground());
      setText("");
    } else {
      setForeground(Color.BLACK);
      setText(String.format("%d", size));
    }

    repaint();
  }

  @SuppressWarnings({"SSBasedInspection"})
  public void notificationsAdded(@NotNull final NotificationImpl... notifications) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        update(notifications[notifications.length - 1], myModel.getCount(), true);
      }
    });
  }

  @SuppressWarnings({"SSBasedInspection"})
  public void notificationsRemoved(@NotNull final NotificationImpl... notifications) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        update(myModel.getFirst(), myModel.getCount(), false);
      }
    });
  }

  private void notifyByBaloon(final NotificationImpl notification, final NotificationSettings settings) {
    if (NotificationDisplayType.BALOON.equals(settings.getDisplayType())) {
      final String html = String.format("%s", notification.getName());

      final Balloon balloon = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(html, notification.getIcon(),
          notification.getBackgroundColor(), null)
          .setCloseButtonEnabled(true).setShowCallout(false).setFadeoutTime(3000).setHideOnClickOutside(false).setHideOnKeyOutside(false).setHideOnFrameResize(false)
          .setClickHandler(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
              performNotificationAction(notification);
            }
          }, true).createBalloon();

      final Runnable show = new Runnable() {
        public void run() {
          final Window window = SwingUtilities.getWindowAncestor(NotificationComponent.this);
          if (window instanceof IdeFrameImpl) {
            final BalloonLayout balloonLayout = ((IdeFrameImpl) window).getBalloonLayout();
            balloonLayout.add(balloon);
          }
        }
      };

      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          show.run();
        }
      });
    }
  }

  public NotificationModel getModel() {
    return myModel;
  }

  private void performNotificationAction(final NotificationImpl notification) {
    final NotificationListener.Continue onClose = notification.getListener().perform();
    if (onClose == NotificationListener.Continue.REMOVE) {
      myModel.remove(notification);
    }
  }

  private void blinkIcon(boolean visible) {
    myCurrentIcon.setPaint(visible);
  }

  private class IconBlinker implements Runnable {
    private boolean myVisible;

    public void run() {
      myVisible = !myVisible;
      blinkIcon(myVisible);
    }
  }

  private class BlinkIconWrapper implements Icon {
    private Icon myOriginal;
    private boolean myPaint;
    private boolean myBlink;

    private BlinkIconWrapper(@NotNull final Icon original, final boolean blink) {
      myOriginal = original;
      myBlink = blink;
    }

    public void setPaint(final boolean paint) {
      if (paint != myPaint) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            repaint();
          }
        });
      }

      myPaint = paint;
    }

    public void paintIcon(Component c, Graphics g, int x, int y) {
      if (!myBlink || !myBlinkIcon || myPaint) {
        myOriginal.paintIcon(c, g, x, y);
      }
    }

    public int getIconWidth() {
      return myOriginal.getIconWidth();
    }

    public int getIconHeight() {
      return myOriginal.getIconHeight();
    }
  }
}
