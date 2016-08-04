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

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorSchemeAttributeDescriptor;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ColorPanel;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;


public class RainbowDescriptionPanel extends JPanel implements OptionsPanelImpl.ColorDescriptionPanel {
  private final EventDispatcher<Listener> myDispatcher = EventDispatcher.create(Listener.class);

  protected JPanel myPanel;

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

  private JTextPane myInheritanceLabel;

  private JBCheckBox myInheritAttributesBox;
  private JBCheckBox myRainbow;

  ColorAndFontGlobalState myGlobalState;

  public RainbowDescriptionPanel(ColorAndFontGlobalState globalState) {
    super(new BorderLayout());
    myGlobalState = globalState;
    add(myPanel, BorderLayout.CENTER);

    setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 4));
    ActionListener actionListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        onSettingsChanged(e);
      }
    };
    for (JBCheckBox c : new JBCheckBox[]{myRainbow, myCbStop1, myCbStop2, myCbStop3, myCbStop4, myCbStop5, myInheritAttributesBox}) {
      c.addActionListener(actionListener);
    }
    for (ColorPanel c : new ColorPanel[]{myStop1, myStop2, myStop3, myStop4, myStop5}) {
      c.addActionListener(actionListener);
    }

    Messages.configureMessagePaneUi(myInheritanceLabel, "<html>", null);
    myInheritanceLabel.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        onHyperLinkClicked(e);
      }
    });
    myInheritanceLabel.setBorder(BorderFactory.createEmptyBorder());
  }

  @NotNull
  @Override
  public JComponent getPanel() {
    return this;
  }

  private void onHyperLinkClicked(HyperlinkEvent e) {
    myDispatcher.getMulticaster().onHyperLinkClicked(e);
  }

  private void onSettingsChanged(ActionEvent e) {
    myDispatcher.getMulticaster().onSettingsChanged(e);
  }

  @Override
  public void resetDefault() {
  }

  @Override
  public void reset(@NotNull EditorSchemeAttributeDescriptor attributeDescriptor) {
    if (!(attributeDescriptor instanceof RainbowAttributeDescriptor)) return;
    RainbowAttributeDescriptor descriptor = (RainbowAttributeDescriptor)attributeDescriptor;

    List<Pair<Boolean, Color>> rainbowCurState = descriptor.getRainbowCurState();
    if (rainbowCurState.size() < myCbStops.length) return;

    myRainbow.setSelected(myGlobalState.isRainbowOn);

    boolean isEnable = !ColorAndFontOptions.isReadOnly(attributeDescriptor.getScheme()) && myGlobalState.isRainbowOn;
    for (int i = 0; i < myCbStops.length; ++i) {
      Pair<Boolean, Color> state = rainbowCurState.get(i);
      myCbStops[i].setEnabled(isEnable);

      boolean isOverride = state.first;
      myCbStops[i].setSelected(isOverride);

      myStops[i].setEditable(isEnable && isOverride);
      myStops[i].setSelectedColor(state.second);
    }
  }

  @Override
  public void apply(@NotNull EditorSchemeAttributeDescriptor attributeDescriptor, EditorColorsScheme scheme) {
    if (!(attributeDescriptor instanceof RainbowAttributeDescriptor)) return;
    RainbowAttributeDescriptor descriptor = (RainbowAttributeDescriptor)attributeDescriptor;

    List<Pair<Boolean, Color>> rainbowCurState = descriptor.getRainbowCurState();
    if (rainbowCurState.size() < myCbStops.length) return;

    myGlobalState.isRainbowOn = myRainbow.isSelected();
    for (int i = 0; i < myCbStops.length; ++i) {
      boolean isOverride = myCbStops[i].isSelected();
      rainbowCurState.set(i, Pair.create(isOverride,
                                         isOverride ? myStops[i].getSelectedColor() : descriptor.getDefaultColor(i)));
    }

    reset(descriptor);
    descriptor.apply(scheme);
  }

  @Override
  public void addListener(@NotNull Listener listener) {
    myDispatcher.addListener(listener);
  }
}
