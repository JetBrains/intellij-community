// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.*;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Path2D;

public abstract class ComboBoxAction extends AnAction implements CustomComponentAction {
  private static Icon myIcon;
  private static Icon myDisabledIcon;

  public static Icon getArrowIcon(boolean enabled) {
    if (myIcon != AllIcons.General.ArrowDown) {
      myIcon = UIManager.getIcon("ComboBoxButton.arrowIcon");
      myDisabledIcon = UIManager.getIcon("ComboBoxButton.arrowIconDisabled");

      if (myIcon == null) myIcon = AllIcons.General.ArrowDown;
      if (myDisabledIcon == null) myDisabledIcon = IconLoader.getDisabledIcon(AllIcons.General.ArrowDown);
    }
    return enabled ? myIcon : myDisabledIcon;
  }
  private boolean mySmallVariant = true;
  private String myPopupTitle;


  protected ComboBoxAction() {
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
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

  /** @deprecated use {@link ComboBoxAction#createCustomComponent(Presentation, String)} */
  @Deprecated
  @NotNull
  @Override
  public JComponent createCustomComponent(@NotNull Presentation presentation) {
    return createCustomComponent(presentation, ActionPlaces.UNKNOWN);
  }

  @NotNull
  @Override
  public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    JPanel panel = new JPanel(new GridBagLayout());
    ComboBoxButton button = createComboBoxButton(presentation);
    GridBagConstraints constraints = new GridBagConstraints(
      0, 0, 1, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, JBInsets.create(0, 3), 0, 0);
    panel.add(button, constraints);
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
    private boolean myForcePressed;
    private String myTooltipText;

    public ComboBoxButton(Presentation presentation) {
      myPresentation = presentation;

      setIcon(myPresentation.getIcon());
      setText(myPresentation.getText());
      setEnabled(myPresentation.isEnabled());

      myTooltipText = myPresentation.getDescription();
      updateTooltipText();

      setModel(new MyButtonModel());
      getModel().setEnabled(myPresentation.isEnabled());
      setVisible(presentation.isVisible());
      setHorizontalAlignment(LEFT);
      setFocusable(ScreenReader.isActive());
      putClientProperty("styleCombo", ComboBoxAction.this);
      setMargin(JBUI.insets(0, 5, 0, 2));
      if (isSmallVariant()) {
        setFont(JBUI.Fonts.toolbarSmallComboBoxFont());
      }

      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(final MouseEvent e) {
          if (SwingUtilities.isLeftMouseButton(e)) {
            e.consume();
            if (e.isShiftDown()) {
              doShiftClick();
            } else {
              doClick();
            }
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

      myPresentation.addPropertyChangeListener(evt -> {
        String propertyName = evt.getPropertyName();
        if (Presentation.PROP_TEXT.equals(propertyName)) {
          setText((String)evt.getNewValue());
        }
        else if (Presentation.PROP_DESCRIPTION.equals(propertyName)) {
          myTooltipText = (String)evt.getNewValue();
          updateTooltipText();
        }
        else if (Presentation.PROP_ICON.equals(propertyName)) {
          setIcon((Icon)evt.getNewValue());
        }
        else if (Presentation.PROP_ENABLED.equals(propertyName)) {
          setEnabled((Boolean)evt.getNewValue());
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
      HelpTooltip.dispose(this);
      super.removeNotify();
    }

    @Override
    public void addNotify() {
      super.addNotify();
      updateTooltipText();
    }

    private void updateTooltipText() {
      HelpTooltip.dispose(this);

      if (Registry.is("ide.helptooltip.enabled") && StringUtil.isNotEmpty(myTooltipText)) {
        String shortcut = KeymapUtil.getFirstKeyboardShortcutText(ComboBoxAction.this);
        new HelpTooltip().setTitle(myTooltipText).setShortcut(shortcut).installOn(this);
      } else {
        String tooltip = KeymapUtil.createTooltipText(myTooltipText, ComboBoxAction.this);
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

    @Override
    public boolean isOpaque() {
      return !isSmallVariant();
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension prefSize = super.getPreferredSize();
      int width = prefSize.width + (StringUtil.isNotEmpty(getText()) ? getIconTextGap() : 0) +
       (myPresentation == null || !isArrowVisible(myPresentation) ? 0 : JBUIScale.scale(16));

      Dimension size = new Dimension(width, isSmallVariant() ? JBUIScale.scale(24) : Math.max(JBUIScale.scale(24), prefSize.height));
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

      if (UIUtil.isUnderWin10LookAndFeel()) {
        Icon icon = getArrowIcon(isEnabled());
        int x = getWidth() - icon.getIconWidth() - getInsets().right - getMargin().right - JBUIScale.scale(3);
        int y = (getHeight() - icon.getIconHeight()) / 2;
        icon.paintIcon(null, g, x, y);
      }
      else {
        Graphics2D g2 = (Graphics2D)g.create();
        try {
          int iconSize = JBUIScale.scale(16);
          int x = getWidth() - iconSize - getInsets().right - getMargin().right; // Different icons correction
          int y = (getHeight() - iconSize)/2;

          g2.translate(x, y);
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

          g2.setColor(JBUI.CurrentTheme.Arrow.foregroundColor(isEnabled()));

          Path2D arrow = new Path2D.Float(Path2D.WIND_EVEN_ODD);
          arrow.moveTo(JBUIScale.scale(3.5f), JBUIScale.scale(6f));
          arrow.lineTo(JBUIScale.scale(12.5f), JBUIScale.scale(6f));
          arrow.lineTo(JBUIScale.scale(8f), JBUIScale.scale(11f));
          arrow.closePath();

          g2.fill(arrow);
        }
        finally {
          g2.dispose();
        }
      }
    }

    protected boolean isArrowVisible(@NotNull Presentation presentation) {
      return true;
    }

    @Override public void updateUI() {
      super.updateUI();
      setMargin(JBUI.insets(0, 5, 0, 2));
    }

    /**
     * @deprecated This method is noop. Set icon, text and tooltip in the constructor
     * or property change listener for proper computation of preferred size.
     * Other updates happen in Swing.
     */
    @Deprecated
    @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
    protected void updateButtonSize() {}

    @ApiStatus.Experimental
    protected void doShiftClick() {
      doClick();
    }
  }

  protected Condition<AnAction> getPreselectCondition() { return null; }
}
