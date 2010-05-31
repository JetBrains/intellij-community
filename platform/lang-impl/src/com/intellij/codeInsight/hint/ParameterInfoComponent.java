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

package com.intellij.codeInsight.hint;

import com.intellij.lang.parameterInfo.*;
import com.intellij.openapi.editor.*;
import com.intellij.psi.*;
import com.intellij.ui.*;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.*;

class ParameterInfoComponent extends JPanel{
  private final Object[] myObjects;
  private int myCurrentParameterIndex;

  private PsiElement myParameterOwner;
  private Object myHighlighted;
  private final @NotNull ParameterInfoHandler myHandler;

  private final OneElementComponent[] myPanels;

  private static final Color BACKGROUND_COLOR = HintUtil.INFORMATION_COLOR;
  private static final Color FOREGROUND_COLOR = new Color(0, 0, 0);
//  private static final Color DISABLED_BACKGROUND_COLOR = HintUtil.INFORMATION_COLOR;
  private static final Color DISABLED_FOREGROUND_COLOR = new Color(128, 128, 128);
  private static final Color HIGHLIGHTED_BORDER_COLOR = new Color(231, 254, 234);
  private final Font NORMAL_FONT;
  private final Font BOLD_FONT;

  private static final Border BOTTOM_BORDER = new SideBorder(Color.lightGray, SideBorder.BOTTOM);
  private static final Border BACKGROUND_BORDER = BorderFactory.createLineBorder(BACKGROUND_COLOR);

  protected int myWidthLimit;

  public ParameterInfoComponent(Object[] objects, Editor editor,@NotNull ParameterInfoHandler handler) {
    super(new GridBagLayout());

    JComponent editorComponent = editor.getComponent();
    JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();
    myWidthLimit = layeredPane.getWidth();

    NORMAL_FONT = UIUtil.getLabelFont();
    BOLD_FONT = NORMAL_FONT.deriveFont(Font.BOLD);

    myObjects = objects;

    setLayout(new GridBagLayout());
    setBorder(BorderFactory.createCompoundBorder(LineBorder.createGrayLineBorder(), BorderFactory.createEmptyBorder(0, 5, 0, 5)));
    setBackground(BACKGROUND_COLOR);

    myHandler = handler;
    myPanels = new OneElementComponent[myObjects.length];
    for(int i = 0; i < myObjects.length; i++) {
      myPanels[i] = new OneElementComponent();
      add(myPanels[i], new GridBagConstraints(0,i,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(0,0,0,0),0,0));
    }

    myCurrentParameterIndex = -1;
  }

  class MyParameterContext implements ParameterInfoUIContextEx {
    private int i;
    public void setupUIComponentPresentation(String text,
                                             int highlightStartOffset,
                                             int highlightEndOffset,
                                             boolean isDisabled,
                                             boolean strikeout,
                                             boolean isDisabledBeforeHighlight,
                                             Color background) {
      myPanels[i].setup(text, highlightStartOffset, highlightEndOffset, isDisabled, strikeout, isDisabledBeforeHighlight, background);
      myPanels[i].setBorder(isLastParameterOwner() ? BACKGROUND_BORDER : BOTTOM_BORDER);
    }

    public void setupUIComponentPresentation(final String[] texts, final EnumSet<Flag>[] flags, final Color background) {
      myPanels[i].setup(texts, flags, background);
      myPanels[i].setBorder(isLastParameterOwner() ? BACKGROUND_BORDER : BOTTOM_BORDER);
    }

    public boolean isUIComponentEnabled() {
      return isEnabled(i);
    }

    public void setUIComponentEnabled(boolean enabled) {
      setEnabled(i, enabled);
    }

    public boolean isLastParameterOwner() {
      return i == myPanels.length - 1;
    }

    public int getCurrentParameterIndex() {
      return myCurrentParameterIndex;
    }

    public PsiElement getParameterOwner() {
      return myParameterOwner;
    }

    public Color getDefaultParameterColor() {
      return myObjects[i].equals(myHighlighted) ? HIGHLIGHTED_BORDER_COLOR : BACKGROUND_COLOR;
    }
  }

  public void update(){
    MyParameterContext context = new MyParameterContext();

    for(int i = 0; i < myObjects.length; i++) {
      context.i = i;
      final Object o = myObjects[i];

      myHandler.updateUI(o,context);
    }

    invalidate();
    validate();
    repaint();
  }

  public Object[] getObjects() {
    return myObjects;
  }

  void setEnabled(int index, boolean enabled){
    myPanels[index].setEnabled(enabled);
  }

  boolean isEnabled(int index){
    return myPanels[index].isEnabled();
  }

  public void setCurrentParameterIndex(int currentParameterIndex) {
    myCurrentParameterIndex = currentParameterIndex;
  }

  public int getCurrentParameterIndex() {
    return myCurrentParameterIndex;
  }

  public void setParameterOwner (PsiElement element) {
    myParameterOwner = element;
  }

  public PsiElement getParameterOwner() {
    return myParameterOwner;
  }

  public void setHighlightedParameter(Object element) {
    myHighlighted = element;
  }

  private class OneElementComponent extends JPanel {
    private OneLineComponent[] myOneLineComponents;

    public OneElementComponent(){
      super(new GridBagLayout());
      myOneLineComponents = new OneLineComponent[0]; //TODO ???
    }

    public void setup(String text, int highlightStartOffset, int highlightEndOffset, boolean isDisabled, boolean strikeout, boolean isDisabledBeforeHighlight, Color background) {
      removeAll();

      String[] lines = UIUtil.splitText(text, getFontMetrics(BOLD_FONT), myWidthLimit, ',');

      myOneLineComponents = new OneLineComponent[lines.length];

      int lineOffset = 0;

      for (int i = 0; i < lines.length; i++) {
        String line = lines[i];

        myOneLineComponents[i] = new OneLineComponent();

        int startOffset = -1;
        int endOffset = -1;
        if (highlightStartOffset >= 0 && highlightEndOffset > lineOffset && highlightStartOffset < lineOffset + line.length()) {
          startOffset = Math.max(highlightStartOffset - lineOffset, 0);
          endOffset = Math.min(highlightEndOffset - lineOffset, line.length());
        }

        myOneLineComponents[i].setup(line, startOffset, endOffset, isDisabled, strikeout, background);

        if (isDisabledBeforeHighlight) {
          if (highlightStartOffset < 0 || highlightEndOffset > lineOffset) {
            myOneLineComponents[i].setDisabledBeforeHighlight();
          }
        }

        add(myOneLineComponents[i], new GridBagConstraints(0,i,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(0,0,0,0),0,0));

        lineOffset += line.length();
      }
    }

    public void setup(final String[] texts, final EnumSet<ParameterInfoUIContextEx.Flag>[] flags, final Color background) {
      removeAll();

      myOneLineComponents = new OneLineComponent[texts.length];
      for (int i = 0; i < texts.length; i++) {
        String line = texts[i];
        final EnumSet<ParameterInfoUIContextEx.Flag> flag = flags[i];
        myOneLineComponents[i] = new OneLineComponent();
        boolean highlighed = flag.contains(ParameterInfoUIContextEx.Flag.HIGHLIGHT);
        myOneLineComponents[i].setup(
          line, 0, highlighed ?  line.length() : 0,
          flag.contains(ParameterInfoUIContextEx.Flag.DISABLE), flags[i].contains(ParameterInfoUIContextEx.Flag.STRIKEOUT),
          background
        );
        add(myOneLineComponents[i], new GridBagConstraints(i,0,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(0,0,0,0),0,0));
      }
    }

    public void setDisabled(){
      for (OneLineComponent oneLineComponent : myOneLineComponents) {
        oneLineComponent.setDisabled();
      }
    }
  }

  private class OneLineComponent extends JPanel {
    StrikeoutLabel myLabel1 = new StrikeoutLabel("", SwingConstants.LEFT);
    StrikeoutLabel myLabel2 = new StrikeoutLabel("", SwingConstants.LEFT);
    StrikeoutLabel myLabel3 = new StrikeoutLabel("", SwingConstants.LEFT);

    private OneLineComponent(){
      super(new GridBagLayout());

      myLabel1.setOpaque(true);
      myLabel1.setFont(NORMAL_FONT);

      myLabel2.setOpaque(true);
      myLabel2.setFont(BOLD_FONT);

      myLabel3.setOpaque(true);
      myLabel3.setFont(NORMAL_FONT);

      add(myLabel1, new GridBagConstraints(0,0,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.NONE,new Insets(0,0,0,0),0,0));
      add(myLabel2, new GridBagConstraints(1,0,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.NONE,new Insets(0,0,0,0),0,0));
      add(myLabel3, new GridBagConstraints(2,0,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(0,0,0,0),0,0));
    }

    private void setup(String text, int highlightStartOffset, int highlightEndOffset, boolean isDisabled, boolean strikeout, Color background) {
      myLabel1.setBackground(background);
      myLabel2.setBackground(background);
      myLabel3.setBackground(background);
      setBackground(background);

      myLabel1.setStrikeout(strikeout);
      myLabel2.setStrikeout(strikeout);
      myLabel3.setStrikeout(strikeout);

      if (isDisabled) {
        myLabel1.setText(text);
        myLabel2.setText("");
        myLabel3.setText("");

        setDisabled();
      }
      else {
        myLabel1.setForeground(FOREGROUND_COLOR);
        myLabel2.setForeground(FOREGROUND_COLOR);
        myLabel3.setForeground(FOREGROUND_COLOR);

        if (highlightStartOffset < 0) {
          myLabel1.setText(text);
          myLabel2.setText("");
          myLabel3.setText("");
        }
        else {
          myLabel1.setText(text.substring(0, highlightStartOffset));
          myLabel2.setText(text.substring(highlightStartOffset, highlightEndOffset));
          myLabel3.setText(text.substring(highlightEndOffset));
        }
      }
    }

    private void setDisabled(){
      myLabel1.setForeground(DISABLED_FOREGROUND_COLOR);
      myLabel2.setForeground(DISABLED_FOREGROUND_COLOR);
      myLabel3.setForeground(DISABLED_FOREGROUND_COLOR);
    }

    private void setDisabledBeforeHighlight(){
      myLabel1.setForeground(DISABLED_FOREGROUND_COLOR);
    }

    public Dimension getPreferredSize(){
      myLabel1.setFont(BOLD_FONT);
      myLabel3.setFont(BOLD_FONT);
      Dimension boldPreferredSize = super.getPreferredSize();
      myLabel1.setFont(NORMAL_FONT);
      myLabel3.setFont(NORMAL_FONT);
      Dimension normalPreferredSize = super.getPreferredSize();

      // some fonts (for example, Arial Black Cursiva) have NORMAL characters wider than BOLD characters
      return new Dimension(Math.max(boldPreferredSize.width, normalPreferredSize.width), Math.max(boldPreferredSize.height, normalPreferredSize.height));
    }
  }
}
