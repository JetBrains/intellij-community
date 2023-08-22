// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.BundleBase;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public class LabeledComponent<Comp extends JComponent> extends JPanel implements PanelWithAnchor {
  private static final @NonNls String[] LABEL_BORDER_CONSTRAINS = {BorderLayout.NORTH, BorderLayout.EAST, BorderLayout.SOUTH, BorderLayout.WEST};

  private final JBLabel myLabel = new JBLabel();
  private Comp myComponent;
  private @NonNls String myLabelConstraints = BorderLayout.NORTH;
  private JComponent myAnchor;

  public LabeledComponent() {
    super(new BorderLayout(UIUtil.DEFAULT_HGAP, 2));
    insertLabel();
  }

  @NotNull
  public static <Comp extends JComponent> LabeledComponent<Comp> create(@NotNull Comp component, @NotNull @NlsContexts.Label String text) {
    return create(component, text, BorderLayout.NORTH);
  }

  @NotNull
  public static <Comp extends JComponent> LabeledComponent<Comp> create(@NotNull Comp component,
                                                                        @NotNull @NlsContexts.Label String text,
                                                                        @NonNls String labelConstraint) {
    LabeledComponent<Comp> labeledComponent = new LabeledComponent<>();
    labeledComponent.setComponent(component);
    labeledComponent.setText(text);
    labeledComponent.setLabelLocation(labelConstraint);
    return labeledComponent;
  }

  private void insertLabel() {
    remove(myLabel);
    add(myLabel, myLabelConstraints);
    setAnchor(myLabel);
  }

  public void setText(@NlsContexts.Label String text) {
    text = handleSemicolon(text);
    TextWithMnemonic.fromTextWithMnemonic(text).setToLabel(myLabel);
  }

  private static @NlsContexts.Label String handleSemicolon(@NlsContexts.Label String text) {
    return StringUtil.isEmpty(text) || StringUtil.endsWithChar(text, ':') || StringUtil.endsWithChar(text, '：') ? text : text + ':';
  }

  public String getText() {
    String text = TextWithMnemonic.fromLabel(myLabel).getTextWithMnemonic();
    return StringUtil.endsWithChar(text, ':') ? text.substring(0, text.length() - 1) : text;
  }

  public void setComponentClass(@NonNls String className) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
    if (className != null) {
      @SuppressWarnings("unchecked") Class<Comp> aClass = (Class<Comp>)getClass().getClassLoader().loadClass(className);
      setComponent(aClass.newInstance());
    }
    else {
      setComponent(null);
    }
  }

  public void setComponent(Comp component) {
    if (myComponent != null) remove(myComponent);
    myComponent = component;
    if (myComponent != null) {
      add(myComponent, BorderLayout.CENTER);
    }
    if (myComponent instanceof ComponentWithBrowseButton && !(myComponent instanceof TextFieldWithBrowseButton)) {
      myLabel.setLabelFor(((ComponentWithBrowseButton<?>)myComponent).getChildComponent());
    }
    else {
      myLabel.setLabelFor(myComponent);
    }
  }

  @NonNls
  public String getComponentClass() {
    return myComponent == null ? null : getComponent().getClass().getName();
  }

  public Comp getComponent() {
    return myComponent;
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    if (myComponent != null) myComponent.setEnabled(enabled);
    myLabel.setEnabled(enabled);
  }

  public void setLabelLocation(@NonNls String borderConstrains) {
    if (ArrayUtil.indexOf(LABEL_BORDER_CONSTRAINS, borderConstrains) >= 0 && !borderConstrains.equals(myLabelConstraints)) {
      myLabelConstraints = borderConstrains;
      insertLabel();
    }
  }

  public @NonNls String getLabelLocation() {
    return myLabelConstraints;
  }

  public Insets getLabelInsets() {
    return myLabel.getInsets();
  }

  @SuppressWarnings("unused")
  public void setLabelInsets(Insets insets) {
    if (!Objects.equals(insets, getLabelInsets())) {
      myLabel.setBorder(IdeBorderFactory.createEmptyBorder(insets));
    }
  }

  public String getRawText() {
    return myLabel.getText().replace(BundleBase.MNEMONIC_STRING, "");
  }

  public JBLabel getLabel() {
    return myLabel;
  }

  @Override
  public JComponent getAnchor() {
    return myAnchor;
  }

  @Override
  public void setAnchor(@Nullable JComponent labelAnchor) {
    myAnchor = labelAnchor;
    myLabel.setAnchor(labelAnchor);
  }

  @Override
  public @Nullable JComponent getOwnAnchor() {
    return myLabel;
  }

  public static class TextWithMnemonic {
    private final @NlsContexts.Label String myText;
    private final int myMnemonicIndex;

    public TextWithMnemonic(@NlsContexts.Label String text, int mnemonicIndex) {
      myText = text;
      myMnemonicIndex = mnemonicIndex;
    }

    public void setToLabel(JLabel label) {
      label.setText(myText);
      if (myMnemonicIndex != -1) label.setDisplayedMnemonic(myText.charAt(myMnemonicIndex));
      else label.setDisplayedMnemonic(0);
      label.setDisplayedMnemonicIndex(myMnemonicIndex);
    }

    public String getTextWithMnemonic() {
      return myMnemonicIndex != -1 ? myText.substring(0, myMnemonicIndex) + '&' + myText.substring(myMnemonicIndex) : myText;
    }

    public static TextWithMnemonic fromTextWithMnemonic(String text) {
      int mnemonicIndex = UIUtil.getDisplayMnemonicIndex(text);
      return mnemonicIndex != -1
             ? new TextWithMnemonic(text.substring(0, mnemonicIndex) + text.substring(mnemonicIndex + 1), mnemonicIndex)
             : new TextWithMnemonic(text, -1);
    }

    public static TextWithMnemonic fromLabel(JLabel label) {
      return new TextWithMnemonic(label.getText(), label.getDisplayedMnemonicIndex());
    }
  }
}