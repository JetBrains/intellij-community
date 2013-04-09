/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.google.common.collect.ImmutableMap;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.lang.parameterInfo.ParameterInfoUIContextEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Map;
import java.util.TreeMap;

class ParameterInfoComponent extends JPanel {
  private final Object[] myObjects;
  private int myCurrentParameterIndex;

  private PsiElement myParameterOwner;
  private Object myHighlighted;
  @NotNull private final ParameterInfoHandler myHandler;

  private final OneElementComponent[] myPanels;

  private static final Color BACKGROUND_COLOR = HintUtil.INFORMATION_COLOR;
  private static final Color FOREGROUND_COLOR = JBColor.foreground();
  private static final Color HIGHLIGHTED_BORDER_COLOR = new JBColor(new Color(231, 254, 234), Gray._100);
  private final Font NORMAL_FONT;
  private final Font BOLD_FONT;

  private static final Border BACKGROUND_BORDER = BorderFactory.createLineBorder(BACKGROUND_COLOR);

  protected int myWidthLimit;

  private static final Map<ParameterInfoUIContextEx.Flag, String> FLAG_TO_TAG =
    ImmutableMap.of(ParameterInfoUIContextEx.Flag.HIGHLIGHT, "b", ParameterInfoUIContextEx.Flag.DISABLE, "font color=gray",
                    ParameterInfoUIContextEx.Flag.STRIKEOUT, "strike");

  private static final Comparator<TextRange> TEXT_RANGE_COMPARATOR = new Comparator<TextRange>() {
    @Override
    public int compare(TextRange o1, TextRange o2) {
      if (o1.getStartOffset() < o2.getStartOffset()) return -1;
      if (o1.getEndOffset() > o2.getEndOffset()) return 1;
      return 0;
    }
  };

  public ParameterInfoComponent(Object[] objects, Editor editor, @NotNull ParameterInfoHandler handler) {
    super(new BorderLayout());

    JComponent editorComponent = editor.getComponent();
    JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();
    myWidthLimit = layeredPane.getWidth();

    NORMAL_FONT = UIUtil.getLabelFont();
    BOLD_FONT = NORMAL_FONT.deriveFont(Font.BOLD);

    myObjects = objects;

    setBackground(BACKGROUND_COLOR);

    myHandler = handler;
    myPanels = new OneElementComponent[myObjects.length];
    final JPanel panel = new JPanel(new GridBagLayout());
    for (int i = 0; i < myObjects.length; i++) {
      myPanels[i] = new OneElementComponent();
      panel.add(myPanels[i], new GridBagConstraints(0, i, 1, 1, 1, 0,
                                                    GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                                    new Insets(0, 0, 0, 0), 0, 0));
    }

    final JScrollPane pane = ScrollPaneFactory.createScrollPane(panel);
    pane.setBorder(null);
    pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    add(pane, BorderLayout.CENTER);

    myCurrentParameterIndex = -1;
  }

  @Override
  public Dimension getPreferredSize() {
    int size = myPanels.length;
    final Dimension preferredSize = super.getPreferredSize();
    if (size >= 0 && size <= 20) {
      return preferredSize;
    }
    else {
      return new Dimension(preferredSize.width + 20, 200);
    }
  }

  public Object getHighlighted() {
    return myHighlighted;
  }

  class MyParameterContext implements ParameterInfoUIContextEx {
    private int i;

    @Override
    public void setupUIComponentPresentation(String text,
                                             int highlightStartOffset,
                                             int highlightEndOffset,
                                             boolean isDisabled,
                                             boolean strikeout,
                                             boolean isDisabledBeforeHighlight,
                                             Color background) {
      myPanels[i].setup(text, highlightStartOffset, highlightEndOffset, isDisabled, strikeout, isDisabledBeforeHighlight, background);
      myPanels[i].setBorder(isLastParameterOwner() ? BACKGROUND_BORDER : new SideBorder(new JBColor(JBColor.LIGHT_GRAY, Gray._90), SideBorder.BOTTOM));
    }

    @Override
    public void setupUIComponentPresentation(final String[] texts, final EnumSet<Flag>[] flags, final Color background) {
      myPanels[i].setup(texts, flags, background);
      myPanels[i].setBorder(isLastParameterOwner() ? BACKGROUND_BORDER : new SideBorder(new JBColor(JBColor.LIGHT_GRAY, Gray._90), SideBorder.BOTTOM));
    }

    @Override
    public boolean isUIComponentEnabled() {
      return isEnabled(i);
    }

    @Override
    public void setUIComponentEnabled(boolean enabled) {
      setEnabled(i, enabled);
    }

    public boolean isLastParameterOwner() {
      return i == myPanels.length - 1;
    }

    @Override
    public int getCurrentParameterIndex() {
      return myCurrentParameterIndex;
    }

    @Override
    public PsiElement getParameterOwner() {
      return myParameterOwner;
    }

    @Override
    public Color getDefaultParameterColor() {
      return myObjects[i].equals(myHighlighted) ? HIGHLIGHTED_BORDER_COLOR : BACKGROUND_COLOR;
    }
  }

  public void update() {
    MyParameterContext context = new MyParameterContext();

    for (int i = 0; i < myObjects.length; i++) {
      context.i = i;
      final Object o = myObjects[i];

      //noinspection unchecked
      myHandler.updateUI(o, context);
    }

    invalidate();
    validate();
    repaint();
  }

  public Object[] getObjects() {
    return myObjects;
  }

  void setEnabled(int index, boolean enabled) {
    myPanels[index].setEnabled(enabled);
  }

  boolean isEnabled(int index) {
    return myPanels[index].isEnabled();
  }

  public void setCurrentParameterIndex(int currentParameterIndex) {
    myCurrentParameterIndex = currentParameterIndex;
  }

  public int getCurrentParameterIndex() {
    return myCurrentParameterIndex;
  }

  public void setParameterOwner(PsiElement element) {
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

    public OneElementComponent() {
      super(new GridBagLayout());
      myOneLineComponents = new OneLineComponent[0]; //TODO ???
    }

    private void setup(String text, int highlightStartOffset, int highlightEndOffset, boolean isDisabled, boolean strikeout, boolean isDisabledBeforeHighlight, Color background) {
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
      final String[] lines = UIUtil.splitText(StringUtil.join(texts), getFontMetrics(BOLD_FONT), myWidthLimit, ',');

      int index = 0;
      int curOffset = 0;

      myOneLineComponents = new OneLineComponent[lines.length];

      Map<TextRange, ParameterInfoUIContextEx.Flag> flagsMap = new TreeMap<TextRange, ParameterInfoUIContextEx.Flag>(TEXT_RANGE_COMPARATOR);

      for (int i = 0; i < texts.length; i++) {
        String line = texts[i];
        String text = lines[index];
        final EnumSet<ParameterInfoUIContextEx.Flag> flag = flags[i];
        if (flag.contains(ParameterInfoUIContextEx.Flag.HIGHLIGHT)) {
          flagsMap.put(TextRange.create(curOffset, curOffset + line.trim().length()), ParameterInfoUIContextEx.Flag.HIGHLIGHT);
        }

        if (flag.contains(ParameterInfoUIContextEx.Flag.DISABLE)) {
          flagsMap.put(TextRange.create(curOffset, curOffset + line.trim().length()), ParameterInfoUIContextEx.Flag.DISABLE);
        }

        if (flag.contains(ParameterInfoUIContextEx.Flag.STRIKEOUT)) {
          flagsMap.put(TextRange.create(curOffset, curOffset + line.trim().length()), ParameterInfoUIContextEx.Flag.STRIKEOUT);
        }

        curOffset += line.length();
        if (text.trim().endsWith(line.trim())) {
          myOneLineComponents[index] = new OneLineComponent();
          myOneLineComponents[index].setup(text, flagsMap, background);
          add(myOneLineComponents[index], new GridBagConstraints(0, index, 1, 1, 1, 0, GridBagConstraints.WEST,
                                                                 GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
          index += 1;
          flagsMap.clear();
          curOffset = 1;
        }
      }
    }
  }

  private class OneLineComponent extends JPanel {
    JLabel myLabel = new JLabel("", SwingConstants.LEFT);
    private boolean isDisabledBeforeHighlight = false;

    private OneLineComponent(){
      super(new GridBagLayout());

      myLabel.setOpaque(true);
      myLabel.setFont(NORMAL_FONT);

      add(myLabel, new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE,
                                          new Insets(0, 0, 0, 0), 0, 0));
    }

    private void setup(String text, int startOffset, int endOffset, boolean isDisabled, boolean isStrikeout, Color background) {
      final TextRange disabled = isDisabled ? TextRange.create(0, text.length()) : TextRange.EMPTY_RANGE;
      final TextRange strikeOut = isStrikeout ? TextRange.create(0, text.length()) : TextRange.EMPTY_RANGE;

      Map<TextRange, ParameterInfoUIContextEx.Flag> flagsMap = new TreeMap<TextRange, ParameterInfoUIContextEx.Flag>(TEXT_RANGE_COMPARATOR);
      flagsMap.put(TextRange.create(startOffset, endOffset), ParameterInfoUIContextEx.Flag.HIGHLIGHT);
      flagsMap.put(disabled, ParameterInfoUIContextEx.Flag.DISABLE);
      flagsMap.put(strikeOut, ParameterInfoUIContextEx.Flag.STRIKEOUT);
      setup(text, flagsMap, background);
    }

    private void setup(@NotNull String text, @NotNull Map<TextRange, ParameterInfoUIContextEx.Flag> flagsMap, @NotNull Color background) {
      myLabel.setBackground(background);
      setBackground(background);

      myLabel.setForeground(FOREGROUND_COLOR);

      if (flagsMap.isEmpty()) {
        myLabel.setText(text);
      }
      else {
        String labelText = buildLabelText(text, flagsMap);
        myLabel.setText(labelText);
      }
    }

    private String buildLabelText(@NotNull final String text, @NotNull final Map<TextRange, ParameterInfoUIContextEx.Flag> flagsMap) {
      StringBuilder labelText = new StringBuilder("<html>");
      int index = 0;
      final String disabledTag = FLAG_TO_TAG.get(ParameterInfoUIContextEx.Flag.DISABLE);
      if (isDisabledBeforeHighlight) {
        addTag(labelText, disabledTag);
      }

      for (Map.Entry<TextRange, ParameterInfoUIContextEx.Flag> entry : flagsMap.entrySet()) {
        TextRange highlightRange = entry.getKey();
        final ParameterInfoUIContextEx.Flag flag = entry.getValue();

        String tagValue = FLAG_TO_TAG.get(flag);
        labelText.append(text.substring(index, highlightRange.getStartOffset()));
        if (flag == ParameterInfoUIContextEx.Flag.HIGHLIGHT && isDisabledBeforeHighlight) {
          addClosingTag(labelText, disabledTag);
        }
        addTag(labelText, tagValue);
        labelText.append(text.substring(highlightRange.getStartOffset(), highlightRange.getEndOffset()));
        addClosingTag(labelText, tagValue);
        index = highlightRange.getEndOffset();
      }
      labelText.append(text.substring(index, text.length()));
      labelText.append("</html>");
      return labelText.toString();
    }

    private void addClosingTag(@NotNull final StringBuilder labelText, @NotNull final String tagText) {
      labelText.append("</").append(tagText).append(">");
    }

    private void addTag(@NotNull final StringBuilder labelText, @NotNull final String disabledTag) {
      labelText.append("<").append(disabledTag).append(">");
    }

    public void setDisabledBeforeHighlight() {
      isDisabledBeforeHighlight = true;
    }
  }
}
