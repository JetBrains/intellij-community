/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.CharFilter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;

public class GuiUtils {

  private static final Insets paddingFromDialogBoundaries = new Insets(7, 5, 7, 5);
  private static final Insets paddingInsideDialog = new Insets(5, 5, 5, 5);

  public static final int lengthForFileField = 25;
  private static final CharFilter NOT_MNEMONIC_CHAR_FILTER = new CharFilter() {
    @Override
    public boolean accept(char ch) {
      return ch != '&' && ch != UIUtil.MNEMONIC;
    }
  };

  public static JPanel constructFieldWithBrowseButton(JComponent aComponent, ActionListener aActionListener) {
    return constructFieldWithBrowseButton(aComponent, aActionListener, 0);
  }

  public static JPanel constructFieldWithBrowseButton(TextFieldWithHistory aComponent, ActionListener aActionListener) {
    return constructFieldWithBrowseButton(aComponent, aActionListener, 0);
  }

  private static JPanel constructFieldWithBrowseButton(final JComponent aComponent, final ActionListener aActionListener, int delta) {
    JPanel result = new JPanel(new GridBagLayout());
    result.add(aComponent, new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0,0));
    FixedSizeButton browseButton = new FixedSizeButton(aComponent.getPreferredSize().height - delta);//ignore border in case of browse button
    TextFieldWithBrowseButton.MyDoClickAction.addTo(browseButton, aComponent);
    result.add(browseButton, new GridBagConstraints(1, 0, 1, 1, 0, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0,0,0,0), 0,0));
    browseButton.addActionListener(aActionListener);

    return result;
  }

  public static JPanel constructDirectoryBrowserField(final JTextField aTextField, final String aSearchedObjectName) {
    return constructFieldWithBrowseButton(aTextField, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.getDirectoryChooserDescriptor(aSearchedObjectName);
        VirtualFile file = FileChooser.chooseFile(descriptor, aTextField, null, null);
        if (file != null) {
          aTextField.setText(FileUtil.toSystemDependentName(file.getPath()));
          aTextField.postActionEvent();
        }
      }
    });
  }

  public static JPanel constructFileURLBrowserField(final TextFieldWithHistory aFieldWithHistory,
                                                    final String aSearchedObjectName) {
    return constructFieldWithBrowseButton(aFieldWithHistory, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.getFileChooserDescriptor(aSearchedObjectName);
        VirtualFile file = FileChooser.chooseFile(descriptor, aFieldWithHistory, null, null);
        if (file != null) {
          try {
            aFieldWithHistory.setText(VfsUtil.virtualToIoFile(file).toURL().toString());
          }
          catch (MalformedURLException e1) {
            aFieldWithHistory.setText("");
          }
        }
      }
    });
  }

  public static JComponent constructLabeledComponent(String aLabelText, JComponent aComponent, @JdkConstants.BoxLayoutAxis int aAxis) {
    JPanel result = new JPanel();
    BoxLayout boxLayout = new BoxLayout(result, aAxis);
    result.setLayout(boxLayout);

    result.add(new JLabel(aLabelText));
    result.add(aComponent);

    return result;
  }

  public static JPanel makeDialogPanel(JPanel aPanel) {
    JPanel emptyBordered = makePaddedPanel(aPanel, paddingFromDialogBoundaries);
    return wrapWithBorder(emptyBordered, IdeBorderFactory.createRoundedBorder());
  }

  public static JPanel makeTitledPanel(JComponent aComponent, String aTitle) {
    JPanel result = makePaddedPanel(aComponent, false, true, false, true);
    return wrapWithBorder(result, IdeBorderFactory.createTitledBorder(aTitle, true));
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
    final Icon defaultIcon = UIUtil.getRadioButtonIcon();
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
    return StringUtil.strip(text, NOT_MNEMONIC_CHAR_FILTER);
  }

  public static char getDisplayedMnemonic(String text) {
    final int i = getDisplayedMnemonicIndex(text);
    return i == -1 ? (char)-1 : text.charAt(i + 1);
  }

  public static int getDisplayedMnemonicIndex(String text) {
    return text.indexOf("&");
  }

  public static void packParentDialog(Component component) {
    while (component != null) {
      if (component instanceof JDialog) {
        component.setVisible(true);
        break;
      }
      component = component.getParent();
    }
  }

  public static void replaceJSplitPaneWithIDEASplitter(JComponent root) {
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
      final Splitter splitter = new JBSplitter(orientation == JSplitPane.VERTICAL_SPLIT);
      splitter.setFirstComponent((JComponent) component1);
      splitter.setSecondComponent((JComponent) component2);
      splitter.setShowDividerControls(pane.isOneTouchExpandable());
      splitter.setHonorComponentsMinimumSize(true);

      if (pane.getDividerLocation() > 0) {
// let the component chance to resize itself
        SwingUtilities.invokeLater(new Runnable() {
          @Override
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
      }

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
      replaceJSplitPaneWithIDEASplitter((JComponent) component1);
      replaceJSplitPaneWithIDEASplitter((JComponent) component2);
    }
    else {
      final Component[] components = root.getComponents();
      for (Component component : components) {
        if (component instanceof JComponent) {
          replaceJSplitPaneWithIDEASplitter((JComponent)component);
        }
      }
    }
  }

  public static void iterateChildren(Component container, Consumer<Component> consumer, JComponent... excludeComponents) {
    if (excludeComponents != null && ArrayUtil.find(excludeComponents, container) != -1) return;
    consumer.consume(container);
    if (container instanceof Container) {
      final Component[] components = ((Container)container).getComponents();
      for (Component child : components) {
        iterateChildren(child, consumer, excludeComponents);
      }
    }
  }

  public static void iterateChildren(Consumer<Component> consumer, Component... components) {
    for (final Component component : components) {
      iterateChildren(component, consumer);
    }
  }

  public static void enableChildren(final boolean enabled, Component... components) {
    for (final Component component : components) {
      enableChildren(component, enabled);
    }
  }

  public static void showComponents(final boolean visible, Component... components) {
    for (final Component component : components) {
      component.setVisible(visible);
    }
  }

  public static void enableChildren(Component container, final boolean enabled, JComponent... excludeComponents) {
    iterateChildren(container, new Consumer<Component>() {
      @Override
      public void consume(final Component t) {
        enableComponent(t, enabled);
      }
    }, excludeComponents);
  }

  private static void enableComponent(Component component, boolean enabled) {
    if (component.isEnabled() == enabled) return;
    component.setEnabled(enabled);
    if (component instanceof JPanel) {
      final Border border = ((JPanel)component).getBorder();
      if (border instanceof TitledBorder) {
        Color color = enabled ? component.getForeground() : UIUtil.getInactiveTextColor();
        ((TitledBorder)border).setTitleColor(color);
      }
    }
    else if (component instanceof JLabel) {
      Color color = UIUtil.getInactiveTextColor();
      if (color == null) color = component.getForeground();
      @NonNls String changeColorString = "<font color=#" + colorToHex(color) +">";
      final JLabel label = (JLabel)component;
      @NonNls String text = label.getText();
      if (text != null && text.startsWith("<html>")) {
        if (StringUtil.startsWithConcatenation(text, "<html>", changeColorString) && enabled) {
          text = "<html>"+text.substring(("<html>"+changeColorString).length());
        }
        else if (!StringUtil.startsWithConcatenation(text, "<html>", changeColorString) && !enabled) {
          text = "<html>"+changeColorString+text.substring("<html>".length());
        }
        label.setText(text);
      }
    }
    else if (component instanceof JTable) {
      TableColumnModel columnModel = ((JTable)component).getColumnModel();
      for (int i=0; i<columnModel.getColumnCount();i++) {
        TableCellRenderer cellRenderer = columnModel.getColumn(0).getCellRenderer();
        if (cellRenderer instanceof Component) {
          enableComponent((Component)cellRenderer, enabled);
        }
      }
    }
  }

  public static String colorToHex(final Color color) {
    return to2DigitsHex(color.getRed())
           +to2DigitsHex(color.getGreen())
           +to2DigitsHex(color.getBlue());
  }

  private static String to2DigitsHex(int i) {
    String s = Integer.toHexString(i);
    if (s.length() < 2) s = "0" + s;
    return s;
  }

  public static void invokeAndWait(@NotNull Runnable runnable) throws InvocationTargetException, InterruptedException {
    Application application = ApplicationManager.getApplication();
    assert !application.isDispatchThread() : "Must not be invoked from AWT dispatch thread";
    if (application.isReadAccessAllowed()) {
      // make ApplicationImpl catch deadlock situation with readLock held
      application.invokeAndWait(runnable, application.getDefaultModalityState());
      return;
    }
    SwingUtilities.invokeAndWait(runnable);
  }

  public static void runOrInvokeAndWait(@NotNull Runnable runnable) throws InvocationTargetException, InterruptedException {
    Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      runnable.run();
    }
    else {
      invokeAndWait(runnable);
    }
  }

  public static JTextField createUndoableTextField() {
    JTextField field = new JTextField();
    if (ApplicationManager.getApplication() != null) {
      new TextComponentUndoProvider(field);
    }
    return field;
  }
}
