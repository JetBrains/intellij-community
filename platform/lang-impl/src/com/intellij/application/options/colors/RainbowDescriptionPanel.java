/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.application.options.colors;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorSchemeAttributeDescriptor;
import com.intellij.openapi.editor.colors.EditorSchemeAttributeDescriptorWithPath;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ColorPanel;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.EventDispatcher;
import com.intellij.util.FontUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;


public class RainbowDescriptionPanel extends JPanel implements OptionsPanelImpl.ColorDescriptionPanel {
  private final EventDispatcher<Listener> myDispatcher = EventDispatcher.create(Listener.class);

  protected JPanel myPanel;

  private JTextPane myGradientLabel;
  private JBCheckBox myCbStop1;
  private JBCheckBox myCbStop2;
  private JBCheckBox myCbStop3;
  private JBCheckBox myCbStop4;
  private JBCheckBox myCbStop5;
  private JBCheckBox[] myCbStops = new JBCheckBox[]{myCbStop1, myCbStop2, myCbStop3, myCbStop4, myCbStop5};

  protected ColorPanel myStop1;
  protected ColorPanel myStop2;
  protected ColorPanel myStop3;
  protected ColorPanel myStop4;
  protected ColorPanel myStop5;
  private ColorPanel[] myStops = new ColorPanel[]{myStop1, myStop2, myStop3, myStop4, myStop5};

  private JBCheckBox myRainbow;
  private JTextPane myInheritanceLabel;
  private JBCheckBox myInheritAttributesBox;

  private final String myInheritedMessage;
  private final String myOverrideMessage;
  private final String myInheritedMessageTooltip;

  public RainbowDescriptionPanel() {
    super(new BorderLayout());
    add(myPanel, BorderLayout.CENTER);

    setBorder(JBUI.Borders.empty(4, 0, 4, 4));

    ActionListener actionListener = e -> myDispatcher.getMulticaster().onSettingsChanged(e);
    for (JBCheckBox c : new JBCheckBox[]{myRainbow, myCbStop1, myCbStop2, myCbStop3, myCbStop4, myCbStop5, myInheritAttributesBox}) {
      c.addActionListener(actionListener);
    }
    for (ColorPanel c : new ColorPanel[]{myStop1, myStop2, myStop3, myStop4, myStop5}) {
      c.addActionListener(actionListener);
    }

    String languageDefaultPageID = OptionsBundle.message("options.language.defaults.display.name");
    String rainbowOptionsID = ApplicationBundle.message("rainbow.option.panel.display.name");
    myInheritedMessage = ApplicationBundle.message("label.inherited.gradient",
                                                   rainbowOptionsID,
                                                   languageDefaultPageID);

    myInheritedMessageTooltip = checkRightArrow(ApplicationBundle.message("label.inherited.gradient.tooltip",
                                                                          rainbowOptionsID,
                                                                          languageDefaultPageID));

    myOverrideMessage = ApplicationBundle.message("label.override.gradient");
    HyperlinkListener listener = e -> myDispatcher.getMulticaster().onHyperLinkClicked(e);

    Messages.configureMessagePaneUi(myGradientLabel, myOverrideMessage, null);
    myGradientLabel.addHyperlinkListener(listener);

    Messages.configureMessagePaneUi(myInheritanceLabel,
                                    checkRightArrow(ApplicationBundle.message("label.rainbow.inheritance",
                                                                              rainbowOptionsID,
                                                                              rainbowOptionsID,
                                                                              languageDefaultPageID)),
                                    null);
    myInheritanceLabel.setToolTipText(checkRightArrow(ApplicationBundle.message("label.rainbow.inheritance.tooltip",
                                                                                rainbowOptionsID,
                                                                                languageDefaultPageID)));
    myInheritanceLabel.addHyperlinkListener(listener);
    myInheritanceLabel.setBorder(JBUI.Borders.empty(4, 0, 4, 4));
  }

  @NotNull
  private static String checkRightArrow(@NotNull String str) {
    return str.replaceAll("->", FontUtil.rightArrow(UIUtil.getLabelFont()));
  }

  @NotNull
  @Override
  public JComponent getPanel() {
    return this;
  }

  @Override
  public void resetDefault() {
  }

  @Override
  public void reset(@NotNull EditorSchemeAttributeDescriptor attributeDescriptor) {
    if (!(attributeDescriptor instanceof RainbowAttributeDescriptor)) return;
    RainbowAttributeDescriptor descriptor = (RainbowAttributeDescriptor)attributeDescriptor;

    List<Pair<Boolean, Color>> rainbowState = descriptor.getRainbowColorsInSchemaState();
    if (rainbowState.size() < myCbStops.length) return;
    Boolean rainbowOn = descriptor.getColorAndFontGlobalState().isRainbowOn(descriptor.getLanguage());
    boolean isInherited = false;
    if (rainbowOn == null) {
      isInherited = true;
      rainbowOn = descriptor.getColorAndFontGlobalState().isRainbowOn(null);
    }
    myRainbow.setEnabled(!isInherited);
    myRainbow.setSelected(rainbowOn);

    // the colors are editable only for default language
    boolean isDefaultLanguage = descriptor.getLanguage() == null;
    boolean isEnable = !ColorAndFontOptions.isReadOnly(attributeDescriptor.getScheme()) && isDefaultLanguage;
    //myGradientLabel.setEnabled(isEnable);
    for (int i = 0; i < myCbStops.length; ++i) {
      Pair<Boolean, Color> state = rainbowState.get(i);
      myCbStops[i].setEnabled(isEnable);

      boolean isOverride = state.first;
      myCbStops[i].setSelected(isOverride);

      myStops[i].setEditable(isEnable && isOverride);
      myStops[i].setSelectedColor(state.second);
    }

    myInheritanceLabel.setVisible(!isDefaultLanguage);
    myInheritAttributesBox.setSelected(isInherited);
    myInheritAttributesBox.setVisible(!isDefaultLanguage);
    myGradientLabel.setText(isDefaultLanguage ? myOverrideMessage : myInheritedMessage);
    myGradientLabel.setToolTipText(isDefaultLanguage ? null : myInheritedMessageTooltip);
  }

  @Override
  public void apply(@NotNull EditorSchemeAttributeDescriptor attributeDescriptor, EditorColorsScheme scheme) {
    if (!(attributeDescriptor instanceof RainbowAttributeDescriptor)) return;
    RainbowAttributeDescriptor descriptor = (RainbowAttributeDescriptor)attributeDescriptor;

    List<Pair<Boolean, Color>> rainbowCurState = descriptor.getRainbowColorsInSchemaState();
    if (rainbowCurState.size() < myCbStops.length) return;

    boolean isDefaultLanguage = descriptor.getLanguage() == null;
    descriptor
      .getColorAndFontGlobalState()
      .setRainbowOn(descriptor.getLanguage(),
                    isDefaultLanguage ? Boolean.valueOf(myRainbow.isSelected())
                                      : myInheritAttributesBox.isSelected() ? null
                                                                            : Boolean.valueOf(myRainbow.isSelected()));
    for (int i = 0; i < myCbStops.length; ++i) {
      boolean isOverride = myCbStops[i].isSelected();
      rainbowCurState.set(i, Pair.create(isOverride,
                                         isOverride ? myStops[i].getSelectedColor() : descriptor.getDefaultColor(i)));
    }

    descriptor.apply(scheme);
  }

  @Override
  public void addListener(@NotNull Listener listener) {
    myDispatcher.addListener(listener);
  }
}
