package com.intellij.util.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class AsyncProcessIcon extends JComponent implements Disposable {

  private Icon[] myIcons;
  private Dimension myPrefSize = new Dimension();

  private int myCurrentIconIndex;

  public static final int COUNT = 12;
  private Icon myPassiveIcon;

  private boolean myRunning = true;

  private Animator myAnimator;

  public AsyncProcessIcon(@NonNls String name) {
    myIcons = new Icon[COUNT];
    for (int i = 0; i <= COUNT - 1; i++) {
      myIcons[i] = IconLoader.getIcon("/process/step_" + (i + 1) + ".png");
    }
    myPassiveIcon = IconLoader.getIcon("/process/step_passive.png");
    myPrefSize.width = myIcons[0].getIconWidth();
    myPrefSize.height = myIcons[0].getIconHeight();

    //setBorder(null);

    UIUtil.removeQuaquaVisualMarginsIn(this);

    myAnimator = new Animator(name, COUNT, 800, true) {
      public void paintNow(final int frame) {
        myCurrentIconIndex = frame;
        paintImmediately(0, 0, getWidth(), getHeight());
      }
    };
  }

  public void resume() {
    myRunning = true;
    myAnimator.resume();
  }

  public void addNotify() {
    super.addNotify();
    if (myRunning) {
      myAnimator.resume();
    }
  }

  public void removeNotify() {
    super.removeNotify();
    myAnimator.suspend();
  }

  public void suspend() {
    myRunning = false;
    myAnimator.suspend();
    repaint();
  }

  public void dispose() {
    myAnimator.dispose();
  }

  public Dimension getPreferredSize() {
    final Insets insets = getInsets();
    return new Dimension(myPrefSize.width + insets.left + insets.right, myPrefSize.height + insets.top + insets.bottom);
  }

  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  public Dimension getMaximumSize() {
    return getPreferredSize();
  }

  protected void paintComponent(Graphics g) {
    if (isOpaque()) {
      g.setColor(getBackground());
      g.fillRect(0, 0, getWidth(), getHeight());
    }

    Icon icon;

    if (myAnimator.isRunning()) {
      icon = myIcons[myCurrentIconIndex];
    } else {
      icon = myPassiveIcon;
    }

    final Dimension size = getSize();
    int x = (size.width - icon.getIconWidth()) / 2;
    int y = (size.height - icon.getIconHeight()) / 2;

    icon.paintIcon(this, g, x, y);
  }

  public static void main(String[] args) {
    JFrame frame = new JFrame();
    frame.getContentPane().setLayout(new BorderLayout());
    JPanel content = new JPanel(new FlowLayout());


    AsyncProcessIcon progress = new AsyncProcessIcon("Process");
    content.add(progress);

    JButton button = new JButton("press me");
    content.add(button);
    button.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Thread sleeper = new Thread() {
          public void run() {
            try {
              sleep(15000);
            } catch (InterruptedException e1) {
              e1.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
          }
        };

        sleeper.start();
        try {
          sleeper.join();
        } catch (InterruptedException e1) {
          e1.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
      }
    });

    progress.resume();


    frame.getContentPane().add(content, BorderLayout.CENTER);
    frame.setBounds(200, 200, 400, 400);
    frame.show();
  }

}
