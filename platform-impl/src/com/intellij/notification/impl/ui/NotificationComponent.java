package com.intellij.notification.impl.ui;

import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.impl.NotificationImpl;
import com.intellij.notification.impl.*;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.impl.IdeGlassPaneEx;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * @author spleaner
 */
public class NotificationComponent extends JLabel implements NotificationModelListener {
  private static final Icon EMPTY_ICON = new ImageIcon(new BufferedImage(16, 16, BufferedImage.TYPE_4BYTE_ABGR));
  private NotificationModel myModel;

  public NotificationComponent(@NotNull final IdeNotificationArea area) {
    myModel = area.getModel();

    setFont(UIManager.getFont("Label.font").deriveFont(Font.PLAIN, 10));
    setIconTextGap(3);

    addMouseListener(new MouseAdapter() {
      public void mouseClicked(final MouseEvent e) {
        showList();
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
    final JRootPane pane = SwingUtilities.getRootPane(this);
    final Component glassPane = pane.getGlassPane();
    if (glassPane instanceof IdeGlassPaneEx) {
      ((IdeGlassPaneEx) glassPane).add(new NotificationsListPanelWrapper(this, new NotificationsListPanel(myModel), (Container) glassPane, pane));
    }
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

    setText(size == 0 ? " " : String.format("%d", size));
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
          NotificationUtil.getColor(notification), null).createBalloon();

      final Runnable show = new Runnable() {
        public void run() {
          final Point point = new Point(getIcon().getIconWidth() / 2 + getInsets().left, 0);
          balloon.show(new RelativePoint(NotificationComponent.this, point), Balloon.Position.above);
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
}
