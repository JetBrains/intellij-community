// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.newItemPopup;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.TextComponentEmptyText;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Consumer;
import com.intellij.util.SlowOperations;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.Predicate;

public class NewItemSimplePopupPanel extends JBPanel implements Disposable {
  protected final ExtendableTextField myTextField;

  protected JBPopup myErrorPopup;
  protected RelativePoint myErrorShowPoint;

  protected Consumer<? super InputEvent> myApplyAction;

  public NewItemSimplePopupPanel() {
    this(false);
  }

  public NewItemSimplePopupPanel(boolean liveValidation) {
    super(new BorderLayout());

    myTextField = createTextField(liveValidation);
    add(myTextField, BorderLayout.NORTH);
    setBottomSpace(true);
    if (ExperimentalUI.isNewUI()) {
      setBackground(JBUI.CurrentTheme.NewClassDialog.panelBackground());
    }

    myErrorShowPoint = new RelativePoint(myTextField, new Point(0, myTextField.getHeight()));
  }

  public void setApplyAction(@NotNull Consumer<? super InputEvent> applyAction) {
    myApplyAction = e -> {
      try (AccessToken ignore = SlowOperations.startSection(SlowOperations.ACTION_PERFORM)) {
        applyAction.consume(e);
      }
    };
  }

  public @NotNull Consumer<? super InputEvent> getApplyAction() {
    return myApplyAction;
  }

  public void setError(@NlsContexts.DialogMessage String error) {
    setMessage(error, false);
  }

  public void setWarning(@NlsContexts.DialogMessage String warning) {
    setMessage(warning, true);
  }

  @ApiStatus.Internal
  protected void setBottomSpace(boolean spaceNeeded) {
    if (ExperimentalUI.isNewUI()) {
      setBorder(spaceNeeded ? JBUI.Borders.emptyBottom(JBUI.CurrentTheme.Popup.bodyBottomInsetNoAd()) : null);
    }
  }

  private void setMessage(@NlsContexts.DialogMessage String message, boolean isWarning) {
    myTextField.putClientProperty("JComponent.outline", message != null ? (isWarning ? "warning" : "error") : null);

    if (myErrorPopup != null && !myErrorPopup.isDisposed()) Disposer.dispose(myErrorPopup);
    if (message == null) return;

    ValidationInfo validationInfo = new ValidationInfo(message, myTextField);
    if (isWarning) {
      validationInfo.asWarning();
    }
    ComponentPopupBuilder popupBuilder = ComponentValidator.createPopupBuilder(validationInfo, errorHint -> {
      Insets insets = myTextField.getInsets();
      Dimension hintSize = errorHint.getPreferredSize();
      Point point = new Point(0, insets.top - JBUIScale.scale(6) - hintSize.height);
      myErrorShowPoint = new RelativePoint(myTextField, point);
    }).setCancelOnWindowDeactivation(false)
      .setCancelOnClickOutside(true);

    myErrorPopup = popupBuilder.createPopup();
    myErrorPopup.show(myErrorShowPoint);
  }

  @Override
  public void dispose() {
    if (myErrorPopup != null && !myErrorPopup.isDisposed()) Disposer.dispose(myErrorPopup);
  }

  public JTextField getTextField() {
    return myTextField;
  }

  private @NotNull ExtendableTextField createTextField(boolean liveErrorValidation) {
    ExtendableTextField res = new ExtendableTextField();

    int textFieldHeight = ExperimentalUI.isNewUI() ? 32 : 28;
    Dimension minSize = res.getMinimumSize();
    Dimension prefSize = res.getPreferredSize();
    minSize.height = JBUIScale.scale(textFieldHeight);
    prefSize.height = JBUIScale.scale(textFieldHeight);
    res.setMinimumSize(minSize);
    res.setPreferredSize(prefSize);
    res.setColumns(30);

    Border errorBorder = new ErrorBorder(res.getBorder());
    if (ExperimentalUI.isNewUI()) {
      res.setBorder(JBUI.Borders.compound(errorBorder, JBUI.Borders.emptyLeft(13)));
    } else {
      Border border = JBUI.Borders.customLine(JBUI.CurrentTheme.NewClassDialog.bordersColor(), 1, 0, 0, 0);
      res.setBorder(JBUI.Borders.compound(border, errorBorder));
    }
    res.setBackground(JBUI.CurrentTheme.NewClassDialog.searchFieldBackground());

    res.putClientProperty(TextComponentEmptyText.STATUS_VISIBLE_FUNCTION, (Predicate<JBTextField>)field -> field.getText().isEmpty());
    res.getEmptyText().setText(IdeBundle.message("action.create.new.class.name.field"));
    res.getAccessibleContext().setAccessibleName(IdeBundle.message("action.create.new.class.name.field"));
    res.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        performApplyActionOnEnter(e);
      }
    });

    res.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        if (!liveErrorValidation) setError(null);
      }
    });

    return res;
  }

  protected void performApplyActionOnEnter(KeyEvent e) {
    if (e.getKeyCode() == KeyEvent.VK_ENTER) {
      if (myApplyAction == null) return;
      myApplyAction.consume(e);
    }
  }

  public boolean hasError() {
    return myErrorPopup != null;
  }

  private static final class ErrorBorder implements Border {
    private final Border errorDelegateBorder;

    private ErrorBorder(Border delegate) {errorDelegateBorder = delegate;}

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      if (checkError(c)) {
        errorDelegateBorder.paintBorder(c, g, x, y, width, height);
      }
    }

    @Override
    public Insets getBorderInsets(Component c) {
      return checkError(c) ? errorDelegateBorder.getBorderInsets(c) : JBInsets.emptyInsets();
    }

    @Override
    public boolean isBorderOpaque() {
      return false;
    }

    private static boolean checkError(Component c) {
      DarculaUIUtil.Outline outline = DarculaUIUtil.getOutline((JComponent)c);
      return DarculaUIUtil.isWarningOrError(outline);
    }
  }
}
