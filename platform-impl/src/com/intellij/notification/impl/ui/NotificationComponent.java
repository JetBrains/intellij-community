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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author spleaner
 */
public class NotificationComponent extends JLabel implements NotificationModelListener {
  private static final Icon EMPTY_ICON = IconLoader.getIcon("/ide/notifications.png");
  private NotificationModel myModel;

  private NotificationsListPanel myPopup;

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
    setIcon(EMPTY_ICON);
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
    myPopup.showOrHide();
  }

  public void update(@Nullable final NotificationImpl notification, final int size, final boolean add) {
    if (notification != null) {
      final NotificationSettings settings = NotificationsConfiguration.getSettings(notification);
      setIcon(notification.getIcon());

      if (add) {
        notifyByBaloon(notification, settings);
      }
    } else {
      setIcon(EMPTY_ICON);
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
}
