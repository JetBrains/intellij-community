package com.intellij.notification.impl.ui;

import com.intellij.concurrency.JobScheduler;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.impl.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.BalloonLayout;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

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
public class NotificationComponent extends JLabel implements NotificationModelListener<Notification> {
  private static final Icon EMPTY_ICON = IconLoader.getIcon("/ide/notifications.png");

  private NotificationsListPanel myPopup;

  private BlinkIconWrapper myCurrentIcon;
  private boolean myBlinkIcon = false;
  private IdeNotificationArea myArea;
  private Notification myLatest;

  public NotificationComponent(@NotNull final IdeNotificationArea area) {
    myArea = area;

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

  private static NotificationsManager getManager() {
    return NotificationsManager.getNotificationsManager();
  }

  @Override
  public void removeNotify() {
    getManager().removeListener(this); // clean up

    if (myPopup != null) {
      myPopup.clear();
      myPopup = null;
    }

    myLatest = null;

    super.removeNotify();
  }

  public void addNotify() {
    super.addNotify();

    getManager().addListener(this);
  }

  private void showList() {
    if (myPopup == null) {
      myPopup = new NotificationsListPanel(this);
    }

    if (myPopup.showOrHide()) {
      myBlinkIcon = false;
    } else {
      myPopup.clear();
      myPopup = null;
    }
  }

  public void updateStatus(final boolean add) {
    final NotificationsManager manager = getManager();
    final int count = manager.count(getProject());
    if (count > 0) {
      final Notification latest = manager.getLatestNotification(getProject());
      assert latest != null;

      if (latest != myLatest) {
        final NotificationSettings settings = NotificationsConfiguration.getSettings(latest);
        myCurrentIcon = new BlinkIconWrapper(latest.getIcon(), true);
        setIcon(myCurrentIcon);

        if (add) {
          myBlinkIcon = myPopup != null && !myPopup.isShowing();
          notifyByBaloon(latest, settings);
        }

        myLatest = latest;
      }

      setForeground(Color.BLACK);
      setText(String.format("%d", count));
    } else {
      myCurrentIcon = new BlinkIconWrapper(EMPTY_ICON, false);
      setIcon(myCurrentIcon);
      setForeground(getBackground());
      setText("");
    }

    repaint();
  }

  public Project getProject() {
    return myArea.getProject();
  }

  @SuppressWarnings({"SSBasedInspection"})
  public void notificationsAdded(@NotNull final Notification... notifications) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        updateStatus(true);
      }
    });
  }

  @SuppressWarnings({"SSBasedInspection"})
  public void notificationsRemoved(@NotNull final Notification... notifications) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        updateStatus(false);
      }
    });
  }

  private void notifyByBaloon(final Notification notification, final NotificationSettings settings) {
    if (NotificationDisplayType.BALLOON.equals(settings.getDisplayType())) {
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

  private static void performNotificationAction(final Notification notification) {
    final NotificationListener.Continue onClose = notification.getListener().perform();
    if (onClose == NotificationListener.Continue.REMOVE) {
      getManager().remove(notification);
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
