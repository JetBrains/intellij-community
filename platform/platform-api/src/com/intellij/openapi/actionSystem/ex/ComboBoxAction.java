// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.ex;

import com.intellij.icons.AllIcons;
import com.intellij.ide.HelpTooltip;
import com.intellij.ide.TooltipTitle;
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
import com.intellij.openapi.util.NlsContexts;
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
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Path2D;
import java.beans.PropertyChangeEvent;

public abstract class ComboBoxAction extends AnAction implements CustomComponentAction {
  private static Icon myIcon;
  private static Icon myDisabledIcon;

  @NotNull
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
  protected @NlsContexts.PopupTitle String myPopupTitle;


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
  protected ListPopup createActionPopup(@NotNull DataContext context, @NotNull JComponent component, @Nullable Runnable disposeCallback) {
    DefaultActionGroup group = createPopupActionGroup(component, context);
    ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
      myPopupTitle, group, context, false, shouldShowDisabledActions(), false, disposeCallback, getMaxRows(), getPreselectCondition());
    popup.setMinimumSize(new Dimension(getMinWidth(), getMinHeight()));
    return popup;
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

  @NotNull
  protected ComboBoxButton createComboBoxButton(@NotNull Presentation presentation) {
    return new ComboBoxButton(presentation);
  }

  public boolean isSmallVariant() {
    return mySmallVariant;
  }

  public void setSmallVariant(boolean smallVariant) {
    mySmallVariant = smallVariant;
  }

  public void setPopupTitle(@NotNull @NlsContexts.PopupTitle String popupTitle) {
    myPopupTitle = popupTitle;
  }

  protected boolean shouldShowDisabledActions() {
    return false;
  }

  @NotNull
  protected abstract DefaultActionGroup createPopupActionGroup(JComponent button);

  @NotNull
  protected DefaultActionGroup createPopupActionGroup(@NotNull JComponent button, @NotNull  DataContext dataContext) {
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

  public class ComboBoxButton extends JButton implements UserActivityProviderComponent {
    private final Presentation myPresentation;
    private boolean myForcePressed;
    private @TooltipTitle String myTooltipText;

    public ComboBoxButton(@NotNull Presentation presentation) {
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
      setMargin(JBUI.insets(0, 8, 0, 5));
      if (isSmallVariant()) {
        setFont(JBUI.Fonts.toolbarSmallComboBoxFont());
      }

      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(final MouseEvent e) {
          if (performClickOnMousePress()) {
            if (SwingUtilities.isLeftMouseButton(e)) {
              e.consume();
              if (e.isShiftDown()) {
                doShiftClick();
              }
              else {
                doClick();
              }
            } else if(SwingUtilities.isRightMouseButton(e)){
              doRightClick();
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
        presentationChanged(evt);
      });
    }

    protected void presentationChanged(PropertyChangeEvent evt) {
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
    }

    protected boolean performClickOnMousePress() {
      return true;
    }

    @TestOnly
    @NotNull
    public Presentation getPresentation() {
      return myPresentation;
    }

    @Override
    protected void fireActionPerformed(ActionEvent event) {
      if (!myForcePressed) {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> showPopup());
      }
    }

    private void setForcePressed(boolean forcePressed) {
      if (myForcePressed == forcePressed) return;
      myForcePressed = forcePressed;
      repaint();
    }

    private void releaseForcePressed() {
      // give the button a chance to handle action listener
      ApplicationManager.getApplication().invokeLater(() -> setForcePressed(false), ModalityState.any());
      repaint();
      fireStateChanged();
    }

    @Nullable
    @Override
    public String getToolTipText() {
      return myForcePressed || Registry.is("ide.helptooltip.enabled") ? null : super.getToolTipText();
    }

    public void showPopup() {
      JBPopup popup = createPopup(this::releaseForcePressed);
      setForcePressed(true);
      if (Registry.is("ide.helptooltip.enabled")) {
        HelpTooltip.setMasterPopup(this, popup);
      }

      popup.showUnderneathOf(this);
    }

    @NotNull
    protected JBPopup createPopup(@Nullable Runnable onDispose) {
      return createActionPopup(getDataContext(), this, onDispose);
    }

    @NotNull
    protected DataContext getDataContext() {
      return ActionToolbar.getDataContextFor(this);
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
      }
      else {
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
      Insets i = getInsets();
      int width = prefSize.width + (StringUtil.isNotEmpty(getText()) ? getIconTextGap() : 0) +
       (myPresentation == null || !isArrowVisible(myPresentation) ? 0 : JBUIScale.scale(16));

      int height = ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height + i.top + i.bottom;
      if (!isSmallVariant()) {
        height = Math.max(height, prefSize.height);
      }
      Dimension size = new Dimension(width, height);
      JBInsets.addTo(size, getMargin());
      return size;
    }

    @Override
    public Dimension getMinimumSize() {
      return new Dimension(super.getMinimumSize().width, getPreferredSize().height);
    }

    @Override
    public Font getFont() {
      return isSmallVariant() ? UIUtil.getToolbarFont() : StartupUiUtil.getLabelFont();
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
        int x = getWidth() - icon.getIconWidth() - getInsets().right - getMargin().right - JBUIScale.scale(3) + getArrowGap();
        int y = (getHeight() - icon.getIconHeight()) / 2;
        icon.paintIcon(null, g, x, y);
      }
      else {
        Graphics2D g2 = (Graphics2D)g.create();
        try {
          int iconSize = JBUIScale.scale(16);
          int x = getWidth() - iconSize - getInsets().right - getMargin().right + getArrowGap(); // Different icons correction
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

    /*
    should be used with margin
     */
    protected int getArrowGap() {
      return 0;
    }

    protected boolean isArrowVisible(@NotNull Presentation presentation) {
      return true;
    }

    @Override
    public void updateUI() {
      super.updateUI();
      setMargin(JBUI.insets(0, 8, 0, 5));
    }

    @ApiStatus.Experimental
    protected void doShiftClick() {
      doClick();
    }

    protected void doRightClick() {}
  }

  protected Condition<AnAction> getPreselectCondition() { return null; }
}
