// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
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
import java.util.function.Supplier;

public class ComponentValidator {
  private static final String PROPERTY_NAME = "JComponent.componentValidator";
  private static final JBValue MAX_WIDTH = new JBValue.UIInteger("ValidationTooltip.maxWidth", 384);

  private static final Color ERROR_BORDER_COLOR = JBColor.namedColor("ValidationTooltip.errorBorderColor", 0xE0A8A9);
  private static final Color ERROR_BACKGROUND_COLOR = JBColor.namedColor("ValidationTooltip.errorBackgroundColor", 0xF5E6E7);

  private static final Color WARNING_BORDER_COLOR = JBColor.namedColor("ValidationTooltip.warningBorderColor", 0xE0CEA8);
  private static final Color WARNING_BACKGROUND_COLOR = JBColor.namedColor("ValidationTooltip.warningBackgroundColor", 0xF5F0E6);

  private final Disposable parentDisposable;
  private ValidationInfo validationInfo;

  private Supplier<ValidationInfo> infoSupplier;
  private ComponentPopupBuilder popupBuilder;
  private JBPopup popup;
  private RelativePoint popupLocation;
  private Dimension popupSize;

  public ComponentValidator(@NotNull Disposable parentDisposable) {
    this.parentDisposable = parentDisposable;
  }

  public ComponentValidator withFocusValidation(Supplier<ValidationInfo> infoSupplier) {
    this.infoSupplier = infoSupplier;
    return this;
  }

  public Optional<ComponentValidator> installOn(@NotNull JComponent component) {
    Component fc = getFocusable(component);
    if (fc != null) {
      component.putClientProperty(PROPERTY_NAME, this);

      FocusListener focusListener = new ValidationFocusListener();
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
      Disposer.register(parentDisposable, () -> {
        fc.removeFocusListener(focusListener);

        if (w != null) {
          w.removeComponentListener(componentListener);
        }
      });
      return Optional.of(this);
    } else {
      return Optional.empty();
    }
  }

  public static Optional<ComponentValidator> getInstance(@NotNull JComponent component) {
    return Optional.ofNullable((ComponentValidator)component.getClientProperty(PROPERTY_NAME));
  }

  private void reset() {
    if (validationInfo != null && validationInfo.component != null) {
      validationInfo.component.putClientProperty("JComponent.outline", null);
    }

    if (popup != null && popup.isVisible()) {
      popup.cancel();
    }

    popupBuilder = null;
    popup = null;
    popupLocation = null;
    popupSize = null;
    validationInfo = null;
  }

  public void updateInfo(@Nullable ValidationInfo info) {
    boolean resetInfo = info == null && validationInfo != null;
    boolean newInfo = info != null && !info.equals(validationInfo);
    if (resetInfo || newInfo) {
      reset();
      validationInfo = info;

      if (newInfo) {
        if (validationInfo.component != null) {
          validationInfo.component.putClientProperty("JComponent.outline", validationInfo.warning ? "warning" : "error");
        }

        if (StringUtil.isNotEmpty(validationInfo.message)) {
          JLabel tipComponent = new JLabel();
          View v = BasicHTML.createHTMLView(tipComponent, String.format("<html>%s</html>", validationInfo.message));
          String labelText = v.getPreferredSpan(View.X_AXIS) > MAX_WIDTH.get() ?
                             String.format("<html><div width=%d>%s</div></html>", MAX_WIDTH.get(), validationInfo.message) :
                             String.format("<html><div>%s</div></html>", validationInfo.message);

          tipComponent.setText(labelText);
          tipComponent.setBackground(validationInfo.warning ? WARNING_BACKGROUND_COLOR : ERROR_BACKGROUND_COLOR);
          tipComponent.setOpaque(true);
          tipComponent.setBorder(getBorder());
          popupSize = tipComponent.getPreferredSize();

          popupBuilder = JBPopupFactory.getInstance().createComponentPopupBuilder(tipComponent, null).
            setBorderColor(validationInfo.warning ? WARNING_BORDER_COLOR : ERROR_BORDER_COLOR).
                                         setShowShadow(false);

          if (getFocusable(validationInfo.component).hasFocus()) {
            showPopup();
          }
        }
      }
    }
  }

  private void showPopup() {
    if (popupBuilder != null && validationInfo != null && validationInfo.component != null) {
      popup = popupBuilder.createPopup();

      Insets i = validationInfo.component.getInsets();
      Point point = new Point(JBUI.scale(40), i.top - JBUI.scale(6) - popupSize.height);
      popupLocation = new RelativePoint(validationInfo.component, point);

      popup.show(popupLocation);
    }
  }

  private static Border getBorder() {
    Insets i = UIManager.getInsets("ValidationTooltip.borderInsets");
    return i != null ? new JBEmptyBorder(i) : JBUI.Borders.empty(4, 8);
  }

  private static Component getFocusable(Component source) {
    return source instanceof JComboBox && !((JComboBox)source).isEditable() ?
           source :
           UIUtil.uiTraverser(source).filter(c -> c instanceof JTextComponent && c.isFocusable()).toList().stream().findFirst().orElse(null);
  }

  private class ValidationFocusListener implements FocusListener {
    @Override
    public void focusGained(FocusEvent e) {
      showPopup();
    }

    @Override
    public void focusLost(FocusEvent e) {
      if (popup != null && popup.isVisible()) {
        popup.cancel();
        popup = null;
      }

      if(infoSupplier != null) {
        updateInfo(infoSupplier.get());
      }
    }
  }
}
