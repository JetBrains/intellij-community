// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.openapi.actionSystem.ex;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.HelpTooltip;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.UserActivityProviderComponent;
import com.intellij.util.ui.*;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public abstract class ComboBoxAction extends AnAction implements CustomComponentAction {
  private static Icon myIcon = null;
  private static Icon myDisabledIcon = null;
  private static Icon myWin10ComboDropTriangleIcon = null;

  public static Icon getArrowIcon(boolean enabled) {
    if (UIUtil.isUnderWin10LookAndFeel()) {
      if (myWin10ComboDropTriangleIcon == null) {
        myWin10ComboDropTriangleIcon = IconLoader.getIcon("/com/intellij/ide/ui/laf/icons/win10/comboDropTriangle.png");
      }
      return myWin10ComboDropTriangleIcon;
    }
    Icon icon = UIUtil.isUnderDarcula() ? AllIcons.General.ComboArrow : AllIcons.General.ComboBoxButtonArrow;
    if (myIcon != icon) {
      myIcon = icon;
      myDisabledIcon = IconLoader.getDisabledIcon(myIcon);
    }
    return enabled ? myIcon : myDisabledIcon;
  }

  private boolean mySmallVariant = true;
  private String myPopupTitle;

  protected ComboBoxAction() {
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    JFrame frame = WindowManager.getInstance().getFrame(project);
    if (!(frame instanceof IdeFrame)) return;

    ListPopup popup = createActionPopup(e.getDataContext(), ((IdeFrame)frame).getComponent(), null);
    popup.showCenteredInCurrentWindow(project);
  }

  @NotNull
  private ListPopup createActionPopup(@NotNull DataContext context, @NotNull JComponent component, @Nullable Runnable disposeCallback) {
    DefaultActionGroup group = createPopupActionGroup(component, context);
    ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
      myPopupTitle, group, context, false, shouldShowDisabledActions(), false, disposeCallback, getMaxRows(), getPreselectCondition());
    popup.setMinimumSize(new Dimension(getMinWidth(), getMinHeight()));
    return popup;
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    JPanel panel = new JPanel(new GridBagLayout());
    ComboBoxButton button = createComboBoxButton(presentation);
    panel.add(button,
              new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, JBUI.insets(0, 3), 0, 0));
    return panel;
  }

  protected ComboBoxButton createComboBoxButton(Presentation presentation) {
    return new ComboBoxButton(presentation);
  }

  public boolean isSmallVariant() {
    return mySmallVariant;
  }

  public void setSmallVariant(boolean smallVariant) {
    mySmallVariant = smallVariant;
  }

  public void setPopupTitle(String popupTitle) {
    myPopupTitle = popupTitle;
  }

  protected boolean shouldShowDisabledActions() {
    return false;
  }

  @NotNull
  protected abstract DefaultActionGroup createPopupActionGroup(JComponent button);

  @SuppressWarnings("unused")
  @NotNull
  protected DefaultActionGroup createPopupActionGroup(JComponent button, @NotNull  DataContext dataContext) {
    return createPopupActionGroup(button);
  }

  protected int getMaxRows() {
    return 30;
  }

  protected int getMinHeight() {
    return 1;
  }

  protected int getMinWidth() {
    return 1;
  }

  protected class ComboBoxButton extends JButton implements UserActivityProviderComponent {
    private final Presentation myPresentation;
    private boolean myForcePressed = false;
    private PropertyChangeListener myButtonSynchronizer;

    public ComboBoxButton(Presentation presentation) {
      myPresentation = presentation;
      setModel(new MyButtonModel());
      getModel().setEnabled(myPresentation.isEnabled());
      setVisible(presentation.isVisible());
      setHorizontalAlignment(LEFT);
      setFocusable(ScreenReader.isActive());
      putClientProperty("styleCombo", ComboBoxAction.this);
      setMargin(JBUI.insets(0, 5, 0, 2));
      if (isSmallVariant() && !UIUtil.isUnderGTKLookAndFeel()) {
        setFont(JBUI.Fonts.label(11));
      }

      //noinspection HardCodedStringLiteral
      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(final MouseEvent e) {
          if (SwingUtilities.isLeftMouseButton(e)) {
            e.consume();
            doClick();
          }
        }
      });
      addMouseMotionListener(new MouseMotionAdapter() {
        @Override
        public void mouseDragged(MouseEvent e) {
          mouseMoved(MouseEventAdapter.convert(e, e.getComponent(),
                                               MouseEvent.MOUSE_MOVED,
                                               e.getWhen(),
                                               e.getModifiers() | e.getModifiersEx(),
                                               e.getX(),
                                               e.getY()));
        }
      });
    }

    @Override
    protected void fireActionPerformed(ActionEvent event) {
      if (!myForcePressed) {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> showPopup());
      }
    }

    @NotNull
    private Runnable setForcePressed() {
      myForcePressed = true;
      repaint();

      return () -> {
        // give the button a chance to handle action listener
        ApplicationManager.getApplication().invokeLater(() -> {
          myForcePressed = false;
          repaint();
        }, ModalityState.any());
        repaint();
        fireStateChanged();
      };
    }

    @Nullable
    @Override
    public String getToolTipText() {
      return myForcePressed || Registry.is("ide.helptooltip.enabled") ? null : super.getToolTipText();
    }

    public void showPopup() {
      JBPopup popup = createPopup(setForcePressed());
      if (Registry.is("ide.helptooltip.enabled")) {
        HelpTooltip.setMasterPopup(this, popup);
      }

      popup.showUnderneathOf(this);
    }

    protected JBPopup createPopup(Runnable onDispose) {
      return createActionPopup(getDataContext(), this, onDispose);
    }

    protected DataContext getDataContext() {
      return DataManager.getInstance().getDataContext(this);
    }

    @Override
    public void removeNotify() {
      if (myButtonSynchronizer != null) {
        myPresentation.removePropertyChangeListener(myButtonSynchronizer);
        myButtonSynchronizer = null;
      }
      super.removeNotify();
    }

    @Override
    public void addNotify() {
      super.addNotify();
      if (myButtonSynchronizer == null) {
        myButtonSynchronizer = new MyButtonSynchronizer();
        myPresentation.addPropertyChangeListener(myButtonSynchronizer);
      }
      initButton();
    }

    private void initButton() {
      setIcon(myPresentation.getIcon());
      setText(myPresentation.getText());
      updateTooltipText(myPresentation.getDescription());
      updateButtonSize();
    }

    private void updateTooltipText(String description) {
      String tooltip = KeymapUtil.createTooltipText(description, ComboBoxAction.this);
      if (Registry.is("ide.helptooltip.enabled") && StringUtil.isNotEmpty(tooltip)) {
        HelpTooltip.dispose(this);
        new HelpTooltip().setDescription(tooltip).setLocation(HelpTooltip.Alignment.BOTTOM).installOn(this);
      } else {
        setToolTipText(!tooltip.isEmpty() ? tooltip : null);
      }
    }

    protected class MyButtonModel extends DefaultButtonModel {
      @Override
      public boolean isPressed() {
        return myForcePressed || super.isPressed();
      }

      @Override
      public boolean isArmed() {
        return myForcePressed || super.isArmed();
      }
    }

    private class MyButtonSynchronizer implements PropertyChangeListener {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        String propertyName = evt.getPropertyName();
        if (Presentation.PROP_TEXT.equals(propertyName)) {
          setText((String)evt.getNewValue());
          updateButtonSize();
        }
        else if (Presentation.PROP_DESCRIPTION.equals(propertyName)) {
          updateTooltipText((String)evt.getNewValue());
        }
        else if (Presentation.PROP_ICON.equals(propertyName)) {
          setIcon((Icon)evt.getNewValue());
          updateButtonSize();
        }
        else if (Presentation.PROP_ENABLED.equals(propertyName)) {
          setEnabled(((Boolean)evt.getNewValue()).booleanValue());
        }
      }
    }

    @Override
    public boolean isOpaque() {
      return !isSmallVariant();
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension prefSize = super.getPreferredSize();
      int width = prefSize.width
                  + (myPresentation != null && isArrowVisible(myPresentation) ? getArrowIcon(isEnabled()).getIconWidth() : 0)
                  + (StringUtil.isNotEmpty(getText()) ? getIconTextGap() : 0)
                  + (UIUtil.isUnderWin10LookAndFeel() ? JBUI.scale(6) : 0);

      Dimension size = new Dimension(width, isSmallVariant() ? JBUI.scale(24) : Math.max(JBUI.scale(24), prefSize.height));
      JBInsets.addTo(size, getMargin());
      return size;
    }

    @Override
    public Dimension getMinimumSize() {
      return new Dimension(super.getMinimumSize().width, getPreferredSize().height);
    }

    @Override
    public Font getFont() {
      return isSmallVariant() ? UIUtil.getToolbarFont() : UIUtil.getLabelFont();
    }

    @Override
    protected Graphics getComponentGraphics(Graphics graphics) {
      return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics));
    }

    @Override
    public void paint(Graphics g) {
      super.paint(g);
      if (!isArrowVisible(myPresentation)) {
        return;
      }
      Icon icon = getArrowIcon(isEnabled());
      int x = getWidth() - icon.getIconWidth() - getInsets().right - getMargin().right -
              (UIUtil.isUnderWin10LookAndFeel() ? JBUI.scale(3) : 0); // Different icons correction

      icon.paintIcon(null, g, x, (getHeight() - icon.getIconHeight()) / 2);
    }

    protected boolean isArrowVisible(@NotNull Presentation presentation) {
      return true;
    }

    @Override public void updateUI() {
      super.updateUI();
      setMargin(JBUI.insets(0, 5, 0, 2));
      updateButtonSize();
    }

    protected void updateButtonSize() {
      invalidate();
      repaint();
      setSize(getPreferredSize());
      repaint();
    }
  }

  protected Condition<AnAction> getPreselectCondition() { return null; }
}
