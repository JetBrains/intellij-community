// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.CharFilter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import sun.awt.AWTAccessor;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeListener;
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
    result.add(aComponent,
               new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, JBInsets.emptyInsets(), 0, 0));
    FixedSizeButton browseButton =
      new FixedSizeButton(aComponent.getPreferredSize().height - delta);//ignore border in case of browse button
    TextFieldWithBrowseButton.MyDoClickAction.addTo(browseButton, aComponent);
    result.add(browseButton, new GridBagConstraints(1, 0, 1, 1, 0, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE,
                                                    JBInsets.emptyInsets(), 0, 0));
    browseButton.addActionListener(aActionListener);

    return result;
  }

  public static Component createVerticalStrut() {
    return Box.createRigidArea(new Dimension(0, paddingInsideDialog.top));
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
      splitter.setFirstComponent((JComponent)component1);
      splitter.setSecondComponent((JComponent)component2);
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
        final Splitter psplitter = (Splitter)parent;
        if (psplitter.getFirstComponent() == root) {
          psplitter.setFirstComponent(splitter);
        }
        else {
          psplitter.setSecondComponent(splitter);
        }
      }
      else {
        parent.remove(0);
        parent.setLayout(new BorderLayout());
        parent.add(splitter, BorderLayout.CENTER);
      }
      replaceJSplitPaneWithIDEASplitter((JComponent)component1, useOnePixelDivider);
      replaceJSplitPaneWithIDEASplitter((JComponent)component2, useOnePixelDivider);
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
      @NonNls String changeColorString = "<font color=#" + colorToHex(color) + ">";
      final JLabel label = (JLabel)component;
      @NonNls String text = label.getText();
      if (text != null && text.startsWith("<html>")) {
        if (StringUtil.startsWithConcatenation(text, "<html>", changeColorString) && enabled) {
          text = "<html>" + text.substring(("<html>" + changeColorString).length());
        }
        else if (!StringUtil.startsWithConcatenation(text, "<html>", changeColorString) && !enabled) {
          text = "<html>" + changeColorString + text.substring("<html>".length());
        }
        label.setText(text);
      }
    }
    else if (component instanceof JTable) {
      TableColumnModel columnModel = ((JTable)component).getColumnModel();
      for (int i = 0; i < columnModel.getColumnCount(); i++) {
        TableCellRenderer cellRenderer = columnModel.getColumn(0).getCellRenderer();
        if (cellRenderer instanceof Component) {
          enableComponent((Component)cellRenderer, enabled);
        }
      }
    }
  }

  public static String colorToHex(final Color color) {
    return UIUtil.colorToHex(color);
  }

  /**
   * @deprecated Use {@link Application#invokeAndWait}
   */
  @SuppressWarnings("RedundantThrows")
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static void runOrInvokeAndWait(@NotNull Runnable runnable) throws InvocationTargetException, InterruptedException {
    ApplicationManager.getApplication().invokeAndWait(runnable);
  }

  /**
   * @deprecated Use ModalityUiUtil instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  public static void invokeLaterIfNeeded(@NotNull Runnable runnable, @NotNull ModalityState modalityState) {
    Application app = ApplicationManager.getApplication();
    if (app.isDispatchThread()) {
      runnable.run();
    }
    else {
      app.invokeLater(runnable, modalityState);
    }
  }

  /**
   * @deprecated Use ModalityUiUtil instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
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
   *
   * @param charCount number of chars
   * @param comp      component
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

  /**
   * removes all children and parent references, listeners from {@code container} to avoid possible memory leaks
   */
  public static void removePotentiallyLeakingReferences(@NotNull Container container) {
    assert SwingUtilities.isEventDispatchThread();
    AWTAccessor.getComponentAccessor().setParent(container, null);
    container.removeAll();
    for (ComponentListener c : container.getComponentListeners()) container.removeComponentListener(c);
    for (FocusListener c : container.getFocusListeners()) container.removeFocusListener(c);
    for (HierarchyListener c : container.getHierarchyListeners()) container.removeHierarchyListener(c);
    for (HierarchyBoundsListener c : container.getHierarchyBoundsListeners()) container.removeHierarchyBoundsListener(c);
    for (KeyListener c : container.getKeyListeners()) container.removeKeyListener(c);
    for (MouseListener c : container.getMouseListeners()) container.removeMouseListener(c);
    for (MouseMotionListener c : container.getMouseMotionListeners()) container.removeMouseMotionListener(c);
    for (MouseWheelListener c : container.getMouseWheelListeners()) container.removeMouseWheelListener(c);
    for (InputMethodListener c : container.getInputMethodListeners()) container.removeInputMethodListener(c);
    for (PropertyChangeListener c : container.getPropertyChangeListeners()) container.removePropertyChangeListener(c);
    for (ContainerListener c : container.getContainerListeners()) container.removeContainerListener(c);
  }
}
