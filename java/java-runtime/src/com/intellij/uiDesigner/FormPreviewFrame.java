// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.text.MessageFormat;
import java.util.ResourceBundle;

// NOTE: DO NOT DELETE THIS FILE (See to PreviewFormAction)

public class FormPreviewFrame {
  private JComponent myComponent;
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle("messages.RuntimeBundle");

  // Note: this class should not be obfuscated

  public static void main(String[] args) {
    FormPreviewFrame f = new FormPreviewFrame();

    JFrame frame = new JFrame(ourBundle.getString("form.preview.title"));
    frame.setContentPane(f.myComponent);
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

    // Add menu bar
    final JMenuBar menuBar = new JMenuBar();
    frame.setJMenuBar(menuBar);

    final JMenu menuFile = new JMenu(ourBundle.getString("form.menu.preview"));
    menuFile.setMnemonic(ourBundle.getString("form.menu.preview.mnemonic").charAt(0));
    menuFile.add(new JMenuItem(new MyPackAction(frame)));
    menuFile.add(new JMenuItem(new MyExitAction()));
    menuBar.add(menuFile);

    final JMenu viewMenu = new JMenu(ourBundle.getString("form.menu.laf"));
    viewMenu.setMnemonic(ourBundle.getString("form.menu.laf.mnemonic").charAt(0));
    menuBar.add(viewMenu);

    final UIManager.LookAndFeelInfo[] lafs = UIManager.getInstalledLookAndFeels();
    for (UIManager.LookAndFeelInfo laf : lafs) {
      viewMenu.add(new MySetLafAction(frame, laf));
    }

    frame.pack();
    Rectangle screenBounds =
      GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
    frame.setLocation(screenBounds.x + (screenBounds.width - frame.getWidth()) / 2,
                      screenBounds.y + (screenBounds.height - frame.getHeight()) / 2);
    frame.setVisible(true);
  }

  private static final class MyExitAction extends AbstractAction{
    MyExitAction() {
      super(ourBundle.getString("form.menu.file.exit"));
    }

    @Override
    public void actionPerformed(final ActionEvent e) {
      System.exit(0);
    }
  }

  private static final class MyPackAction extends AbstractAction{
    private final JFrame myFrame;

    MyPackAction(final JFrame frame) {
      super(ourBundle.getString("form.menu.view.pack"));
      myFrame = frame;
    }

    @Override
    public void actionPerformed(final ActionEvent e) {
      myFrame.pack();
    }
  }

  private static final class MySetLafAction extends AbstractAction{
    private final JFrame myFrame;
    private final UIManager.LookAndFeelInfo myInfo;

    MySetLafAction(final JFrame frame, final UIManager.LookAndFeelInfo info) {
      this(frame, info, info.getName());
    }

    MySetLafAction(final JFrame frame, final UIManager.LookAndFeelInfo info, String name) {
      super(name);
      myFrame = frame;
      myInfo = info;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      try{
        UIManager.setLookAndFeel(myInfo.getClassName());
        SwingUtilities.updateComponentTreeUI(myFrame);
        Dimension prefSize = myFrame.getPreferredSize();
        if(prefSize.width > myFrame.getWidth() || prefSize.height > myFrame.getHeight()){
          myFrame.pack();
        }
      }
      catch(Exception exc){
        JOptionPane.showMessageDialog(
          myFrame,
          MessageFormat.format(ourBundle.getString("error.cannot.change.look.feel"), exc.getMessage()),
          ourBundle.getString("error.title"),
          JOptionPane.ERROR_MESSAGE
        );
      }
    }
  }
}
