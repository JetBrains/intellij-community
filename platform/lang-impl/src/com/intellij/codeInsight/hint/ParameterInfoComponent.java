// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.lang.parameterInfo.ParameterInfoUIContextEx;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.Function;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import com.intellij.xml.util.XmlStringUtil;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntMaps;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ParameterInfoComponent extends JPanel {
  private Object[] myObjects;
  private int myCurrentParameterIndex;

  private PsiElement myParameterOwner;
  private Object myHighlighted;
  @NotNull private final ParameterInfoHandler myHandler;

  private final JPanel myMainPanel;
  private OneElementComponent[] myPanels;
  private JLabel myShortcutLabel;
  private final boolean myAllowSwitchLabel;

  private final Font NORMAL_FONT;
  private final Font BOLD_FONT;

  private static final Color BACKGROUND = JBColor.namedColor("ParameterInfo.background", HintUtil.getInformationColor());
  private static final Color FOREGROUND = JBColor.namedColor("ParameterInfo.foreground", new JBColor(0x1D1D1D, 0xBBBBBB));
  private static final Color HIGHLIGHTED_COLOR = JBColor.namedColor("ParameterInfo.currentParameterForeground", new JBColor(0x1D1D1D, 0xE8E8E8));
  private static final Color DISABLED_COLOR = JBColor.namedColor("ParameterInfo.disabledForeground", new JBColor(0xA8A8A8, 0x777777));
  private static final Color CONTEXT_HELP_FOREGROUND = JBColor.namedColor("ParameterInfo.infoForeground", new JBColor(0x787878, 0x878787));
  static final Color BORDER_COLOR = JBColor.namedColor("ParameterInfo.borderColor", HintUtil.INFORMATION_BORDER_COLOR);
  private static final Color HIGHLIGHTED_BACKGROUND = JBColor.namedColor("ParameterInfo.currentOverloadBackground", BORDER_COLOR);
  private static final Color SEPARATOR_COLOR = JBColor.namedColor("ParameterInfo.lineSeparatorColor", BORDER_COLOR);
  private static final Border EMPTY_BORDER = JBUI.Borders.empty(2, 10);
  private static final Border BOTTOM_BORDER = new CompoundBorder(JBUI.Borders.customLine(SEPARATOR_COLOR, 0, 0, 1, 0), EMPTY_BORDER);

  protected int myWidthLimit = 500;
  private final int myMaxVisibleRows = Registry.intValue("parameter.info.max.visible.rows");

  private static final Comparator<TextRange> TEXT_RANGE_COMPARATOR = (o1, o2) -> {
    int endResult = Integer.compare(o2.getEndOffset(), o1.getEndOffset());
    return endResult == 0 ? Integer.compare(o1.getStartOffset(), o2.getStartOffset()) : endResult;
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

    NORMAL_FONT = editor != null && Registry.is("parameter.info.editor.font")
                  ? editor.getColorsScheme().getFont(EditorFontType.PLAIN)
                  : UIUtil.getLabelFont();
    BOLD_FONT = editor != null && Registry.is("parameter.info.editor.font")
                ? editor.getColorsScheme().getFont(EditorFontType.BOLD)
                : NORMAL_FONT.deriveFont(Font.BOLD);

    myObjects = objects;

    setBackground(BACKGROUND);

    myHandler = handler;
    myMainPanel = new JPanel(new GridBagLayout());
    setPanels();

    if (myRequestFocus) {
      AccessibleContextUtil.setName(this, "Parameter Info. Press TAB to navigate through each element. Press ESC to close.");
    }

    final JScrollPane pane = ScrollPaneFactory.createScrollPane(myMainPanel, true);
    add(pane, BorderLayout.CENTER);

    myAllowSwitchLabel = allowSwitchLabel && !(editor instanceof EditorWindow);
    setShortcutLabel();
    myCurrentParameterIndex = -1;
  }

  private void setPanels() {
    myMainPanel.removeAll();
    myPanels = new OneElementComponent[myObjects.length];
    for (int i = 0; i < myObjects.length; i++) {
      myPanels[i] = new OneElementComponent();
      myMainPanel.add(myPanels[i], new GridBagConstraints(0, i, 1, 1, 1, 0,
                                                          GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                                          JBUI.emptyInsets(), 0, 0));
    }
  }

  private void setShortcutLabel() {
    if (myShortcutLabel != null) remove(myShortcutLabel);

    String upShortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_METHOD_OVERLOAD_SWITCH_UP);
    String downShortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_METHOD_OVERLOAD_SWITCH_DOWN);
    if (!myAllowSwitchLabel || myObjects.length <= 1 || !myHandler.supportsOverloadSwitching() ||
        upShortcut.isEmpty() && downShortcut.isEmpty()) {
      myShortcutLabel = null;
    }
    else {
      myShortcutLabel = new JLabel(
        upShortcut.isEmpty() || downShortcut.isEmpty()
        ? CodeInsightBundle.message("parameter.info.switch.overload.shortcuts.single", upShortcut.isEmpty() ? downShortcut : upShortcut)
        : CodeInsightBundle.message("parameter.info.switch.overload.shortcuts", upShortcut, downShortcut));
      myShortcutLabel.setForeground(CONTEXT_HELP_FOREGROUND);
      Font labelFont = UIUtil.getLabelFont();
      myShortcutLabel.setFont(labelFont.deriveFont(labelFont.getSize2D() - (SystemInfo.isWindows ? 1 : 2)));
      myShortcutLabel.setBorder(JBUI.Borders.empty(6, 10, 0, 10));
      add(myShortcutLabel, BorderLayout.SOUTH);
    }
  }

  void setDescriptors(Object[] descriptors) {
    myObjects = descriptors;
    setPanels();
    setShortcutLabel();
  }

  @Override
  public Dimension getPreferredSize() {
    long visibleRows = Stream.of(myPanels).filter(Component::isVisible).count();
    final Dimension preferredSize = super.getPreferredSize();
    if (visibleRows <= myMaxVisibleRows) {
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
    private Function<? super String, String> myEscapeFunction;
    private final ParameterInfoController.Model result = new ParameterInfoController.Model();

    MyParameterContext(boolean singleParameterInfo) {
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
      ParameterInfoController.RawSignatureItem item = new ParameterInfoController.RawSignatureItem(htmlText);

      result.current = getCurrentParameterIndex();
      result.signatures.add(item);

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
    public void setEscapeFunction(@Nullable Function<? super String, String> escapeFunction) {
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
      return mySingleParameterInfo || !isHighlighted() ? BACKGROUND : HIGHLIGHTED_BACKGROUND;
    }
  }

  public ParameterInfoController.Model update(boolean singleParameterInfo) {
    MyParameterContext context = new MyParameterContext(singleParameterInfo);

    int highlightedComponentIdx = -1;
    for (int i = 0; i < myObjects.length; i++) {
      context.i = i;
      final Object o = myObjects[i];

      boolean isHighlighted = myObjects[i].equals(myHighlighted);
      if (isHighlighted) {
        context.result.highlightedSignature = i;
      }
      if (singleParameterInfo && myObjects.length > 1 && !context.isHighlighted()) {
        setVisible(i, false);
      }
      else {
        setVisible(i, true);
        //noinspection unchecked
        myHandler.updateUI(o, context);

        // ensure that highlighted element is visible
        if (context.isHighlighted()) {
          highlightedComponentIdx = i;
        }
      }
    }

    if (myShortcutLabel != null) myShortcutLabel.setVisible(!singleParameterInfo);

    if (highlightedComponentIdx != -1) {
      myMainPanel.scrollRectToVisible(new Rectangle()); // hack to validate component tree synchronously
      myMainPanel.scrollRectToVisible(myPanels[highlightedComponentIdx].getBounds());
    }

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

    OneElementComponent() {
      super(new GridBagLayout());
      setOpaque(true);
    }

    @Override
    public String toString() {
      boolean highlighted = getComponentCount() > 0 && !BACKGROUND.equals(getComponent(0).getBackground());
      String text = Stream.of(getComponents()).map(Object::toString).collect(Collectors.joining());
      return highlighted ? '[' + text + ']' : text;
    }

    private OneLineComponent getOneLineComponent(int index) {
      for (int i = getComponentCount(); i <= index; i++) {
        add(new OneLineComponent(),
            new GridBagConstraints(0, i, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0));
      }
      return (OneLineComponent)getComponent(index);
    }

    private void trimComponents(int count) {
      for (int i = getComponentCount() - 1; i >= count; i--) {
        remove(i);
      }
    }

    private void setup(String htmlText, Color background) {
      setBackground(background);
      getOneLineComponent(0).doSetup(htmlText, background);
      trimComponents(1);
    }

    private String setup(String text,
                         Function<? super String, String> escapeFunction,
                         int highlightStartOffset,
                         int highlightEndOffset,
                         boolean isDisabled,
                         boolean strikeout,
                         boolean isDisabledBeforeHighlight,
                         Color background) {
      StringBuilder buf = new StringBuilder(text.length());
      setBackground(background);

      String[] lines = UIUtil.splitText(text, getFontMetrics(BOLD_FONT),
                                        // disable splitting by width, to avoid depending on platform's font in tests
                                        ApplicationManager.getApplication().isUnitTestMode() ? Integer.MAX_VALUE : myWidthLimit,
                                        ',');

      int lineOffset = 0;

      boolean hasHighlighting = highlightStartOffset >= 0 && highlightEndOffset > highlightStartOffset;
      TextRange highlightingRange = hasHighlighting ? new TextRange(highlightStartOffset, highlightEndOffset) : null;

      for (int i = 0; i < lines.length; i++) {
        String line = lines[i];
        OneLineComponent component = getOneLineComponent(i);

        TextRange lRange = new TextRange(lineOffset, lineOffset + line.length());
        TextRange hr = highlightingRange == null ? null : lRange.intersection(highlightingRange);
        hr = hr == null ? null : hr.shiftRight(-lineOffset);

        String before = escapeString(hr == null ? line : line.substring(0, hr.getStartOffset()), escapeFunction);
        String in = hr == null ? "" : escapeString(hr.substring(line), escapeFunction);
        String after = hr == null ? "" : escapeString(line.substring(hr.getEndOffset()), escapeFunction);

        TextRange escapedHighlightingRange = in.isEmpty() ? null : TextRange.create(before.length(), before.length() + in.length());
        buf.append(component.setup(before + in + after, isDisabled, strikeout, background, escapedHighlightingRange,
                                   isDisabledBeforeHighlight && (highlightStartOffset < 0 || highlightEndOffset > lineOffset)));

         lineOffset += line.length();
      }
      trimComponents(lines.length);
      return buf.toString();
    }

    private String escapeString(String line, Function<? super String, String> escapeFunction) {
      line = XmlStringUtil.escapeString(line);
      return escapeFunction == null ? line : escapeFunction.fun(line);
    }

    public String setup(final ParameterInfoController.Model result,
                        final String[] texts,
                        Function<? super String, String> escapeFunction,
                        final EnumSet<ParameterInfoUIContextEx.Flag>[] flags,
                        final Color background) {
      StringBuilder buf = new StringBuilder();
      setBackground(background);
      int index = 0;
      int curOffset = 0;
      final List<Integer> startOffsets = new ArrayList<>();
      final List<Integer> endOffsets = new ArrayList<>();
      TreeMap<TextRange, ParameterInfoUIContextEx.Flag> flagsMap = new TreeMap<>(TEXT_RANGE_COMPARATOR);
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
          OneLineComponent component = getOneLineComponent(index);
          buf.append(component.setup(escapeString(line.toString(), escapeFunction), flagsMap, background));
          index += 1;
          flagsMap.clear();
          curOffset = 0;
          line = new StringBuilder();
        }
      }
      ParameterInfoController.SignatureItem item = new ParameterInfoController.SignatureItem(fullLine.toString(), false, false,
                                                                                             startOffsets, endOffsets);
      result.signatures.add(item);
      OneLineComponent component = getOneLineComponent(index);
      buf.append(component.setup(escapeString(line.toString(), escapeFunction), flagsMap, background));
      trimComponents(index + 1);
      return buf.toString();
    }
  }

  private final class OneLineComponent extends JPanel {
    JLabel myLabel = new JLabel("", SwingConstants.LEFT);

    private OneLineComponent(){
      super(new GridBagLayout());

      myLabel.setOpaque(true);
      myLabel.setFont(NORMAL_FONT);
      if (myRequestFocus)
        myLabel.setFocusable(true);

      add(myLabel, new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.NONE, JBUI.emptyInsets(), 0, 0));
    }

    @Override
    public String toString() {
      return myLabel.getText();
    }

    private String setup(String text,
                         boolean isDisabled,
                         boolean isStrikeout,
                         Color background,
                         @Nullable TextRange highlightRange,
                         boolean disableBeforeHighlight) {
      TreeMap<TextRange, ParameterInfoUIContextEx.Flag> flagsMap = new TreeMap<>(TEXT_RANGE_COMPARATOR);
      if (highlightRange != null) {
        flagsMap.put(highlightRange, ParameterInfoUIContextEx.Flag.HIGHLIGHT);
      }
      if (isDisabled) {
        flagsMap.put(TextRange.create(0, text.length()), ParameterInfoUIContextEx.Flag.DISABLE);
      }
      if (isStrikeout) {
        flagsMap.put(TextRange.create(0, text.length()), ParameterInfoUIContextEx.Flag.STRIKEOUT);
      }
      if (disableBeforeHighlight) {
        flagsMap.put(new TextRange(0, highlightRange == null ? text.length() : highlightRange.getStartOffset()),
                     ParameterInfoUIContextEx.Flag.DISABLE);
      }
      return setup(text, flagsMap, background);
    }

    // flagsMap is supposed to use TEXT_RANGE_COMPARATOR
    private String setup(@NotNull String text, @NotNull TreeMap<TextRange, ParameterInfoUIContextEx.Flag> flagsMap,
                         @NotNull Color background) {
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

      myLabel.setForeground(FOREGROUND);

      myLabel.setText(XmlStringUtil.wrapInHtml(text));
      return myLabel.getText();
    }

    // flagsMap is supposed to use TEXT_RANGE_COMPARATOR
    private String buildLabelText(@NotNull final String text, @NotNull final TreeMap<TextRange, ParameterInfoUIContextEx.Flag> flagsMap) {
      final StringBuilder labelText = new StringBuilder(text);
      final Int2IntOpenHashMap faultMap = new Int2IntOpenHashMap();

      for (Map.Entry<TextRange, ParameterInfoUIContextEx.Flag> entry : flagsMap.entrySet()) {
        final TextRange highlightRange = entry.getKey();
        final ParameterInfoUIContextEx.Flag flag = entry.getValue();

        final String tagValue = getTagValue(flag);
        final String tag = getOpeningTag(tagValue);
        final String endTag = getClosingTag(tagValue);

        int startOffset = highlightRange.getStartOffset();
        int endOffset = highlightRange.getEndOffset() + tag.length();

        for (Int2IntMap.Entry entry1 : Int2IntMaps.fastIterable(faultMap)) {
          if (entry1.getIntKey() <= highlightRange.getStartOffset()) {
            startOffset += entry1.getIntValue();
            endOffset += entry1.getIntValue();
          }
        }

        labelText.insert(startOffset, tag);
        labelText.insert(endOffset, endTag);

        faultMap.put(startOffset, tag.length());
      }
      return labelText.toString();
    }
  }

  private static String getOpeningTag(@NotNull String value) {
    return "<" + value + ">";
  }

  private static String getClosingTag(@NotNull String value) {
    int index = value.indexOf(' ');
    return "</" + (0 <= index ? value.substring(0, index) : value) + ">";
  }

  private static String getTagValue(@NotNull ParameterInfoUIContextEx.Flag flag) {
    if (flag == ParameterInfoUIContextEx.Flag.HIGHLIGHT) return "b color=" + ColorUtil.toHex(HIGHLIGHTED_COLOR);
    if (flag == ParameterInfoUIContextEx.Flag.DISABLE) return "font color=" + ColorUtil.toHex(DISABLED_COLOR);
    if (flag == ParameterInfoUIContextEx.Flag.STRIKEOUT) return "strike";
    throw new IllegalArgumentException("flag=" + flag);
  }
}
