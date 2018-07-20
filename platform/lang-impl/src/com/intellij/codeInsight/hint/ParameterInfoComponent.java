// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.hint;

import com.google.common.collect.ImmutableMap;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.lang.parameterInfo.ParameterInfoUIContextEx;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.ui.*;
import com.intellij.util.Function;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ParameterInfoComponent extends JPanel {
  private final Object[] myObjects;
  private int myCurrentParameterIndex;

  private PsiElement myParameterOwner;
  private Object myHighlighted;
  @NotNull private final ParameterInfoHandler myHandler;

  private final OneElementComponent[] myPanels;
  private final JLabel myShortcutLabel;

  private final Font NORMAL_FONT;
  private final Font BOLD_FONT;

  private static final Border EMPTY_BORDER = BorderFactory.createEmptyBorder();
  private static final Border BOTTOM_BORDER = new SideBorder(new JBColor(JBColor.LIGHT_GRAY, Gray._90), SideBorder.BOTTOM);

  protected int myWidthLimit = 500;

  private static final Map<ParameterInfoUIContextEx.Flag, String> FLAG_TO_TAG =
    ImmutableMap.of(ParameterInfoUIContextEx.Flag.HIGHLIGHT, "b", ParameterInfoUIContextEx.Flag.DISABLE, "font color=gray",
                    ParameterInfoUIContextEx.Flag.STRIKEOUT, "strike");

  private static final Comparator<TextRange> TEXT_RANGE_COMPARATOR = (o1, o2) -> {
    if (o1.getStartOffset() == o2.getStartOffset()) {
      return o1.getEndOffset() > o2.getEndOffset() ? 1 : -1;
    }
    if (o1.getStartOffset() > o2.getStartOffset()) return 1;
    if (o1.getEndOffset() > o2.getEndOffset()) return 1;
    return -1;
  };
  private final boolean myRequestFocus;

  @TestOnly
  public static ParameterInfoUIContextEx createContext(Object[] objects, Editor editor, @NotNull ParameterInfoHandler handler, int currentParameterIndex) {
    return createContext(objects, editor, handler, currentParameterIndex, null);
  }

  @TestOnly
  public static ParameterInfoUIContextEx createContext(Object[] objects, Editor editor, @NotNull ParameterInfoHandler handler, int currentParameterIndex, @Nullable PsiElement parameterOwner) {
    final ParameterInfoComponent infoComponent = new ParameterInfoComponent(objects, editor, handler);
    infoComponent.setCurrentParameterIndex(currentParameterIndex);
    infoComponent.setParameterOwner(parameterOwner);
    return infoComponent.new MyParameterContext(false);
  }

  ParameterInfoComponent(Object[] objects, Editor editor, @NotNull ParameterInfoHandler handler) {
    this(objects, editor, handler, false, false);
  }

  ParameterInfoComponent(Object[] objects, Editor editor, @NotNull ParameterInfoHandler handler, 
                         boolean requestFocus, boolean allowSwitchLabel) {
    super(new BorderLayout());
    myRequestFocus = requestFocus;

    if (!ApplicationManager.getApplication().isUnitTestMode() && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
      JComponent editorComponent = editor.getComponent();
      JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();
      myWidthLimit = layeredPane.getWidth();
    }

    NORMAL_FONT = UIUtil.getLabelFont();
    BOLD_FONT = NORMAL_FONT.deriveFont(Font.BOLD);

    myObjects = objects;

    setBackground(HintUtil.getInformationColor());

    myHandler = handler;
    myPanels = new OneElementComponent[myObjects.length];
    final JPanel panel = new JPanel(new GridBagLayout());
    for (int i = 0; i < myObjects.length; i++) {
      myPanels[i] = new OneElementComponent();
      panel.add(myPanels[i], new GridBagConstraints(0, i, 1, 1, 1, 0,
                                                    GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                                    new Insets(0, 0, 0, 0), 0, 0));
    }
    if (myRequestFocus) {
      AccessibleContextUtil.setName(this, "Parameter Info. Press TAB to navigate through each element. Press ESC to close.");
    }

    final JScrollPane pane = ScrollPaneFactory.createScrollPane(panel);
    pane.setBorder(null);
    pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    add(pane, BorderLayout.CENTER);

    String upShortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_METHOD_OVERLOAD_SWITCH_UP);
    String downShortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_METHOD_OVERLOAD_SWITCH_DOWN);
    if (!allowSwitchLabel || editor instanceof EditorWindow || myObjects.length <= 1 || !myHandler.supportsOverloadSwitching() || 
        upShortcut.isEmpty() && downShortcut.isEmpty()) {
      myShortcutLabel = null;
    }
    else {
      myShortcutLabel = new JLabel(
        upShortcut.isEmpty() || downShortcut.isEmpty() 
        ? CodeInsightBundle.message("parameter.info.switch.overload.shortcuts.single", upShortcut.isEmpty() ? downShortcut : upShortcut)
        : CodeInsightBundle.message("parameter.info.switch.overload.shortcuts", upShortcut, downShortcut));
      myShortcutLabel.setForeground(new JBColor(0x787878, 0x787878));
      Font labelFont = UIUtil.getLabelFont();
      myShortcutLabel.setFont(labelFont.deriveFont(labelFont.getSize2D() - (SystemInfo.isWindows ? 1 : 2)));
      myShortcutLabel.setBorder(new JBEmptyBorder(3, 0, 0, 0));
      add(myShortcutLabel, BorderLayout.SOUTH);
    }
    myCurrentParameterIndex = -1;
  }

  @Override
  public Dimension getPreferredSize() {
    long visibleRows = Stream.of(myPanels).filter(Component::isVisible).count();
    final Dimension preferredSize = super.getPreferredSize();
    if (visibleRows <= 20) {
      return preferredSize;
    }
    else {
      return new Dimension(preferredSize.width + 20, 200);
    }
  }

  @Override
  public String toString() {
    return Stream.of(myPanels)
      .filter(Component::isVisible)
      .map(c -> c.toString() + (c.getBorder() == BOTTOM_BORDER ? "\n-" : ""))
      .collect(Collectors.joining("\n"));
  }

  public Object getHighlighted() {
    return myHighlighted;
  }

  public boolean isRequestFocus() {
    return myRequestFocus;
  }

  class MyParameterContext implements ParameterInfoUIContextEx {
    private final boolean mySingleParameterInfo;
    private int i;
    private Function<String, String> myEscapeFunction;
    private final ParameterInfoController.Model result = new ParameterInfoController.Model();
    
    public MyParameterContext(boolean singleParameterInfo) {
      mySingleParameterInfo = singleParameterInfo;
    }

    @Override
    public String setupUIComponentPresentation(String text,
                                               int highlightStartOffset,
                                               int highlightEndOffset,
                                               boolean isDisabled,
                                               boolean strikeout,
                                               boolean isDisabledBeforeHighlight,
                                               Color background) {
      List<String> split = StringUtil.split(text, ",", false);
      StringBuilder plainLine = new StringBuilder();
      final List<Integer> startOffsets = new ArrayList<>();
      final List<Integer> endOffsets = new ArrayList<>();

      TextRange highlightRange = highlightStartOffset >=0 && highlightEndOffset >= highlightStartOffset ?
                               new TextRange(highlightStartOffset, highlightEndOffset) :
                               null;
      for (int j = 0; j < split.size(); j++) {
        String line = split.get(j);
        int startOffset = plainLine.length();
        startOffsets.add(startOffset);
        plainLine.append(line);
        int endOffset = plainLine.length();
        endOffsets.add(endOffset);
        if (highlightRange != null && highlightRange.intersects(new TextRange(startOffset, endOffset))) {
          result.current = j;
        }
      }
      ParameterInfoController.SignatureItem item = new ParameterInfoController.SignatureItem(plainLine.toString(), strikeout, isDisabled,
                                                                                             startOffsets, endOffsets);
      result.signatures.add(item);

      final String resultedText =
        myPanels[i].setup(text, myEscapeFunction, highlightStartOffset, highlightEndOffset, isDisabled, strikeout, isDisabledBeforeHighlight, background);
      myPanels[i].setBorder(isLastParameterOwner() || isSingleParameterInfo() ? EMPTY_BORDER : BOTTOM_BORDER);
      return resultedText;
    }

    @Override
    public void setupRawUIComponentPresentation(String htmlText) {
      myPanels[i].setup(htmlText, getDefaultParameterColor());
      myPanels[i].setBorder(isLastParameterOwner() || isSingleParameterInfo() ? EMPTY_BORDER : BOTTOM_BORDER);
    }

    @Override
    public String setupUIComponentPresentation(final String[] texts, final EnumSet<Flag>[] flags, final Color background) {
      final String resultedText = myPanels[i].setup(result, texts, myEscapeFunction, flags, background);
      myPanels[i].setBorder(isLastParameterOwner() || isSingleParameterInfo() ? EMPTY_BORDER : BOTTOM_BORDER);
      return resultedText;
    }

    @Override
    public void setEscapeFunction(@Nullable Function<String, String> escapeFunction) {
      myEscapeFunction = escapeFunction;
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
    public boolean isSingleOverload() {
      return myPanels.length == 1;
    }

    @Override
    public boolean isSingleParameterInfo() {
      return mySingleParameterInfo;
    }

    private boolean isHighlighted() {
      return myObjects[i].equals(myHighlighted);
    }

    @Override
    public Color getDefaultParameterColor() {
      Color color = HintUtil.getInformationColor();
      return mySingleParameterInfo || !isHighlighted() ? color :
             ColorUtil.isDark(color) ? ColorUtil.brighter(color, 2) : ColorUtil.darker(color, 2);
    }
  }

  public ParameterInfoController.Model update(boolean singleParameterInfo) {
    MyParameterContext context = new MyParameterContext(singleParameterInfo);

    for (int i = 0; i < myObjects.length; i++) {
      context.i = i;
      final Object o = myObjects[i];

      if (singleParameterInfo && myObjects.length > 1 && !context.isHighlighted()) {
        setVisible(i, false);
      }
      else {
        setVisible(i, true);
        //noinspection unchecked
        myHandler.updateUI(o, context);
      }
    }

    if (myShortcutLabel != null) myShortcutLabel.setVisible(!singleParameterInfo);
    return context.result;
  }

  public Object[] getObjects() {
    return myObjects;
  }

  void setEnabled(int index, boolean enabled) {
    myPanels[index].setEnabled(enabled);
  }

  void setVisible(int index, boolean visible) {
    myPanels[index].setVisible(visible);
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

    @Override
    public String toString() {
      boolean highlighted = myOneLineComponents.length > 0 && 
                            !HintUtil.getInformationColor().equals(myOneLineComponents[0].getBackground());
      String text = Stream.of(myOneLineComponents).filter(Objects::nonNull).map(Object::toString).collect(Collectors.joining());
      return highlighted ? '[' + text + ']' : text;
    }

    private void setup(String htmlText, Color background) {
      removeAll();
      myOneLineComponents = new OneLineComponent[1];
      myOneLineComponents[0] = new OneLineComponent();
      myOneLineComponents[0].doSetup(htmlText, background);
      add(myOneLineComponents[0], new GridBagConstraints(0,0,1,1,1,0,
                                                         GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                                         new Insets(0,0,0,0),0,0));
    }

    private String setup(String text,
                         Function<String, String> escapeFunction,
                         int highlightStartOffset,
                         int highlightEndOffset,
                         boolean isDisabled,
                         boolean strikeout,
                         boolean isDisabledBeforeHighlight,
                         Color background) {
      StringBuilder buf = new StringBuilder();
      removeAll();

      String[] lines = UIUtil.splitText(text, getFontMetrics(BOLD_FONT), myWidthLimit, ',');

      myOneLineComponents = new OneLineComponent[lines.length];

      int lineOffset = 0;

      boolean hasHighlighting = highlightStartOffset >= 0 && highlightEndOffset > highlightStartOffset;
      TextRange highlightingRange = hasHighlighting ? new TextRange(highlightStartOffset, highlightEndOffset) : null;

      for (int i = 0; i < lines.length; i++) {
        String line = lines[i];

        myOneLineComponents[i] = new OneLineComponent();

        TextRange lRange = new TextRange(lineOffset, lineOffset + line.length());
        TextRange hr = highlightingRange == null ? null : lRange.intersection(highlightingRange);
        hr = hr == null ? null : hr.shiftRight(-lineOffset);

        String before = escapeString(hr == null ? line : line.substring(0, hr.getStartOffset()), escapeFunction);
        String in = hr == null ? "" : escapeString(hr.substring(line), escapeFunction);
        String after = hr == null ? "" : escapeString(line.substring(hr.getEndOffset()), escapeFunction);

        TextRange escapedHighlightingRange = in.isEmpty() ? null : TextRange.create(before.length(), before.length() + in.length());
        buf.append(myOneLineComponents[i].setup(before + in + after, isDisabled, strikeout, background, escapedHighlightingRange));

        if (isDisabledBeforeHighlight) {
          if (highlightStartOffset < 0 || highlightEndOffset > lineOffset) {
            myOneLineComponents[i].setDisabledBeforeHighlight();
          }
        }

        add(myOneLineComponents[i], new GridBagConstraints(0,i,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(0,0,0,0),0,0));

        lineOffset += line.length();
      }
      return buf.toString();
    }

    private String escapeString(String line, Function<String, String> escapeFunction) {
      line = XmlStringUtil.escapeString(line);
      return escapeFunction == null ? line : escapeFunction.fun(line);
    }

    public String setup(final ParameterInfoController.Model result,
                        final String[] texts,
                        Function<String, String> escapeFunction,
                        final EnumSet<ParameterInfoUIContextEx.Flag>[] flags,
                        final Color background) {
      StringBuilder buf = new StringBuilder();
      removeAll();
      setBackground(background);
      int index = 0;
      int curOffset = 0;
      final ArrayList<OneLineComponent> components = new ArrayList<>();
      final List<Integer> startOffsets = new ArrayList<>();
      final List<Integer> endOffsets = new ArrayList<>();


      Map<TextRange, ParameterInfoUIContextEx.Flag> flagsMap = new TreeMap<>(TEXT_RANGE_COMPARATOR);

      StringBuilder fullLine = new StringBuilder();
      StringBuilder line = new StringBuilder();
      for (int i = 0; i < texts.length; i++) {
        String paramText = escapeString(texts[i], escapeFunction);
        if (paramText == null) break;
        startOffsets.add(fullLine.length());
        fullLine.append(texts[i]);
        endOffsets.add(fullLine.length());
        line.append(texts[i]);
        final EnumSet<ParameterInfoUIContextEx.Flag> flag = flags[i];
        if (flag.contains(ParameterInfoUIContextEx.Flag.HIGHLIGHT)) {
          result.current = i;
          flagsMap.put(TextRange.create(curOffset, curOffset + paramText.trim().length()), ParameterInfoUIContextEx.Flag.HIGHLIGHT);
        }

        if (flag.contains(ParameterInfoUIContextEx.Flag.DISABLE)) {
          flagsMap.put(TextRange.create(curOffset, curOffset + paramText.trim().length()), ParameterInfoUIContextEx.Flag.DISABLE);
        }

        if (flag.contains(ParameterInfoUIContextEx.Flag.STRIKEOUT)) {
          flagsMap.put(TextRange.create(curOffset, curOffset + paramText.trim().length()), ParameterInfoUIContextEx.Flag.STRIKEOUT);
        }

        curOffset += paramText.length();
        if (line.length() >= 50) {
          final OneLineComponent component = new OneLineComponent();
          buf.append(component.setup(escapeString(line.toString(), escapeFunction), flagsMap, background));
          add(component, new GridBagConstraints(0, index, 1, 1, 1, 0, GridBagConstraints.WEST,
                                                                 GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
          index += 1;
          flagsMap.clear();
          curOffset = 0;
          line = new StringBuilder();
          components.add(component);
        }
      }
      ParameterInfoController.SignatureItem item = new ParameterInfoController.SignatureItem(fullLine.toString(), false, false,
                                                                                             startOffsets, endOffsets);
      result.signatures.add(item);
      final OneLineComponent component = new OneLineComponent();
      buf.append(component.setup(escapeString(line.toString(), escapeFunction), flagsMap, background));
      add(component, new GridBagConstraints(0, index, 1, 1, 1, 0, GridBagConstraints.WEST,
                                            GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
      components.add(component);
      myOneLineComponents = components.toArray(new OneLineComponent[0]);
      return buf.toString();
    }
  }

  private class OneLineComponent extends JPanel {
    JLabel myLabel = new JLabel("", SwingConstants.LEFT);
    private boolean isDisabledBeforeHighlight = false;

    private OneLineComponent(){
      super(new GridBagLayout());

      myLabel.setOpaque(true);
      myLabel.setFont(NORMAL_FONT);
      if (myRequestFocus)
        myLabel.setFocusable(true);

      add(myLabel, new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.NONE,
                                          new Insets(0, 0, 0, 0), 0, 0));
    }

    @Override
    public String toString() {
      return myLabel.getText();
    }

    private String setup(String text,
                         boolean isDisabled,
                         boolean isStrikeout,
                         Color background, @Nullable TextRange range) {
      Map<TextRange, ParameterInfoUIContextEx.Flag> flagsMap = new TreeMap<>(TEXT_RANGE_COMPARATOR);
      if (range != null)
        flagsMap.put(range, ParameterInfoUIContextEx.Flag.HIGHLIGHT);
      if (isDisabled)
        flagsMap.put(TextRange.create(0, text.length()), ParameterInfoUIContextEx.Flag.DISABLE);
      if (isStrikeout)
        flagsMap.put(TextRange.create(0, text.length()), ParameterInfoUIContextEx.Flag.STRIKEOUT);
      return setup(text, flagsMap, background);
    }

    private String setup(@NotNull String text, @NotNull Map<TextRange, ParameterInfoUIContextEx.Flag> flagsMap, @NotNull Color background) {
      if (flagsMap.isEmpty()) {
        return doSetup(text, background);
      }
      else {
        String labelText = buildLabelText(text, flagsMap);
        return doSetup(labelText, background);
      }
    }

    private String doSetup(@NotNull String text, @NotNull Color background) {
      myLabel.setBackground(background);
      setBackground(background);

      myLabel.setForeground(JBColor.foreground());

      myLabel.setText(XmlStringUtil.wrapInHtml(text));

      //IDEA-95904 Darcula parameter info pop-up colors hard to read
      if (UIUtil.isUnderDarcula()) {
        myLabel.setText(myLabel.getText().replace("<b>", "<b color=ffC800>"));
      }
      return myLabel.getText();
    }

    private String buildLabelText(@NotNull final String text, @NotNull final Map<TextRange, ParameterInfoUIContextEx.Flag> flagsMap) {
      final StringBuilder labelText = new StringBuilder(text);
      final String disabledTag = FLAG_TO_TAG.get(ParameterInfoUIContextEx.Flag.DISABLE);

      final Map<Integer, Integer> faultMap = new HashMap<>();
      if (isDisabledBeforeHighlight) {
        final String tag = getTag(disabledTag);
        labelText.insert(0, tag);
        faultMap.put(0, tag.length());
      }

      for (Map.Entry<TextRange, ParameterInfoUIContextEx.Flag> entry : flagsMap.entrySet()) {
        final TextRange highlightRange = entry.getKey();
        final ParameterInfoUIContextEx.Flag flag = entry.getValue();

        final String tagValue = FLAG_TO_TAG.get(flag);
        final String tag = getTag(tagValue);

        int startOffset = highlightRange.getStartOffset();
        int endOffset = highlightRange.getEndOffset() + tag.length();

        for (Map.Entry<Integer, Integer> entry1 : faultMap.entrySet()) {
          if (entry1.getKey() < highlightRange.getStartOffset()) {
            startOffset += entry1.getValue();
          }
          if (entry1.getKey() < highlightRange.getEndOffset()) {
            endOffset += entry1.getValue();
          }
        }

        if (flag == ParameterInfoUIContextEx.Flag.HIGHLIGHT && isDisabledBeforeHighlight) {
          final String disableCloseTag = getClosingTag(disabledTag);
          labelText.insert(startOffset, disableCloseTag);
          faultMap.put(highlightRange.getStartOffset(), disableCloseTag.length());
        }

        labelText.insert(startOffset, tag);
        faultMap.put(highlightRange.getStartOffset(), tag.length());

        final String endTag = getClosingTag(tagValue);
        labelText.insert(endOffset, endTag);
        faultMap.put(highlightRange.getEndOffset(), endTag.length());

      }
      return labelText.toString();
    }

    private String getTag(@NotNull final String tagValue) {
      return "<" + tagValue + ">";
    }

    private String getClosingTag(@NotNull final String tagValue) {
      return "</" + tagValue + ">";
    }

    public void setDisabledBeforeHighlight() {
      isDisabledBeforeHighlight = true;
    }
  }
}
