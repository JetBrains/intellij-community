package com.intellij.diagnostic;

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.LayeredIcon;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author ksafonov
 */
public class IdeFatalErrorsIcon extends JLabel {
  public static final Icon UNREAD_ERROR_ICON = IconLoader.getIcon("/ide/fatalError.png");
  private static final Icon READ_ERROR_ICON = IconLoader.getIcon("/ide/fatalError-read.png");
  private static final Icon NO_ERRORS_ICON = IconLoader.getIcon("/ide/emptyFatalError.png");

  public enum State {UnreadErrors, ReadErrors, NoErrors}

  private final LayeredIcon myIcon;
  private final ActionListener myListener;

  private Future myBlinker;
  private State myState;

  public IdeFatalErrorsIcon(ActionListener aListener) {
    myListener = aListener;
    setBorder(BorderFactory.createEmptyBorder(0, 1, 0, 1));

    addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (myState != State.NoErrors) {
          myListener.actionPerformed(null);
        }
      }
    });

    myIcon = new LayeredIcon(UNREAD_ERROR_ICON, READ_ERROR_ICON, NO_ERRORS_ICON) {
      @Override
      public synchronized void paintIcon(Component c, Graphics g, int x, int y) {
        super.paintIcon(c, g, x, y);
      }

      @Override
      public synchronized void setLayerEnabled(int layer, boolean enabled) {
        super.setLayerEnabled(layer, enabled);
      }
    };
    setIcon(myIcon);
  }

  public void setState(State state) {
    myState = state;
    switch (state) {
      case UnreadErrors:
        changeVisibility(true);
        myIcon.setLayerEnabled(0, true);
        myIcon.setLayerEnabled(1, false);
        myIcon.setLayerEnabled(2, false);
        startBlinker();
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(IdeMessagePanel.INTERNAL_ERROR_NOTICE);
        break;

      case ReadErrors:
        changeVisibility(true);
        stopBlinker();
        myIcon.setLayerEnabled(0, false);
        myIcon.setLayerEnabled(1, true);
        myIcon.setLayerEnabled(2, false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(IdeMessagePanel.INTERNAL_ERROR_NOTICE);
        break;

      case NoErrors:
        // let's keep all this layers stuff for the case if we decide not to hide the icon
        stopBlinker();
        changeVisibility(false);
        myIcon.setLayerEnabled(0, false);
        myIcon.setLayerEnabled(1, false);
        myIcon.setLayerEnabled(2, true);
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        setToolTipText(DiagnosticBundle.message("error.notification.empty.text"));
        break;

      default:
        assert false;
    }
    repaint();
  }

    private void changeVisibility(final boolean visible) {
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          setVisible(visible);
        }
      });
    }

    private synchronized void startBlinker() {
    if (myBlinker != null) {
      return;
    }

    myBlinker = JobScheduler.getScheduler().scheduleAtFixedRate(new Runnable() {
      boolean enabled = false;

      @Override
      public void run() {
        myIcon.setLayerEnabled(0, enabled);
        myIcon.setLayerEnabled(1, false);
        myIcon.setLayerEnabled(2, !enabled);
        repaint();
        enabled = !enabled;
      }
    }, 1, 1, TimeUnit.SECONDS);
  }

  private synchronized void stopBlinker() {
    if (myBlinker != null) {
      myBlinker.cancel(true);
      myBlinker = null;
    }
  }
}
