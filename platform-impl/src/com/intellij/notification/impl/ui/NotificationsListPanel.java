package com.intellij.notification.impl.ui;

import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.impl.NotificationImpl;
import com.intellij.notification.impl.NotificationModel;
import com.intellij.notification.impl.NotificationModelListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.util.MinimizeButton;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author spleaner
 */
public class NotificationsListPanel extends JPanel implements NotificationModelListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.notification.impl.ui.NotificationsListPanel");
  private static final String REMOVE_KEY = "REMOVE";

  private WeakReference<JBPopup> myPopupRef;

  private JList myList;
  private NotificationModel myNotificationsModel;
  private NotificationsListModel myDataModel;

  private JComponent myActiveContentComponent;
  private JComponent myInactiveContentComponent;

  private Wrapper myContentPane = new Wrapper();
  private NotificationComponent myNotificationComponent;

  public NotificationsListPanel(@NotNull final NotificationComponent component) {
    setLayout(new BorderLayout());

    myNotificationComponent = component;

    myNotificationsModel = component.getModel();
    myDataModel = new NotificationsListModel(myNotificationsModel);

    myNotificationsModel.addListener(this);

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

    myList.setBackground(myContentPane.getBackground());
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

    myList.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), REMOVE_KEY);
    myList.getActionMap().put(REMOVE_KEY, new AbstractAction() {
      public void actionPerformed(final ActionEvent e) {
        removeSelected();
      }
    });

    final JScrollPane scrollPane = new JScrollPane(myList);
    scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
//    scrollPane.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
//    scrollPane.setBackground(Color.WHITE);

    final JPanel wrapperPane = new JPanel();
    wrapperPane.setLayout(new BorderLayout());

    wrapperPane.add(scrollPane, BorderLayout.CENTER);
    wrapperPane.add(buildFilterBar(myDataModel), BorderLayout.NORTH);

    myActiveContentComponent = wrapperPane;

    myInactiveContentComponent = new JLabel("No notifications.", JLabel.CENTER) {
      @Override
      public Dimension getPreferredSize() {
        return getEmptyPreferredSize();
      }
    };

    myInactiveContentComponent.setFocusable(true);

    switchLayout();

    add(myContentPane, BorderLayout.CENTER);
  }

  private JComponent buildFilterBar(@NotNull final NotificationsListModel listModel) {
    final JPanel box = new JPanel();
    box.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
    box.setLayout(new BoxLayout(box, BoxLayout.X_AXIS));

    final ButtonGroup buttonGroup = new ButtonGroup();

    createFilterButton(box, buttonGroup, "All", new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        listModel.filter(null);
        resetSelection();
      }
    }, true);

    createFilterButton(box, buttonGroup, "Error", new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        listModel.filter(NotificationType.ERROR);
        resetSelection();
      }
    }, false);

    createFilterButton(box, buttonGroup, "Warning", new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        listModel.filter(NotificationType.WARNING);
        resetSelection();
      }
    }, false);

    createFilterButton(box, buttonGroup, "Information", new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        listModel.filter(NotificationType.INFORMATION);
        resetSelection();
      }
    }, false);

    box.add(Box.createHorizontalGlue());

    return box;
  }

  private void resetSelection() {
    if (myDataModel.getSize() > 0) {
      myList.getSelectionModel().setSelectionInterval(0, 0);
    }
  }

  private static void createFilterButton(final JPanel parent, final ButtonGroup group, final String title, final ActionListener listener, final boolean active) {
    final StickyButton b = new StickyButton(title, listener);
    parent.add(b);
    group.add(b);
    b.setSelected(active);
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

    if (count > 0) {
      if (myActiveContentComponent.getParent() == null) {
        myContentPane.removeAll();
        myContentPane.setContent(myActiveContentComponent);
      }
    } else {
      if (myInactiveContentComponent.getParent() == null) {
        myContentPane.removeAll();
        myContentPane.setContent(myInactiveContentComponent);
      }
    }

    revalidateAll();
  }

  private void performNotificationAction(final NotificationImpl notification) {
    final NotificationListener.Continue onClose = notification.getListener().perform();
    if (onClose == NotificationListener.Continue.REMOVE) {
      myNotificationsModel.remove(notification);
      revalidateAll();
    }
  }

  private static Dimension getEmptyPreferredSize() {
    final Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
    size.width *= 0.3d;
    size.height *= 0.3d;
    return size;
  }

  static Dimension getMinSize() {
    final Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
    size.width *= 0.1d;
    size.height *= 0.1d;
    return size;
  }

  public void clear() {
    if (myPopupRef != null) {
      final JBPopup jbPopup = myPopupRef.get();
      if (jbPopup != null) {
        jbPopup.cancel();
      }

      myPopupRef = null;
    }
  }

  private void revalidateAll() {
    myContentPane.revalidate();
    myContentPane.repaint();
  }

  public void removeSelected() {
    final ListSelectionModel model = myList.getSelectionModel();
    if (!model.isSelectionEmpty()) {
      final int min = model.getMinSelectionIndex();
      final int max = model.getMaxSelectionIndex();

      final List<NotificationImpl> tbr = new ArrayList<NotificationImpl>();
      for (int i = min; i <= max; i++) {
        if (model.isSelectedIndex(i)) {
          final NotificationImpl notification = (NotificationImpl) myDataModel.getElementAt(i);
          if (notification != null) {
            final NotificationListener.Continue onRemove = notification.getListener().onRemove();
            if (onRemove == NotificationListener.Continue.REMOVE) {
              tbr.add(notification);
            }
          }
        }
      }

      if (tbr.size() > 0) {
        myNotificationsModel.remove(tbr.toArray(new NotificationImpl[tbr.size()]));

        final int toSelect = Math.min(min, myDataModel.getSize() - 1);
        model.clearSelection();
        if (toSelect >= 0) {
          model.setSelectionInterval(toSelect, toSelect);
          myList.scrollRectToVisible(myList.getCellBounds(toSelect, toSelect));
        }
      }
    }

    revalidateAll();
  }

  public JComponent getPreferredFocusedComponent() {
    return myContentPane.getTargetComponent() == myActiveContentComponent ? myList : myInactiveContentComponent;
  }

  public void showOrHide() {
    if (myPopupRef != null) {
      final JBPopup popup = myPopupRef.get();
      if (popup != null) {
        popup.cancel();
      }

      myPopupRef = null;
      return;
    }

    final ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(this, getPreferredFocusedComponent());
    final JBPopup popup =
        builder.setResizable(true)
            .setMinSize(getMinSize())
            .setDimensionServiceKey(null, "NotificationsPopup", true)
            .setCancelOnClickOutside(false)
            .setBelongsToGlobalPopupStack(false)
            .setCancelButton(new MinimizeButton("Hide"))
            .setMovable(true)
            .setRequestFocus(true)
            .setTitle("Notifications")
            .createPopup();

    myPopupRef = new WeakReference<JBPopup>(popup);
    popup.showInCenterOf(SwingUtilities.getRootPane(myNotificationComponent));
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

      setIcon(notification.getIcon());
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
    private NotificationType myType;

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
      myNotifications.addAll(myNotificationsModel.getByType(myType));

      fireContentsChanged(this, 0, myNotifications.size() - 1);
    }

    public void filter(final NotificationType type) {
      myType = type;
      rebuildList();
    }
  }
}
