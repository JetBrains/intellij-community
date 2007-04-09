package com.intellij.util.ui;

import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.AnimatedIcon;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class AsyncProcessIcon extends AnimatedIcon {

  public static final int COUNT = 12;

  public AsyncProcessIcon(@NonNls String name) {
    super(name);

    Icon[] icons = new Icon[COUNT];
    for (int i = 0; i <= COUNT - 1; i++) {
      icons[i] = IconLoader.getIcon("/process/step_" + (i + 1) + ".png");
    }
    Icon passive = IconLoader.getIcon("/process/step_passive.png");

    init(icons, passive, 800, 0, -1);
  }

  public static void main(String[] args) {
    IconLoader.activate();


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

  public boolean isDisposed() {
    return myAnimator.isDisposed();
  }
}