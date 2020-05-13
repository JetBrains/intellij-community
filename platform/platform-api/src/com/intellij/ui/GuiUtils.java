// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.CharFilter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
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
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.lang.reflect.InvocationTargetException;

public final class GuiUtils {
  private static final Insets paddingInsideDialog = JBUI.insets(5);

  private static final CharFilter NOT_MNEMONIC_CHAR_FILTER = ch -> ch != '&' && ch != UIUtil.MNEMONIC;

  public static JPanel constructFieldWithBrowseButton(JComponent aComponent, ActionListener aActionListener) {
    return constructFieldWithBrowseButton(aComponent, aActionListener, 0);
  }

  public static JPanel constructFieldWithBrowseButton(TextFieldWithHistory aComponent, ActionListener aActionListener) {
    return constructFieldWithBrowseButton(aComponent, aActionListener, 0);
  }

  private static JPanel constructFieldWithBrowseButton(final JComponent aComponent, final ActionListener aActionListener, int delta) {
    JPanel result = new JPanel(new GridBagLayout());
    result.add(aComponent, new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, JBUI.emptyInsets(), 0, 0));
    FixedSizeButton browseButton = new FixedSizeButton(aComponent.getPreferredSize().height - delta);//ignore border in case of browse button
    TextFieldWithBrowseButton.MyDoClickAction.addTo(browseButton, aComponent);
    result.add(browseButton, new GridBagConstraints(1, 0, 1, 1, 0, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE,
                                                    JBUI.emptyInsets(), 0, 0));
    browseButton.addActionListener(aActionListener);

    return result;
  }

  @Deprecated
  public static JPanel constructDirectoryBrowserField(final JTextField field, final String objectName) {
    return constructFieldWithBrowseButton(field, new ActionListener() {
      @SuppressWarnings("HardCodedStringLiteral")
      @Override
      public void actionPerformed(ActionEvent e) {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle("Select " + objectName);
        VirtualFile file = FileChooser.chooseFile(descriptor, field, null, null);
        if (file != null) {
          field.setText(FileUtil.toSystemDependentName(file.getPath()));
          field.postActionEvent();
        }
      }
    });
  }

  @Deprecated
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

  @Deprecated
  public static BorderLayout createBorderLayout() {
    return new BorderLayout(paddingInsideDialog.left, paddingInsideDialog.top);
  }

  @Deprecated
  public static GridLayout createGridLayout(int aRows, int aColumns) {
    return new GridLayout(aRows, aColumns, paddingInsideDialog.left, paddingInsideDialog.top);
  }

  public static Component createVerticalStrut() {
    return Box.createRigidArea(new Dimension(0, paddingInsideDialog.top));
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

  public static void replaceJSplitPaneWithIDEASplitter(JComponent root) {
    replaceJSplitPaneWithIDEASplitter(root, false);
  }

  public static void replaceJSplitPaneWithIDEASplitter(JComponent root, boolean useOnePixelDivider) {
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
      boolean vertical = orientation == JSplitPane.VERTICAL_SPLIT;
      final Splitter splitter = useOnePixelDivider ? new OnePixelSplitter(vertical) : new JBSplitter(vertical);
      splitter.setFirstComponent((JComponent) component1);
      splitter.setSecondComponent((JComponent) component2);
      splitter.setShowDividerControls(pane.isOneTouchExpandable());
      splitter.setHonorComponentsMinimumSize(true);

      if (pane.getDividerLocation() > 0) {
// let the component chance to resize itself
        SwingUtilities.invokeLater(() -> {
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
      replaceJSplitPaneWithIDEASplitter((JComponent) component1, useOnePixelDivider);
      replaceJSplitPaneWithIDEASplitter((JComponent) component2, useOnePixelDivider);
    }
    else {
      final Component[] components = root.getComponents();
      for (Component component : components) {
        if (component instanceof JComponent) {
          replaceJSplitPaneWithIDEASplitter((JComponent)component, useOnePixelDivider);
        }
      }
    }
  }

  public static void iterateChildren(Component container, Consumer<? super Component> consumer, JComponent... excludeComponents) {
    if (excludeComponents != null && ArrayUtil.find(excludeComponents, container) != -1) return;
    consumer.consume(container);
    if (container instanceof Container) {
      final Component[] components = ((Container)container).getComponents();
      for (Component child : components) {
        iterateChildren(child, consumer, excludeComponents);
      }
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
    iterateChildren(container, t -> enableComponent(t, enabled), excludeComponents);
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

  public static void runOrInvokeAndWait(@NotNull Runnable runnable) throws InvocationTargetException, InterruptedException {
    ApplicationManager.getApplication().invokeAndWait(runnable);
  }

  public static void invokeLaterIfNeeded(@NotNull Runnable runnable, @NotNull ModalityState modalityState) {
    Application app = ApplicationManager.getApplication();
    if (app.isDispatchThread()) {
      runnable.run();
    }
    else {
      app.invokeLater(runnable, modalityState);
    }
  }

  public static void invokeLaterIfNeeded(@NotNull Runnable runnable, @NotNull ModalityState modalityState, @NotNull Condition expired) {
    Application app = ApplicationManager.getApplication();
    if (app.isDispatchThread()) {
      runnable.run();
    }
    else {
      app.invokeLater(runnable, modalityState, expired);
    }
  }

  public static JTextField createUndoableTextField() {
    return new JBTextField();
  }

  /**
   * Returns dimension with width required to type certain number of chars in provided component
   * @param charCount number of chars
   * @param comp component
   * @return dimension with width enough to insert provided number of chars into component
   */
  @NotNull
  public static Dimension getSizeByChars(int charCount, @NotNull JComponent comp) {
    Dimension size = comp.getPreferredSize();
    FontMetrics fontMetrics = comp.getFontMetrics(comp.getFont());
    size.width = fontMetrics.charWidth('a') * charCount;
    return size;
  }

  public static void installVisibilityReferent(JComponent owner, JComponent referent) {
    referent.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentShown(ComponentEvent e) {
        toggleVisibility(e);
      }

      @Override
      public void componentHidden(ComponentEvent e) {
        toggleVisibility(e);
      }

      private void toggleVisibility(ComponentEvent e) {
        Component component = e.getComponent();
        if (component != null) {
          owner.setVisible(component.isVisible());
        }
      }
    });
  }
}
