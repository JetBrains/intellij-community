/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.io.FileUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.MalformedURLException;
import java.util.Arrays;

public class GuiUtils {

  private static final Insets paddingFromDialogBoundaries = new Insets(7, 5, 7, 5);
  private static final Insets paddingInsideDialog = new Insets(5, 5, 5, 5);

  public static final int lengthForFileField = 25;

  public static JPanel constructFieldWithBrowseButton(JComponent aComponent, ActionListener aActionListener) {
    EnableablePanel result = new EnableablePanel(new BorderLayout(2, 0));
    result.add(aComponent, BorderLayout.CENTER);
    FixedSizeButton browseButton = new FixedSizeButton(aComponent.getPreferredSize().height);
    TextFieldWithBrowseButton.MyDoClickAction.addTo(browseButton, aComponent);
    result.add(browseButton, BorderLayout.EAST);
    browseButton.addActionListener(aActionListener);

    return result;
  }

  public static JPanel constructDirectoryBrowserField(final JTextField aTextField, final String aSearchedObjectName) {
    return constructFieldWithBrowseButton(aTextField, new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.getDirectoryChooserDescriptor(aSearchedObjectName);
        VirtualFile[] files = FileChooser.chooseFiles(aTextField, descriptor);
        if (files.length != 0) {
          aTextField.setText(FileUtil.toSystemDependentName(files[0].getPath()));
          aTextField.postActionEvent();
        }
      }
    });
  }

  public static JPanel constructFileURLBrowserField(final TextFieldWithHistory aFieldWithHistory,
                                                    final String aSearchedObjectName) {
    return constructFieldWithBrowseButton(aFieldWithHistory, new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.getFileChooserDescriptor(aSearchedObjectName);
        VirtualFile[] files = FileChooser.chooseFiles(aFieldWithHistory, descriptor);
        if (files.length != 0) {
          try {
            aFieldWithHistory.setText(VfsUtil.virtualToIoFile(files[0]).toURL().toString());
          }
          catch (MalformedURLException e1) {
            aFieldWithHistory.setText("");
          }
        }
      }
    });
  }

  public static JComponent constructLabeledComponent(String aLabelText, JComponent aComponent, int aAxis) {
    JPanel result = new JPanel();
    BoxLayout boxLayout = new BoxLayout(result, aAxis);
    result.setLayout(boxLayout);

    result.add(new JLabel(aLabelText));
    result.add(aComponent);

    return result;
  }

  public static JPanel makeDialogPanel(JPanel aPanel) {
    JPanel emptyBordered = makePaddedPanel(aPanel, paddingFromDialogBoundaries);
    return wrapWithBorder(emptyBordered, IdeBorderFactory.createBorder());
  }

  public static JPanel makeTitledPanel(JComponent aComponent, String aTitle) {
    JPanel result = makePaddedPanel(aComponent, false, true, false, true);
    return wrapWithBorder(result, IdeBorderFactory.createTitledBorder(aTitle));
  }


  private static JPanel wrapWithBorder(JComponent aPanel, Border aBorder) {
    JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.add(aPanel, BorderLayout.CENTER);
    wrapper.setBorder(aBorder);
    return wrapper;
  }


  public static BorderLayout createBorderLayout() {
    return new BorderLayout(paddingInsideDialog.left, paddingInsideDialog.top);
  }

  public static GridLayout createGridLayout(int aRows, int aColumns) {
    return new GridLayout(aRows, aColumns, paddingInsideDialog.left, paddingInsideDialog.top);
  }

  public static Component createVerticalStrut() {
    return Box.createRigidArea(new Dimension(0, paddingInsideDialog.top));
  }

  public static Component createHorisontalStrut() {
    return Box.createRigidArea(new Dimension(paddingInsideDialog.left, 0));
  }

  private static JPanel makePaddedPanel(JComponent aComponent, Insets aInsets) {
    return wrapWithBorder(aComponent, BorderFactory.createEmptyBorder(
      aInsets.top,
      aInsets.left,
      aInsets.bottom,
      aInsets.right
    ));
  }

  private static JPanel makePaddedPanel(JComponent aComponent,
                                        boolean aTop,
                                        boolean aLeft,
                                        boolean aBottom,
                                        boolean aRight) {
    return wrapWithBorder(aComponent, BorderFactory.createEmptyBorder(
      aTop ? paddingInsideDialog.top : 0,
      aLeft ? paddingInsideDialog.left : 0,
      aBottom ? paddingInsideDialog.bottom : 0,
      aRight ? paddingInsideDialog.right : 0));
  }

  public static void setAdditionalIcon(JRadioButton button, Icon icon) {
    final Icon defaultIcon = UIManager.getIcon("RadioButton.icon");
    LayeredIcon deficon = new LayeredIcon(2);
    deficon.setIcon(defaultIcon, 0);
    deficon.setIcon(icon, 1, defaultIcon.getIconWidth() + 5, 0);
    button.setIcon(deficon);

    LayeredIcon pressed = new LayeredIcon(2);
    pressed.setIcon(defaultIcon, 0);
    pressed.setIcon(icon, 1, defaultIcon.getIconWidth() + 5, 0);
    button.setPressedIcon(pressed);

    LayeredIcon selected = new LayeredIcon(2);
    selected.setIcon(defaultIcon, 0);
    selected.setIcon(icon, 1, defaultIcon.getIconWidth() + 5, 0);
    button.setSelectedIcon(selected);
  }

  public static String getTextWithoutMnemonicEscaping(String text) {
    return text.replaceAll("&", "");
  }

  public static char getDisplayedMnemonic(String text) {
    final int i = getDisplayedMnemonicIndex(text);
    return i == -1 ? (char)-1 : text.charAt(i + 1);
  }

  public static int getDisplayedMnemonicIndex(String text) {
    int mnemonicIndex = text.indexOf("&");
    return mnemonicIndex;
  }

  public static void packParentDialog(Component component) {
    while (component != null) {
      if (component instanceof JDialog) {
        ((JDialog)component).show();
        break;
      }
      component = component.getParent();
    }
  }

  public static void replaceJSplitPaneWithIDEASplitter(JComponent root) {
    replaceJSplitPaneWithIDEASplitter(root, false);
  }
  public static void replaceJSplitPaneWithIDEASplitter(JComponent root, boolean honorMinimumSize) {
    final Container parent = root.getParent();
    if (root instanceof JSplitPane) {
      // we can painlessly replace only splitter which is the only child in container
      if (parent.getComponents().length != 1 && !(parent instanceof Splitter)) {
        return;
      }
      final JSplitPane pane = (JSplitPane)root;
      final Component component1 = pane.getTopComponent();
      final Component component2 = pane.getBottomComponent();
      final int orientation = pane.getOrientation();
      final Splitter splitter = new Splitter(orientation == JSplitPane.VERTICAL_SPLIT);
      splitter.setFirstComponent((JComponent) component1);
      splitter.setSecondComponent((JComponent) component2);
      splitter.setShowDividerControls(pane.isOneTouchExpandable());
      splitter.setHonorComponentsMinimumSize(honorMinimumSize);

      // let the component chance to resize itself
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          double proportion;
          if (pane.getOrientation() == JSplitPane.VERTICAL_SPLIT) {
            proportion = pane.getDividerLocation() / (double)(parent.getHeight() - pane.getDividerSize());
          }
          else {
            proportion = pane.getDividerLocation() / (double)(parent.getWidth() - pane.getDividerSize());
          }
          if (proportion > 0 && proportion < 1) {
            splitter.setProportion((float)proportion);
          }
        }
      });

      if (parent instanceof Splitter) {
        final Splitter psplitter = (Splitter) parent;
        if (psplitter.getFirstComponent() == root)
          psplitter.setFirstComponent(splitter);
        else
          psplitter.setSecondComponent(splitter);
      }
      else {
        parent.remove(0);
        parent.setLayout(new BorderLayout());
        parent.add(splitter, BorderLayout.CENTER);
      }
      replaceJSplitPaneWithIDEASplitter((JComponent) component1, honorMinimumSize);
      replaceJSplitPaneWithIDEASplitter((JComponent) component2, honorMinimumSize);
    }
    else {
      final Component[] components = root.getComponents();
      for (int i = 0; i < components.length; i++) {
        Component component = components[i];
        if (component instanceof JComponent) {
          replaceJSplitPaneWithIDEASplitter((JComponent) component, honorMinimumSize);
        }
      }
    }
  }

  public static void enableChildren(Component container, boolean enabled, JComponent[] excludeComponents) {
    if (excludeComponents != null && Arrays.asList(excludeComponents).contains(container)) return;
    enableComponent(container, enabled);
    if (container instanceof Container) {
      final Component[] components = ((Container)container).getComponents();
      for (int i = 0; i < components.length; i++) {
        Component child = components[i];
        enableChildren(child, enabled, excludeComponents);
      }
    }
  }

  private static void enableComponent(Component component, boolean enabled) {
    component.setEnabled(enabled);
    if (component instanceof JPanel) {
      final Border border = ((JPanel)component).getBorder();
      if (border instanceof TitledBorder) {
        Color color = enabled ? component.getForeground() : UIManager.getColor("textInactiveText");
        ((TitledBorder)border).setTitleColor(color);
      }
    }
    else if (component instanceof JLabel) {
      Color color = UIManager.getColor("textInactiveText");
      if (color == null) color = component.getForeground();
      String changeColorString = "<font color=#"+Integer.toHexString(color.getRed())+Integer.toHexString(color.getGreen())+Integer.toHexString(color.getBlue())+">";
      final JLabel label = (JLabel)component;
      String text = label.getText();
      if (text != null && text.startsWith("<html>")) {
        if (text.startsWith("<html>"+changeColorString) && enabled) {
          text = "<html>"+text.substring(("<html>"+changeColorString).length());
        }
        else if (!text.startsWith("<html>"+changeColorString) && !enabled) {
          text = "<html>"+changeColorString+text.substring("<html>".length());
        }
        label.setText(text);
      }
    }
  }
}
