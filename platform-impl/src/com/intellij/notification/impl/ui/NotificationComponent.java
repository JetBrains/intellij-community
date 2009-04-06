package com.intellij.notification.impl.ui;

import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.impl.*;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.util.MinimizeButton;
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
import java.lang.ref.WeakReference;

/**
 * @author spleaner
 */
public class NotificationComponent extends JLabel implements NotificationModelListener {
  private static final Icon EMPTY_ICON = IconLoader.getIcon("/ide/notifications.png");
  private NotificationModel myModel;

  private WeakReference<JBPopup> myPopupRef;

  public NotificationComponent(@NotNull final IdeNotificationArea area) {
    myModel = area.getModel();

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

    setText(" ");
    setIcon(EMPTY_ICON);
  }

  @Override
  public void removeNotify() {
    myModel.removeListener(this); // clean up

    super.removeNotify();
  }

  public void addNotify() {
    super.addNotify();

    myModel.addListener(this);
  }

  private void showList() {
    if (myPopupRef != null) {
      final JBPopup popup = myPopupRef.get();
      if (popup != null) {
        popup.cancel();
      }

      myPopupRef = null;
    }

    if (myModel.getCount() == 1) {
      final NotificationImpl notification = myModel.getFirst();
      if (notification != null) {
        performNotificationAction(notification);
        return;
      }
    }

    assert myPopupRef == null;

    final NotificationsListPanel listPanel = new NotificationsListPanel(myModel);

    final ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(listPanel, listPanel.getPreferredFocusedComponent());
    final JBPopup popup =
        builder.setResizable(true)
            .setMinSize(NotificationsListPanel.getMinSize())
            .setDimensionServiceKey(null, "NotificationsPopup", true)
            .setCancelOnClickOutside(false)
            .setBelongsToGlobalPopupStack(false)
            .setCancelButton(new MinimizeButton("Hide"))
            .setMovable(true)
            .setRequestFocus(true)
            .setTitle("Notifications")
            .createPopup();

    myPopupRef = new WeakReference<JBPopup>(popup);
    popup.showInCenterOf(SwingUtilities.getRootPane(this));
  }

  public void update(@Nullable final NotificationImpl notification, final int size, final boolean add) {
    if (notification != null) {
      final NotificationSettings settings = NotificationsConfiguration.getSettings(notification);
      setIcon(NotificationUtil.getIcon(notification));

      if (add) {
        notifyByBaloon(notification, settings);
      }
    } else {
      setIcon(EMPTY_ICON);
    }

    if (size == 0) {
      setForeground(getBackground());
      setText("0");
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

      final Balloon balloon = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(html, NotificationUtil.getIcon(notification),
          NotificationUtil.getColor(notification), null)
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
