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

  private Timer myTimer;
  private String myName;

  private int myCurrentIconIndex = 0;
  private int myCurrentCount = 0;

  public static final int COUNT = 12;
  private Icon myPassiveIcon;

  private boolean myRunning = true;

  public AsyncProcessIcon(@NonNls String name) {
    myName = name;
    myIcons = new Icon[COUNT];
    for (int i = 0; i <= COUNT - 1; i++) {
      myIcons[i] = IconLoader.getIcon("/process/step_" + (i + 1) + ".png");
    }
    myPassiveIcon = IconLoader.getIcon("/process/step_passive.png");
    myPrefSize.width = myIcons[0].getIconWidth();
    myPrefSize.height = myIcons[0].getIconHeight();

    //setBorder(null);

    UIUtil.removeQuaquaVisualMarginsIn(this);

    myTimer = new Timer(myName, 800 / COUNT) {
      protected void onTimer() {
        if (myCurrentCount > COUNT) return;

        if (myCurrentIconIndex + 1 < myIcons.length) {
          myCurrentIconIndex++;
        } else {
          myCurrentIconIndex = 0;
        }
        myCurrentCount++;

        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            myCurrentCount--;
            paintImmediately(0, 0, getWidth(), getHeight());
          }
        });
      }
    };
  }

  public void resume() {
    myRunning = true;
    myTimer.resume();
    repaint();
  }

  public void addNotify() {
    super.addNotify();
    if (myRunning) {
      myTimer.resume();
    }
  }

  public void removeNotify() {
    super.removeNotify();
    myTimer.suspend();
  }

  public void suspend() {
    myRunning = false;
    myTimer.suspend();
    repaint();
  }

  public void dispose() {
    myTimer.dispose();
  }

  public Dimension getPreferredSize() {
    return myPrefSize;
  }

  public Dimension getMinimumSize() {
    return myPrefSize;
  }

  public Dimension getMaximumSize() {
    return myPrefSize;
  }

  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    Icon icon;

    if (myTimer.isRunning()) {
      icon = myIcons[myCurrentIconIndex];
    } else {
      icon = myPassiveIcon;
    }

    int x = (getWidth() - icon.getIconWidth()) / 2;
    int y = (getHeight() - icon.getIconHeight()) / 2;

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
              sleep(5000);
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
