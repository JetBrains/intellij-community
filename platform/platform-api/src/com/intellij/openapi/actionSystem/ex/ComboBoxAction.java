// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.UserActivityProviderComponent;
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
import java.beans.PropertyChangeEvent;

public abstract class ComboBoxAction extends AnAction implements CustomComponentAction {
  private static Icon myIcon;
  private static Icon myDisabledIcon;

  public static @NotNull Icon getArrowIcon(boolean enabled) {
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

  protected @NotNull ListPopup createActionPopup(@NotNull DataContext context, @NotNull JComponent component, @Nullable Runnable disposeCallback) {
    return createActionPopup(createPopupActionGroup(component, context), context, disposeCallback);
  }

  protected ListPopup createActionPopup(DefaultActionGroup group, @NotNull DataContext context, @Nullable Runnable disposeCallback) {
    ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
      myPopupTitle, group, context, false, shouldShowDisabledActions(), false, disposeCallback, getMaxRows(), getPreselectCondition());
    popup.setMinimumSize(new Dimension(getMinWidth(), getMinHeight()));
    return popup;
  }

  @Override
  public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    ComboBoxButton button = createComboBoxButton(presentation);
    if (isNoWrapping(place)) return button;

    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints constraints = new GridBagConstraints(
      0, 0, 1, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, JBInsets.create(0, 3), 0, 0);
    panel.add(button, constraints);
    return panel;
  }

  protected boolean isNoWrapping(@NotNull String place) {
    return ExperimentalUI.isNewUI() && ActionPlaces.isMainToolbar(place);
  }

  protected @NotNull ComboBoxButton createComboBoxButton(@NotNull Presentation presentation) {
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

  /** @deprecated override {@link #createPopupActionGroup(JComponent, DataContext)} instead */
  @Deprecated
  protected @NotNull DefaultActionGroup createPopupActionGroup(JComponent button) {
    throw new UnsupportedOperationException();
  }

  protected @NotNull DefaultActionGroup createPopupActionGroup(@NotNull JComponent button, @NotNull  DataContext dataContext) {
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

    @Override
    public String getUIClassID() {
      return "ComboBoxButtonUI";
    }

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
                                               UIUtil.getAllModifiers(e),
                                               e.getX(),
                                               e.getY()));
        }
      });

      myPresentation.addPropertyChangeListener(evt -> {
        presentationChanged(evt);
      });
    }

    /**
     * Sets a label for this component.  If the given label has displayed mnemonic,
     * it will call the {@code #doClick} method when the mnemonic is activated.
     *
     * @param label the label referring to this component
     * @see JLabel#setLabelFor
     */
    public void setLabel(@NotNull JLabel label) {
      label.setLabelFor(this);

      ActionMap map = label.getActionMap();
      String name = "release"; // BasicLabelUI.Actions.RELEASE
      Action old = map.get(name);
      map.put(name, new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent event) {
          if (old != null) old.actionPerformed(event);
          doClick();
        }
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
    public @NotNull Presentation getPresentation() {
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

    @Override
    public @Nullable String getToolTipText() {
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

    protected @NotNull JBPopup createPopup(@Nullable Runnable onDispose) {
      return createActionPopup(getDataContext(), this, onDispose);
    }

    protected @NotNull DataContext getDataContext() {
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
    public Font getFont() {
      return isSmallVariant() ? UIUtil.getToolbarFont() : StartupUiUtil.getLabelFont();
    }

    @Override
    protected Graphics getComponentGraphics(Graphics graphics) {
      return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics));
    }

    /*
    should be used with margin
     */
    public int getArrowGap() {
      return 0;
    }

    protected boolean isArrowVisible(@NotNull Presentation presentation) {
      return true;
    }

    public boolean isArrowVisible() {
      return myPresentation != null && isArrowVisible(myPresentation);
    }

    public boolean isSmallVariant() {
      return ComboBoxAction.this.isSmallVariant();
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
