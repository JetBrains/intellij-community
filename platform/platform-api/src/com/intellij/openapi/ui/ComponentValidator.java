// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBValue;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.JTextComponent;
import javax.swing.text.View;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeListener;
import java.util.Optional;
import java.util.function.Consumer;

import static com.intellij.util.ui.JBUI.CurrentTheme.Validator.*;

public class ComponentValidator {
  private static final String PROPERTY_NAME = "JComponent.componentValidator";
  private static final JBValue MAX_WIDTH = new JBValue.UIInteger("ValidationTooltip.maxWidth", 384);

  private final Disposable parentDisposable;
  private Consumer<ComponentValidator> validator;

  private ValidationInfo validationInfo;

  private ComponentPopupBuilder popupBuilder;
  private JBPopup popup;
  private RelativePoint popupLocation;
  private Dimension popupSize;
  private boolean disableValidation;

  public ComponentValidator(@NotNull Disposable parentDisposable) {
    this.parentDisposable = parentDisposable;
  }

  public ComponentValidator withValidator(@NotNull Consumer<ComponentValidator> validator) {
    this.validator = validator;
    return this;
  }

  public ComponentValidator andStartOnFocusLost() {
    disableValidation = true;
    return this;
  }

  public ComponentValidator installOn(@NotNull JComponent component) {
    Component fc = getFocusable(component).orElse(null);
    if (fc == null) {
      return null;
    }
    else {
      component.putClientProperty(PROPERTY_NAME, this);

      FocusListener focusListener = new ValidationFocusListener();
      MouseListener mouseListener = new ValidationMouseListener();

      ComponentListener componentListener = new ComponentAdapter() {
        @Override public void componentMoved(ComponentEvent e) {
          if (popup != null && popup.isVisible() && popupLocation != null) {
            popup.setLocation(popupLocation.getScreenPoint());
          }
        }
      };

      PropertyChangeListener ancestorListener = e -> {
        Window w = (Window)UIUtil.findParentByCondition((Component)e.getSource(), v -> v instanceof Window);
        if (w != null) {
          if (e.getNewValue() != null) {
            w.addComponentListener(componentListener);
          } else {
            w.removeComponentListener(componentListener);
          }
        }
      };

      Window w = (Window)UIUtil.findParentByCondition(component, v -> v instanceof Window);
      if (w != null) {
        w.addComponentListener(componentListener);
      } else {
        component.addPropertyChangeListener("ancestor", ancestorListener);
      }

      fc.addFocusListener(focusListener);
      fc.addMouseListener(mouseListener);
      Disposer.register(parentDisposable, () -> {
        fc.removeFocusListener(focusListener);
        fc.removeMouseListener(mouseListener);

        if (w != null) {
          w.removeComponentListener(componentListener);
        }
      });
      return this;
    }
  }

  public void revalidate() {
    if (validator != null) {
      validator.accept(this);
    }
  }

  public static Optional<ComponentValidator> getInstance(@NotNull JComponent component) {
    return Optional.ofNullable((ComponentValidator)component.getClientProperty(PROPERTY_NAME));
  }

  private void reset() {
    if (validationInfo != null && validationInfo.component != null) {
      validationInfo.component.putClientProperty("JComponent.outline", null);
      validationInfo.component.revalidate();
      validationInfo.component.repaint();
    }

    hidePopup();

    popupBuilder = null;
    popupLocation = null;
    popupSize = null;
    validationInfo = null;
  }

  public void updateInfo(@Nullable ValidationInfo info) {
    if (disableValidation) return;

    boolean resetInfo = info == null && validationInfo != null;
    boolean newInfo = info != null && !info.equals(validationInfo);
    if (resetInfo || newInfo) {
      reset();
      validationInfo = info;

      if (newInfo) {
        if (validationInfo.component != null) {
          validationInfo.component.putClientProperty("JComponent.outline", validationInfo.warning ? "warning" : "error");
          validationInfo.component.revalidate();
          validationInfo.component.repaint();
        }

        if (StringUtil.isNotEmpty(validationInfo.message)) {
          JLabel tipComponent = new JLabel();
          View v = BasicHTML.createHTMLView(tipComponent, String.format("<html>%s</html>", validationInfo.message));
          String labelText = v.getPreferredSpan(View.X_AXIS) > MAX_WIDTH.get() ?
                             String.format("<html><div width=%d>%s</div></html>", MAX_WIDTH.get(), validationInfo.message) :
                             String.format("<html><div>%s</div></html>", validationInfo.message);

          tipComponent.setText(labelText);
          tipComponent.setBackground(validationInfo.warning ? warningBackgroundColor() : errorBackgroundColor());
          tipComponent.setOpaque(true);
          tipComponent.setBorder(getBorder());
          popupSize = tipComponent.getPreferredSize();

          popupBuilder = JBPopupFactory.getInstance().createComponentPopupBuilder(tipComponent, null).
            setBorderColor(validationInfo.warning ? warningBorderColor() : errorBorderColor()).
            setCancelOnClickOutside(false).
            setCancelOnMouseOutCallback(e -> e.getID() == MouseEvent.MOUSE_PRESSED && !withinComponent(e)).
            setShowShadow(false);

          getFocusable(validationInfo.component).ifPresent(fc -> {
            if (fc.hasFocus()) {
              showPopup();
            }
          });
        }
      }
    }
  }

  private boolean withinComponent(@NotNull MouseEvent e) {
    if (validationInfo != null && validationInfo.component != null && validationInfo.component.isShowing()) {
      Rectangle screenBounds = new Rectangle(validationInfo.component.getLocationOnScreen(), validationInfo.component.getSize());
      return screenBounds.contains(e.getLocationOnScreen());
    }
    else {
      return false;
    }
  }

  private void showPopup() {
    if ((popup == null || !popup.isVisible()) &&
        popupBuilder != null &&
        validationInfo != null &&
        validationInfo.component != null &&
        validationInfo.component.isEnabled()) {
      popup = popupBuilder.createPopup();

      Insets i = validationInfo.component.getInsets();
      Point point = new Point(JBUI.scale(40), i.top - JBUI.scale(6) - popupSize.height);
      popupLocation = new RelativePoint(validationInfo.component, point);

      popup.show(popupLocation);
    }
  }

  private void hidePopup() {
    if (popup != null && popup.isVisible()) {
      popup.cancel();
      popup = null;
    }
  }

  private static Border getBorder() {
    Insets i = UIManager.getInsets("ValidationTooltip.borderInsets");
    return i != null ? new JBEmptyBorder(i) : JBUI.Borders.empty(4, 8);
  }

  private static Optional<Component> getFocusable(Component source) {
    return source instanceof JComboBox && !((JComboBox)source).isEditable() ?
           Optional.of(source) :
           UIUtil.uiTraverser(source).filter(c -> c instanceof JTextComponent && c.isFocusable()).toList().stream().findFirst();
  }

  private class ValidationFocusListener implements FocusListener {
    @Override
    public void focusGained(FocusEvent e) {
      showPopup();
    }

    @Override
    public void focusLost(FocusEvent e) {
      hidePopup();

      if (disableValidation) {
        disableValidation = false;
        revalidate();
      }
    }
  }

  private class ValidationMouseListener extends MouseAdapter {
    @Override
    public void mouseEntered(MouseEvent e) {
      showPopup();
    }

    @Override
    public void mouseExited(MouseEvent e) {
      if (validationInfo != null) {
        getFocusable(validationInfo.component).ifPresent(fc -> {
          if (!fc.hasFocus()) {
            hidePopup();
          }
        });
      }
    }
  }
}
