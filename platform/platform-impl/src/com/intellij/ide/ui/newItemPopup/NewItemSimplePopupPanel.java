// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.newItemPopup;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.BooleanFunction;
import com.intellij.util.Consumer;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class NewItemSimplePopupPanel extends JBPanel implements Disposable {
  protected final ExtendableTextField myTextField;

  private JBPopup myErrorPopup;
  protected RelativePoint myErrorShowPoint;

  protected Consumer<? super InputEvent> myApplyAction;

  public NewItemSimplePopupPanel() {
    this(false);
  }

  public NewItemSimplePopupPanel(boolean liveValidation) {
    super(new BorderLayout());

    myTextField = createTextField(liveValidation);
    add(myTextField, BorderLayout.NORTH);

    myErrorShowPoint = new RelativePoint(myTextField, new Point(0, myTextField.getHeight()));
  }

  public void setApplyAction(@NotNull Consumer<? super InputEvent> applyAction) {
    myApplyAction = applyAction;
  }

  public @NotNull Consumer<? super InputEvent> getApplyAction() {
    return myApplyAction;
  }

  public void setError(@NlsContexts.DialogMessage String error) {
    myTextField.putClientProperty("JComponent.outline", error != null ? "error" : null);

    if (myErrorPopup != null && !myErrorPopup.isDisposed()) Disposer.dispose(myErrorPopup);
    if (error == null) return;

    ComponentPopupBuilder popupBuilder = ComponentValidator.createPopupBuilder(new ValidationInfo(error, myTextField), errorHint -> {
      Insets insets = myTextField.getInsets();
      Dimension hintSize = errorHint.getPreferredSize();
      Point point = new Point(0, insets.top - JBUIScale.scale(6) - hintSize.height);
      myErrorShowPoint = new RelativePoint(myTextField, point);
    }).setCancelOnWindowDeactivation(false)
      .setCancelOnClickOutside(true)
      .addUserData("SIMPLE_WINDOW");

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

  @NotNull
  protected ExtendableTextField createTextField(boolean liveErrorValidation) {
    ExtendableTextField res = new ExtendableTextField();

    Dimension minSize = res.getMinimumSize();
    Dimension prefSize = res.getPreferredSize();
    minSize.height = JBUIScale.scale(28);
    prefSize.height = JBUIScale.scale(28);
    res.setMinimumSize(minSize);
    res.setPreferredSize(prefSize);
    res.setColumns(30);

    Border border = JBUI.Borders.customLine(JBUI.CurrentTheme.NewClassDialog.bordersColor(), 1, 0, 0, 0);
    Border errorBorder = new ErrorBorder(res.getBorder());
    res.setBorder(JBUI.Borders.merge(border, errorBorder, false));
    res.setBackground(JBUI.CurrentTheme.NewClassDialog.searchFieldBackground());

    res.putClientProperty("StatusVisibleFunction", (BooleanFunction<JBTextField>)field -> field.getText().isEmpty());
    res.getEmptyText().setText(IdeBundle.message("action.create.new.class.name.field"));
    res.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          if (myApplyAction != null) myApplyAction.consume(e);
        }
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
      Object outlineObj = ((JComponent)c).getClientProperty("JComponent.outline");
      if (outlineObj == null) return false;

      DarculaUIUtil.Outline outline = outlineObj instanceof DarculaUIUtil.Outline
                                      ? (DarculaUIUtil.Outline) outlineObj : DarculaUIUtil.Outline.valueOf(outlineObj.toString());
      return outline == DarculaUIUtil.Outline.error || outline == DarculaUIUtil.Outline.warning;
    }
  }
}
