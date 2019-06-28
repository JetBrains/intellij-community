// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBValue;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.DefaultCaret;
import javax.swing.text.EditorKit;
import javax.swing.text.JTextComponent;
import javax.swing.text.View;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeListener;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.intellij.util.ui.JBUI.CurrentTheme.Validator.*;

public class ComponentValidator {
  private static final String PROPERTY_NAME = "JComponent.componentValidator";
  private static final JBValue MAX_WIDTH = new JBValue.UIInteger("ValidationTooltip.maxWidth", 384);

  /**
   * Convenient error/warning outline provider for {@link ComponentWithBrowseButton}
   * ComponentWithBrowseButton isn't a {@link ErrorBorderCapable} component so it needs a special provider.
   * Suitable for {@link ComponentWithBrowseButton} and it's descendants.
   */
  public static final Function<JComponent, JComponent> CWBB_PROVIDER = c -> ((ComponentWithBrowseButton)c).getChildComponent();

  private final Disposable parentDisposable;
  private Supplier<? extends ValidationInfo> validator;
  private Supplier<? extends ValidationInfo> focusValidator;

  private Function<? super JComponent, ? extends JComponent> outlineProvider = Function.identity();
  private HyperlinkListener hyperlinkListener;

  private ValidationInfo validationInfo;
  private final Alarm popupAlarm = new Alarm();
  private boolean isOverPopup;

  private ComponentPopupBuilder popupBuilder;
  private JBPopup popup;
  private RelativePoint popupLocation;
  private Dimension popupSize;
  private boolean disableValidation;

  public ComponentValidator(@NotNull Disposable parentDisposable) {
    this.parentDisposable = parentDisposable;
  }

  /**
   * @deprecated Use {@link ComponentValidator#withValidator(Supplier)} instead
   */
  @Deprecated
  public ComponentValidator withValidator(@NotNull Consumer<? super ComponentValidator> validator) {
    this.validator = () -> {
      validator.accept(this);
      return validationInfo;
    };
    return this;
  }

  public ComponentValidator withValidator(@NotNull Supplier<? extends ValidationInfo> validator) {
    this.validator = validator;
    return this;
  }

  public ComponentValidator withFocusValidator(@NotNull Supplier<? extends ValidationInfo> focusValidator) {
    this.focusValidator = focusValidator;
    return this;
  }

  public ComponentValidator withHyperlinkListener(@NotNull HyperlinkListener hyperlinkListener) {
    this.hyperlinkListener = hyperlinkListener;
    return this;
  }

  public ComponentValidator withOutlineProvider(@NotNull Function<? super JComponent, ? extends JComponent> outlineProvider) {
    this.outlineProvider = outlineProvider;
    return this;
  }

  public ComponentValidator andStartOnFocusLost() {
    disableValidation = true;
    return this;
  }

  public ComponentValidator installOn(@NotNull JComponent component) {
    Component fc = getFocusable(component).orElse(outlineProvider.apply(component));
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
      Window w = (Window)ComponentUtil.findParentByCondition((Component)e.getSource(), v -> v instanceof Window);
      if (w != null) {
        if (e.getNewValue() != null) {
          w.addComponentListener(componentListener);
        } else {
          w.removeComponentListener(componentListener);
        }
      }
    };

    Window w = (Window)ComponentUtil.findParentByCondition(component, v -> v instanceof Window);
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

      validator = null;
      focusValidator = null;
      hyperlinkListener = null;
      outlineProvider = null;
      component.putClientProperty(PROPERTY_NAME, null);
    });
    return this;
  }

  /**
   * Convenient wrapper for mostly used scenario.
   */
  public ComponentValidator andRegisterOnDocumentListener(@NotNull JTextComponent textComponent) {
    textComponent.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        getInstance(textComponent).ifPresent(ComponentValidator::revalidate); // Don't use 'this' to avoid cyclic references.
      }
    });
    return this;
  }

  public void revalidate() {
    if (validator != null) {
      updateInfo(validator.get());
    }
  }

  public static Optional<ComponentValidator> getInstance(@NotNull JComponent component) {
    return Optional.ofNullable((ComponentValidator)component.getClientProperty(PROPERTY_NAME));
  }

  private void reset() {
    if (validationInfo != null && validationInfo.component != null) {
      outlineProvider.apply(validationInfo.component).putClientProperty("JComponent.outline", null);

      validationInfo.component.revalidate();
      validationInfo.component.repaint();
    }

    hidePopup(true);

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
        JComponent component = validationInfo.component;
        if (component != null) {
          outlineProvider.apply(component).putClientProperty("JComponent.outline", validationInfo.warning ? "warning" : "error");

          component.revalidate();
          component.repaint();
        }

        if (StringUtil.isNotEmpty(validationInfo.message)) {
          popupBuilder = createPopupBuilder(validationInfo, tipComponent -> {
            tipComponent.addHyperlinkListener(hyperlinkListener);
            tipComponent.addMouseListener(new TipComponentMouseListener());
            popupSize = tipComponent.getPreferredSize();
          }).setCancelOnMouseOutCallback(e -> e.getID() == MouseEvent.MOUSE_PRESSED && !withinComponent(info, e));

          getFocusable(component).ifPresent(fc -> {
            if (fc.hasFocus()) {
              showPopup();
            }
          });
        }
      }
    }
  }

  @NotNull
  public static ComponentPopupBuilder createPopupBuilder(@NotNull ValidationInfo info, @Nullable Consumer<? super JEditorPane> configurator) {
    JEditorPane tipComponent = new JEditorPane();
    View v = BasicHTML.createHTMLView(tipComponent, String.format("<html>%s</html>", info.message));
    String text = v.getPreferredSpan(View.X_AXIS) > MAX_WIDTH.get() ?
                  String.format("<html><div width=%d>%s</div></html>", MAX_WIDTH.get(), info.message) :
                  String.format("<html><div>%s</div></html>", info.message);

    tipComponent.setContentType("text/html");
    tipComponent.setEditable(false);
    tipComponent.setEditorKit(UIUtil.getHTMLEditorKit());

    EditorKit kit = tipComponent.getEditorKit();
    if (kit instanceof HTMLEditorKit) {
      StyleSheet css = ((HTMLEditorKit)kit).getStyleSheet();

      css.addRule("a, a:link {color:#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.linkColor()) + ";}");
      css.addRule("a:visited {color:#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.linkVisitedColor()) + ";}");
      css.addRule("a:hover {color:#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.linkHoverColor()) + ";}");
      css.addRule("a:active {color:#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.linkPressedColor()) + ";}");
      css.addRule("body {background-color:#" + ColorUtil.toHex(info.warning ? warningBackgroundColor() : errorBackgroundColor()) + ";}");
    }

    if (tipComponent.getCaret() instanceof DefaultCaret) {
      ((DefaultCaret)tipComponent.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
    }

    tipComponent.setCaretPosition(0);
    tipComponent.setText(text);

    tipComponent.setBackground(info.warning ? warningBackgroundColor() : errorBackgroundColor());
    tipComponent.setOpaque(true);
    tipComponent.setBorder(getBorder());

    if (configurator != null) {
      configurator.accept(tipComponent);
    }

    return JBPopupFactory.getInstance().createComponentPopupBuilder(tipComponent, null).
      setBorderColor(info.warning ? warningBorderColor() : errorBorderColor()).
      setCancelOnClickOutside(false).
      setShowShadow(false);
  }

  public static boolean withinComponent(@NotNull ValidationInfo info, @NotNull MouseEvent e) {
    if (info.component != null && info.component.isShowing()) {
      Rectangle screenBounds = new Rectangle(info.component.getLocationOnScreen(), info.component.getSize());
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
      Point point = new Point(JBUIScale.scale(40), i.top - JBUIScale.scale(6) - popupSize.height);
      popupLocation = new RelativePoint(validationInfo.component, point);

      popup.show(popupLocation);
    }
  }

  private void hidePopup(boolean now) {
    if (popup != null && popup.isVisible()) {
      if (now || hyperlinkListener == null) {
        popup.cancel();
        popup = null;
      } else {
        popupAlarm.addRequest(() -> {
          if (!isOverPopup || hyperlinkListener == null) {
            hidePopup(true);
          }
        }, Registry.intValue("ide.tooltip.initialDelay.highlighter"));
      }
    }
  }

  public static Border getBorder() {
    Insets i = UIManager.getInsets("ValidationTooltip.borderInsets");
    return i != null ? new JBEmptyBorder(i) : JBUI.Borders.empty(4, 8);
  }

  private static Optional<Component> getFocusable(Component source) {
    return (source instanceof JComboBox && !((JComboBox)source).isEditable() ||
            source instanceof JCheckBox ||
            source instanceof JRadioButton) ?
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
      hidePopup(false);

      ValidationInfo info = null;
      if (focusValidator != null) {
        info = focusValidator.get();
      }

      if (info != null) {
        updateInfo(info);
      } else if (disableValidation) {
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
            hidePopup(false);
          }
        });
      }
    }
  }

  private class TipComponentMouseListener extends MouseAdapter {
    @Override
    public void mouseEntered(MouseEvent e) {
      isOverPopup = true;
    }

    @Override
    public void mouseExited(MouseEvent e) {
      isOverPopup = false;
      if (popup != null) {
        getFocusable(validationInfo.component).ifPresent(fc -> {
          if (!fc.hasFocus()) {
            popup.cancel();
            popup = null;
          }
        });
      }
    }
  }
}
