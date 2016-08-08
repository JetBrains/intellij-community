/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.ui;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class LabeledComponent<Comp extends JComponent> extends JPanel implements PanelWithAnchor {
  private final JBLabel myLabel = new JBLabel();
  private Comp myComponent;
  private String myLabelConstraints = BorderLayout.NORTH;
  private JComponent myAnchor;

  public LabeledComponent() {
    super(new BorderLayout(UIUtil.DEFAULT_HGAP, 2));
    insertLabel();
  }

  @NotNull
  public static <Comp extends JComponent> LabeledComponent<Comp> create(@NotNull Comp component, @NotNull String text) {
    return create(component, text, BorderLayout.NORTH);
  }

  @NotNull
  public static <Comp extends JComponent> LabeledComponent<Comp> create(@NotNull Comp component, @NotNull String text, @NonNls String labelConstraint) {
    final LabeledComponent<Comp> labeledComponent = new LabeledComponent<>();
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

  public void setText(String textWithMnemonic) {
    if (!StringUtil.isEmpty(textWithMnemonic) && !StringUtil.endsWithChar(textWithMnemonic, ':')) textWithMnemonic += ":";
    TextWithMnemonic withMnemonic = TextWithMnemonic.fromTextWithMnemonic(textWithMnemonic);
    withMnemonic.setToLabel(myLabel);
  }

  public String getText() {
    String text = TextWithMnemonic.fromLabel(myLabel).getTextWithMnemonic();
    if (StringUtil.endsWithChar(text, ':')) return text.substring(0, text.length() - 1);
    return text;
  }

  public void setComponentClass(@NonNls String className) throws ClassNotFoundException, InstantiationException,
                                                                                           IllegalAccessException {
    if (className != null) {
      Class<Comp> aClass = (Class<Comp>)getClass().getClassLoader().loadClass(className);
      Comp component = aClass.newInstance();
      setComponent(component);
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
      myLabel.setLabelFor(((ComponentWithBrowseButton)myComponent).getChildComponent());
    } else myLabel.setLabelFor(myComponent);
  }

  public String getComponentClass() {
    if (myComponent == null) return null;
    return getComponent().getClass().getName();
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
    String constrains = findBorderConstrains(borderConstrains);
    if (constrains == null || constrains.equals(myLabelConstraints)) return;
    myLabelConstraints = borderConstrains;
    insertLabel();
  }

  public String getLabelLocation() {
    return myLabelConstraints;
  }

  public Insets getLabelInsets() {
    return myLabel.getInsets();
  }

  public void setLabelInsets(Insets insets) {
    if (Comparing.equal(insets, getLabelInsets())) return;
    myLabel.setBorder(IdeBorderFactory.createEmptyBorder(insets));
  }

  private static final String[] LABEL_BORDER_CONSTRAINS = new String[]{BorderLayout.NORTH, BorderLayout.EAST, BorderLayout.SOUTH, BorderLayout.WEST};

  private static String findBorderConstrains(String str) {
    for (String constrain : LABEL_BORDER_CONSTRAINS) {
      if (constrain.equals(str)) return constrain;
    }
    return null;
  }

  public String getRawText() {
    return myLabel.getText().replace("\u001B", "");
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

  public static class TextWithMnemonic {
    private final String myText;
    private final int myMnemoniIndex;

    public TextWithMnemonic(String text, int mnemoniIndex) {
      myText = text;
      myMnemoniIndex = mnemoniIndex;
    }

    public void setToLabel(JLabel label) {
      label.setText(myText);
      if (myMnemoniIndex != -1) label.setDisplayedMnemonic(myText.charAt(myMnemoniIndex));
      else label.setDisplayedMnemonic(0);
      label.setDisplayedMnemonicIndex(myMnemoniIndex);
    }

    public String getTextWithMnemonic() {
      if (myMnemoniIndex == -1) return myText;
      return myText.substring(0, myMnemoniIndex) + "&" + myText.substring(myMnemoniIndex);
    }

    public static TextWithMnemonic fromTextWithMnemonic(String textWithMnemonic) {
      int mnemonicIndex = UIUtil.getDisplayMnemonicIndex(textWithMnemonic);
      if (mnemonicIndex == -1) {
        return new TextWithMnemonic(textWithMnemonic, -1);
      }
      textWithMnemonic = textWithMnemonic.substring(0, mnemonicIndex) + textWithMnemonic.substring(mnemonicIndex + 1);
      return new TextWithMnemonic(textWithMnemonic, mnemonicIndex);
    }

    public static TextWithMnemonic fromLabel(JLabel label) {
      return new TextWithMnemonic(label.getText(), label.getDisplayedMnemonicIndex());
    }
  }
}
