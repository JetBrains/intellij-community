package com.intellij.notification.impl.ui;

import com.intellij.notification.NotificationListener;
import com.intellij.notification.impl.NotificationImpl;
import com.intellij.notification.impl.NotificationModel;
import com.intellij.notification.impl.NotificationModelListener;
import com.intellij.notification.impl.NotificationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author spleaner
 */
public class NotificationsListPanel extends JPanel implements NotificationModelListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.notification.impl.ui.NotificationsListPanel");
  private JList myList;
  private NotificationModel myNotificationsModel;
  private NotificationsListModel myDataModel;
  private JScrollPane myScrollPane;
  private JLabel myLabel;
  private JPanel myInnerPanel;
  private static final String EMPTY_CARD = "EMPTY";
  private static final String LIST_CARD = "LIST";

  public NotificationsListPanel(@NotNull final NotificationModel notificationsModel) {
    setLayout(new BorderLayout());

    setBorder(BorderFactory.createLineBorder(new Color(100, 100, 100), 1));

    myNotificationsModel = notificationsModel;
    myDataModel = new NotificationsListModel(myNotificationsModel);

    notificationsModel.addListener(this);

    myList = new JList(myDataModel) {
      @Override
      public String getToolTipText(final MouseEvent event) {
        final int i = locationToIndex(event.getPoint());
        if (i >= 0) {
          final Object o = getModel().getElementAt(i);
          if (o instanceof NotificationImpl) {
            return ((NotificationImpl) o).getDescription();
          }
        }

        return null;
      }
    };
    myList.setCellRenderer(new NotificationsListRenderer());
    myList.getSelectionModel().setSelectionInterval(0, 0);
    myList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(final MouseEvent e) {
        if (e.getClickCount() % 2 == 0) {
          final int index = myList.locationToIndex(e.getPoint());
          final NotificationImpl notification = myNotificationsModel.get(index);
          if (notification != null) {
            performNotificationAction(notification);
          }
        }
      }
    });
    
    myScrollPane = new JScrollPane(myList);
    myScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    myScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    myScrollPane.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
    myScrollPane.setBackground(Color.WHITE);

    myLabel = new JLabel("Notifications");
    myLabel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    myLabel.setOpaque(true);
    myLabel.setFont(UIManager.getFont("Label.font").deriveFont(Font.PLAIN, 10));
    myLabel.setHorizontalAlignment(JLabel.CENTER);
    myLabel.setBackground(new Color(171, 200, 226));
    myLabel.setForeground(Color.BLACK);
    add(myLabel, BorderLayout.NORTH);


    myInnerPanel = new JPanel(new CardLayout()) {{
      add(new JPanel() {{
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        setBackground(Color.WHITE);
        add(new JLabel("No notifications."));
      }}, EMPTY_CARD);
      
      add(myScrollPane, LIST_CARD);
    }};

    switchLayout();

    add(myInnerPanel, BorderLayout.CENTER);
  }

  @Override
  public void removeNotify() {
    myNotificationsModel.removeListener(this);
    
    super.removeNotify();
  }

  public void notificationsAdded(@NotNull final NotificationImpl... notification) {
    myDataModel.rebuildList();
    switchLayout();
  }

  public void notificationsRemoved(@NotNull final NotificationImpl... notification) {
    myDataModel.rebuildList();
    switchLayout();
  }

  private void switchLayout() {
    final int count = myNotificationsModel.getCount();

    final CardLayout layout = (CardLayout) myInnerPanel.getLayout();

    if (count > 0) {
      layout.show(myInnerPanel, LIST_CARD);
    } else {
      layout.show(myInnerPanel, EMPTY_CARD);
    }
  }

  private void performNotificationAction(final NotificationImpl notification) {
    final NotificationListener.OnClose onClose = notification.getListener().perform();
    if (onClose == NotificationListener.OnClose.REMOVE) {
      myNotificationsModel.remove(notification);
    }
  }

  @Override
  public Dimension getPreferredSize() {
    final int width = 400;

    int count = myDataModel.getSize();
    if (count > 0) {
      final int h = myLabel.getPreferredSize().height;
      final Insets i0 = getInsets();
      final Insets i = myList.getInsets();
      final Insets i2 = myScrollPane.getInsets();
      final Rectangle r = myList.getCellBounds(0, 0);
      final int vInsets = i.top + i.bottom + i2.top + i2.bottom + i0.top + i0.bottom;
      count = count < 3 ? 3 : count;
      if (count < 6) {
        return new Dimension(width, count * r.height + vInsets + h);
      } else {
        return new Dimension(width, 6 * r.height + vInsets + h);
      }
    } else {
      return new Dimension(width, 60);
    }
  }

  public NotificationModel getNotificationsModel() {
    return myNotificationsModel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myList;
  }

  public void dispose() {
  }

  public void removeSelected() {
    final ListSelectionModel model = myList.getSelectionModel();
    if (!model.isSelectionEmpty()) {
      final int min = model.getMinSelectionIndex();
      final int max = model.getMaxSelectionIndex();

      final List<NotificationImpl> tbr = new ArrayList<NotificationImpl>();
      for (int i = min; i <= max; i++) {
        if (model.isSelectedIndex(i)) {
          tbr.add(myNotificationsModel.get(i));
        }
      }

      if (tbr.size() > 0) {
        myNotificationsModel.remove(tbr.toArray(new NotificationImpl[tbr.size()]));

        final int toSelect = Math.min(min, myNotificationsModel.getCount() - 1);
        model.clearSelection();
        if (toSelect >= 0) {
          model.setSelectionInterval(toSelect, toSelect);
          myList.scrollRectToVisible(myList.getCellBounds(toSelect, toSelect));
        }
      }
    }
  }

  private static class NotificationsListRenderer extends JPanel implements ListCellRenderer {
    public static final Border SEPARATION_BORDER = BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(204, 204, 204));
    public static final Border DEFAULT_BORDER = BorderFactory.createEmptyBorder(5, 3, 3, 3);

    private JLabel myTitleLabel;
    private JLabel myTimestampLabel;

    private NotificationsListRenderer() {
      setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
      setBorder(BorderFactory.createCompoundBorder(SEPARATION_BORDER, DEFAULT_BORDER));

      myTitleLabel = new JLabel();
      add(myTitleLabel);
      add(Box.createHorizontalGlue());

      myTimestampLabel = new JLabel();
      myTimestampLabel.setFont(UIManager.getFont("Label.font").deriveFont(Font.PLAIN, 10));
      myTimestampLabel.setForeground(Color.GRAY);

      add(myTimestampLabel);
    }

    public Component getListCellRendererComponent(final JList list, final Object value, final int index, final boolean isSelected, final boolean cellHasFocus) {
      LOG.assertTrue(value instanceof NotificationImpl);
      final NotificationImpl notification = (NotificationImpl) value;

      setIcon(NotificationUtil.getIcon(notification));
      final Color color = list.getSelectionBackground();
      setBackground(isSelected ? new Color(color.getRed(), color.getGreen(), color.getBlue(), 80) : list.getBackground());

      if (cellHasFocus) {
        setBorder(BorderFactory.createCompoundBorder(SEPARATION_BORDER,
            BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, color),
                BorderFactory.createEmptyBorder(4, 2, 2, 2))));
      } else {
        setBorder(BorderFactory.createCompoundBorder(SEPARATION_BORDER, DEFAULT_BORDER));
      }

      setTitle(notification.getName());
      setTimestamp(DateFormatUtil.formatDate(new Date(), notification.getDate()));
      return this;
    }

    private void setIcon(@NotNull final Icon icon) {
      myTitleLabel.setIcon(icon);
    }

    private void setTitle(@NotNull final String title) {
      myTitleLabel.setText(title);
    }

    private void setTimestamp(@NotNull final String component) {
      myTimestampLabel.setText(component);
    }
  }

  private static class NotificationsListModel extends AbstractListModel {
    private NotificationModel myNotificationsModel;
    private List<NotificationImpl> myNotifications = new ArrayList<NotificationImpl>();

    private NotificationsListModel(@NotNull final NotificationModel notificationsModel) {
      myNotificationsModel = notificationsModel;

      rebuildList();
    }

    public int getSize() {
      return myNotifications.size();
    }

    public Object getElementAt(final int index) {
      return myNotifications.get(index);
    }

    private void rebuildList() {
      myNotifications.clear();
      myNotifications.addAll(myNotificationsModel.getAll(null));

      fireContentsChanged(this, 0, myNotifications.size() - 1);
    }
  }
}
