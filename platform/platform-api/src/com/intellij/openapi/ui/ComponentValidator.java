// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.execution.ui.TagButton;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.EditorTextComponent;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Alarm;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.swing.SwingUtilities2;

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
import java.util.Objects;
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
  public static final Function<JComponent, JComponent> CWBB_PROVIDER = c -> ((ComponentWithBrowseButton<?>)c).getChildComponent();

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
  private JEditorPane tipComponent;

  public ComponentValidator(@NotNull Disposable parentDisposable) {
    this.parentDisposable = parentDisposable;
  }

  /**
   * @deprecated Use {@link ComponentValidator#withValidator(Supplier)} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
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

      reset();
    });
    return this;
  }

  /**
   * Convenient wrapper for mostly used scenario.
   */
  public ComponentValidator andRegisterOnDocumentListener(@NotNull JTextComponent textComponent) {
    DocumentAdapter listener = new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        getInstance(textComponent).ifPresent(ComponentValidator::revalidate); // Don't use 'this' to avoid cyclic references.
      }
    };
    textComponent.getDocument().addDocumentListener(listener);
    Disposer.register(parentDisposable, () -> {
      textComponent.getDocument().removeDocumentListener(listener);
    });
    return this;
  }

  public <T extends JComponent & EditorTextComponent> ComponentValidator andRegisterOnDocumentListener(@NotNull T textComponent) {
    textComponent.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(com.intellij.openapi.editor.event.@NotNull DocumentEvent event) {
        getInstance(textComponent).ifPresent(ComponentValidator::revalidate); // Don't use 'this' to avoid cyclic references.
      }
    }, parentDisposable);
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
    tipComponent = null;
    popupLocation = null;
    popupSize = null;
    validationInfo = null;
  }

  @Nullable
  public ValidationInfo getValidationInfo() {
    return validationInfo;
  }

  private boolean needResetForInfo(@Nullable ValidationInfo info) {
    if (info == null && validationInfo != null) return true;
    else if (info != null && validationInfo != null) {
      if (info.warning != validationInfo.warning) return true;
      if (tipComponent != null && !Objects.equals(info.message, validationInfo.message)) {
        String message = HtmlChunk.div().attr("width", MAX_WIDTH.get()).addRaw(trimMessage(info.message, tipComponent)).
                           wrapWith(HtmlChunk.html()).toString();
        View v = BasicHTML.createHTMLView(tipComponent, message);

        Dimension size = tipComponent.getPreferredSize();
        JBInsets.removeFrom(size, tipComponent.getInsets());

        if (v.getPreferredSpan(View.Y_AXIS) != size.height) return true;
      }
    }
    return false;
  }

  public void updateInfo(@Nullable ValidationInfo info) {
    if (disableValidation) return;

    boolean hasNewInfo = info != null && !info.equals(validationInfo);
    if (needResetForInfo(info)) {
      reset();
    }

    if (hasNewInfo) {
      validationInfo = info;

      if (popup != null && popup.isVisible() && tipComponent != null) {
        popup.pack(true, convertMessage(info.message, tipComponent));
      }
      else {
        JComponent component = validationInfo.component;
        if (component != null) {
          outlineProvider.apply(component).putClientProperty("JComponent.outline", validationInfo.warning ? "warning" : "error");

          component.revalidate();
          component.repaint();
        }

        if (!StringUtil.isEmptyOrSpaces(info.message)) {
          // create popup if there is something to show to user
          popupBuilder = createPopupBuilder(validationInfo, editorPane -> {
            tipComponent = editorPane;
            editorPane.addHyperlinkListener(hyperlinkListener);
            editorPane.addMouseListener(new TipComponentMouseListener());
            popupSize = editorPane.getPreferredSize();
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
  private static ComponentPopupBuilder createPopupBuilder(boolean isWarning, @Nullable Consumer<? super JEditorPane> configurator) {
    JEditorPane tipComponent = new JEditorPane();
    tipComponent.setContentType("text/html");
    tipComponent.setEditable(false);
    tipComponent.setEditorKit(HTMLEditorKitBuilder.simple());

    EditorKit kit = tipComponent.getEditorKit();
    if (kit instanceof HTMLEditorKit) {
      StyleSheet css = ((HTMLEditorKit)kit).getStyleSheet();

      css.addRule("a, a:link {color:#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.ENABLED) + ";}");
      css.addRule("a:visited {color:#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.VISITED) + ";}");
      css.addRule("a:hover {color:#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.HOVERED) + ";}");
      css.addRule("a:active {color:#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.PRESSED) + ";}");
      css.addRule("body {background-color:#" + ColorUtil.toHex(isWarning ? warningBackgroundColor() : errorBackgroundColor()) + ";}");
    }

    if (tipComponent.getCaret() instanceof DefaultCaret) {
      ((DefaultCaret)tipComponent.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
    }

    tipComponent.setCaretPosition(0);

    tipComponent.setBackground(isWarning ? warningBackgroundColor() : errorBackgroundColor());
    tipComponent.setOpaque(true);
    tipComponent.setBorder(getBorder());

    if (configurator != null) {
      configurator.accept(tipComponent);
    }

    return JBPopupFactory.getInstance().createComponentPopupBuilder(tipComponent, null).
      setBorderColor(isWarning ? warningBorderColor() : errorBorderColor()).
      setCancelOnClickOutside(false).
      setShowShadow(true);
  }

  /**
   * @return true if message is multiline.
   */
  @NlsSafe
  private static boolean convertMessage(@Nls String message, @NotNull JEditorPane component) {
    View v = BasicHTML.createHTMLView(component, String.format("<html>%s</html>", message));
    boolean widerText = v.getPreferredSpan(View.X_AXIS) > MAX_WIDTH.get();
    HtmlChunk.Element div =  widerText ?
                             HtmlChunk.div().attr("width", MAX_WIDTH.get()).addRaw(trimMessage(message, component))
                             : HtmlChunk.div().addRaw(message);
    component.setText(div.wrapWith("body").wrapWith("html").toString());
    return widerText;
  }

  @NotNull
  public static ComponentPopupBuilder createPopupBuilder(@NotNull ValidationInfo info, @Nullable Consumer<? super JEditorPane> configurator) {
    return createPopupBuilder(info.warning, tipComponent -> {
      convertMessage(info.message, tipComponent);
      if (configurator != null) {
        configurator.accept(tipComponent);
      }
    });
  }

  private static @Nls String trimMessage(@Nls String message, JComponent c) {
    String[] words = message.split("\\s+");
    @Nls StringBuilder result = new StringBuilder();

    for(String word : words) {
      word = SwingUtilities2.clipStringIfNecessary(c, c.getFontMetrics(c.getFont()), word, MAX_WIDTH.get());
      result.append(word).append(" ");
    }

    return result.toString();
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
    return (source instanceof JComboBox && !((JComboBox<?>)source).isEditable() ||
            source instanceof JCheckBox ||
            source instanceof JRadioButton ||
            source instanceof TagButton) ?
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

      if (focusValidator != null) {
        updateInfo(focusValidator.get());
      }

      if (disableValidation) {
        enableValidation();
        revalidate();
      }
    }
  }

  public void enableValidation() {
    disableValidation = false;
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
