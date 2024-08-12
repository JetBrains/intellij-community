// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.UISettings;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.lang.parameterInfo.ParameterInfoUIContextEx;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.Function;
import com.intellij.util.SlowOperations;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.ui.*;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import com.intellij.xml.util.XmlStringUtil;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntMaps;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.codeWithMe.ClientIdKt.isForeignClientOnServer;

@VisibleForTesting
public final class ParameterInfoComponent extends JPanel {

  private final JPanel myMainPanel;
  private OneElementComponent[] myPanels;
  private JLabel myShortcutLabel;
  private JPanel myBottomPanel;
  private JComponent myCustomBottomComponent;
  private final JLabel myDumbLabel = new JLabel(IdeBundle.message("dumb.mode.results.might.be.incomplete"));
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

  private int myWidthLimit = 500;
  private static final int myMaxWrappableLengthLimit = 1000;
  private final int myMaxVisibleRows = Registry.intValue("parameter.info.max.visible.rows");

  private static final Comparator<TextRange> TEXT_RANGE_COMPARATOR = (o1, o2) -> {
    int endResult = Integer.compare(o2.getEndOffset(), o1.getEndOffset());
    return endResult == 0 ? Integer.compare(o1.getStartOffset(), o2.getStartOffset()) : endResult;
  };

  private final ParameterInfoControllerData myParameterInfoControllerData;
  private final Editor myEditor;
  private final boolean myRequestFocus;

  private final boolean mySimpleDesignMode = ExperimentalUI.isNewUI() && !ApplicationManager.getApplication().isUnitTestMode();

  private boolean mySetup;

  @TestOnly
  public static ParameterInfoUIContextEx createContext(Object[] objects, @NotNull Editor editor, @NotNull ParameterInfoHandler handler, int currentParameterIndex) {
    return createContext(objects, editor, handler, currentParameterIndex, null);
  }

  @TestOnly
  public static ParameterInfoUIContextEx createContext(Object[] objects, @NotNull Editor editor, @NotNull ParameterInfoHandler handler, int currentParameterIndex, @Nullable PsiElement parameterOwner) {
    @SuppressWarnings("unchecked")
    ParameterInfoControllerData dataObject = new ParameterInfoControllerData(handler);
    dataObject.setDescriptors(objects);
    dataObject.setCurrentParameterIndex(currentParameterIndex);
    dataObject.setParameterOwner(parameterOwner);
    final ParameterInfoComponent infoComponent = new ParameterInfoComponent(dataObject, editor);
    return infoComponent.new MyParameterContext(false);
  }

  private ParameterInfoComponent(ParameterInfoControllerData parameterInfoControllerData, Editor editor) {
    this(parameterInfoControllerData, editor, false, false);
  }

  ParameterInfoComponent(ParameterInfoControllerData parameterInfoControllerData, Editor editor,
                         boolean requestFocus, boolean allowSwitchLabel) {
    super(new BorderLayout());
    myParameterInfoControllerData = parameterInfoControllerData;
    myEditor = editor;
    myRequestFocus = requestFocus;

    if (!ApplicationManager.getApplication().isUnitTestMode()
        && !ApplicationManager.getApplication().isHeadlessEnvironment()
        && !isForeignClientOnServer()) { //don't access ui for the foreign cwm clientIds
      JComponent editorComponent = editor.getComponent();
      JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();
      myWidthLimit = layeredPane.getWidth();
    }

    NORMAL_FONT = editor != null && Registry.is("parameter.info.editor.font")
                  ? editor.getColorsScheme().getFont(EditorFontType.PLAIN)
                  : StartupUiUtil.getLabelFont();
    BOLD_FONT = editor != null && Registry.is("parameter.info.editor.font")
                ? editor.getColorsScheme().getFont(EditorFontType.BOLD)
                : NORMAL_FONT.deriveFont(Font.BOLD);

    if (mySimpleDesignMode) {
      setOpaque(false);
    }
    else {
      setBackground(BACKGROUND);
    }

    myMainPanel = new JPanel(new GridBagLayout());
    myMainPanel.setOpaque(!mySimpleDesignMode);
    setPanels();

    if (myRequestFocus) {
      AccessibleContextUtil.setName(this, CodeInsightBundle.message("accessible.name.parameter.info.press.tab"));
    }
    myBottomPanel = new JPanel(new VerticalLayout(5));
    myBottomPanel.setOpaque(false);
    add(myBottomPanel, BorderLayout.SOUTH);

    myDumbLabel.setForeground(CONTEXT_HELP_FOREGROUND);
    myDumbLabel.setIcon(AllIcons.General.Warning);
    if (mySimpleDesignMode) {
      myDumbLabel.setBorder(JBUI.Borders.emptyTop(12));
      myBottomPanel.add(myDumbLabel);
    }
    else {
      myDumbLabel.setBorder(new CompoundBorder(JBUI.Borders.customLine(SEPARATOR_COLOR, 0, 0, 1, 0), JBUI.Borders.empty(2, 10, 6, 10)));
      add(myDumbLabel, BorderLayout.NORTH);
    }

    final JScrollPane pane = ScrollPaneFactory.createScrollPane(myMainPanel, true);
    pane.setOpaque(!mySimpleDesignMode);
    pane.getViewport().setOpaque(!mySimpleDesignMode);
    add(pane, BorderLayout.CENTER);

    myAllowSwitchLabel = allowSwitchLabel && !(editor instanceof EditorWindow);
    setShortcutLabel();
    setCustomBottomComponent();
  }

  private void setPanels() {
    myMainPanel.removeAll();
    myPanels = new OneElementComponent[myParameterInfoControllerData.getDescriptors().length];
    int lineGap = UISettings.getInstance().getCompactMode() ? 4 : 8;
    for (int i = 0; i < myParameterInfoControllerData.getDescriptors().length; i++) {
      myPanels[i] = new OneElementComponent();
      myMainPanel.add(myPanels[i], new GridBagConstraints(0, i, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                                          i > 0 && mySimpleDesignMode ? new JBInsets(lineGap, 0, 0, 0) : JBInsets.emptyInsets(), 0, 0));
    }
  }

  private void setShortcutLabel() {
    if (myShortcutLabel != null) {
      myBottomPanel.remove(myShortcutLabel);
    }

    String upShortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_METHOD_OVERLOAD_SWITCH_UP);
    String downShortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_METHOD_OVERLOAD_SWITCH_DOWN);
    if (!myAllowSwitchLabel ||
        myParameterInfoControllerData.getDescriptors().length <= 1 ||
        !myParameterInfoControllerData.getHandler().supportsOverloadSwitching() ||
        upShortcut.isEmpty() && downShortcut.isEmpty()) {
      myShortcutLabel = null;
    }
    else {
      myShortcutLabel = new JLabel(
        upShortcut.isEmpty() || downShortcut.isEmpty()
        ? CodeInsightBundle.message("parameter.info.switch.overload.shortcuts.single", upShortcut.isEmpty() ? downShortcut : upShortcut)
        : CodeInsightBundle.message("parameter.info.switch.overload.shortcuts", upShortcut, downShortcut));
      myShortcutLabel.setForeground(CONTEXT_HELP_FOREGROUND);
      if (mySimpleDesignMode) {
        myShortcutLabel.setBorder(JBUI.Borders.emptyTop(10));
      }
      else {
        Font labelFont = StartupUiUtil.getLabelFont();
        myShortcutLabel.setFont(labelFont.deriveFont(labelFont.getSize2D() - (SystemInfo.isWindows ? 1 : 2)));
        myShortcutLabel.setBorder(JBUI.Borders.empty(6, 10, 0, 10));
      }
      myBottomPanel.add(myShortcutLabel);
    }
  }

  private void setCustomBottomComponent() {
    if (myCustomBottomComponent != null) {
      myBottomPanel.remove(myCustomBottomComponent);
    }

    myCustomBottomComponent = myParameterInfoControllerData.getHandler().createBottomComponent();
    if (myCustomBottomComponent != null) {
      myBottomPanel.add(myCustomBottomComponent);
    }
  }

  void fireDescriptorsWereSet() {
    setPanels();
    setShortcutLabel();
    setCustomBottomComponent();
  }

  @Override
  public Dimension getPreferredSize() {
    final Dimension preferredSize = super.getPreferredSize();
    int panelsHeight = Stream.of(myPanels).filter(Component::isVisible).mapToInt(panel -> panel.getPreferredSize().height).sum();
    int visibleRowsHeight = getFontMetrics(BOLD_FONT).getHeight() * myMaxVisibleRows;
    if (panelsHeight <= visibleRowsHeight) {
      return preferredSize;
    }
    else {
      return new Dimension(preferredSize.width, visibleRowsHeight);
    }
  }

  @Override
  public String toString() {
    return Stream.of(myPanels)
      .filter(Component::isVisible)
      .map(c -> c + (c.getBorder() == BOTTOM_BORDER ? "\n-" : ""))
      .collect(Collectors.joining("\n"));
  }

  public boolean isSetup() {
    return mySetup;
  }

  final class MyParameterContext implements ParameterInfoUIContextEx {
    private final boolean mySingleParameterInfo;
    private int i;
    private Function<? super String, String> myEscapeFunction;
    private final ParameterInfoControllerBase.Model result = new ParameterInfoControllerBase.Model();
    private boolean isVisible = true;

    MyParameterContext(boolean singleParameterInfo) {
      mySingleParameterInfo = singleParameterInfo;
    }

    @Override
    public String setupUIComponentPresentation(@NlsContexts.Label String text,
                                               int highlightStartOffset,
                                               int highlightEndOffset,
                                               boolean isDisabled,
                                               boolean strikeout,
                                               boolean isDisabledBeforeHighlight,
                                               Color background) {
      mySetup = true;
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
      ParameterInfoControllerBase.SignatureItem item = new ParameterInfoControllerBase.SignatureItem(plainLine.toString(), strikeout, isDisabled,
                                                                                                     startOffsets, endOffsets);
      result.signatures.add(item);

      final String resultedText =
        myPanels[i].setup(text, myEscapeFunction, highlightStartOffset, highlightEndOffset, isDisabled, strikeout,
                          isDisabledBeforeHighlight, background);
      if (!mySimpleDesignMode) {
        myPanels[i].setBorder(isLastParameterOwner() || isSingleParameterInfo() ? EMPTY_BORDER : BOTTOM_BORDER);
      }
      return resultedText;
    }

    @Override
    public void setupRawUIComponentPresentation(@NlsContexts.Label String htmlText) {
      mySetup = true;
      ParameterInfoControllerBase.RawSignatureItem item = new ParameterInfoControllerBase.RawSignatureItem(htmlText);

      result.current = getCurrentParameterIndex();
      result.signatures.add(item);

      myPanels[i].setup(htmlText, getDefaultParameterColor());
      if (!mySimpleDesignMode) {
        myPanels[i].setBorder(isLastParameterOwner() || isSingleParameterInfo() ? EMPTY_BORDER : BOTTOM_BORDER);
      }
    }

    @Override
    public String setupUIComponentPresentation(final String[] texts, final EnumSet<Flag>[] flags, final Color background) {
      mySetup = true;
      final String resultedText = myPanels[i].setup(result, texts, myEscapeFunction, flags, background);
      if (!mySimpleDesignMode) {
        myPanels[i].setBorder(isLastParameterOwner() || isSingleParameterInfo() ? EMPTY_BORDER : BOTTOM_BORDER);
      }
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

    @Override
    public void setUIComponentVisible(boolean visible) {
      isVisible = visible;
    }

    @Override
    public boolean isUIComponentVisible() {
      return isVisible;
    }

    public boolean isLastParameterOwner() {
      return i == myPanels.length - 1;
    }

    @Override
    public int getCurrentParameterIndex() {
      return myParameterInfoControllerData.getCurrentParameterIndex();
    }

    @Override
    public PsiElement getParameterOwner() {
      return myParameterInfoControllerData.getParameterOwner();
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
      return myParameterInfoControllerData.getDescriptors()[i].equals(myParameterInfoControllerData.getHighlighted());
    }

    @Override
    public Color getDefaultParameterColor() {
      return mySingleParameterInfo || !isHighlighted() ? BACKGROUND : HIGHLIGHTED_BACKGROUND;
    }
  }

  ParameterInfoControllerBase.Model update(boolean singleParameterInfo) {
    MyParameterContext context = new MyParameterContext(singleParameterInfo);

    int highlightedComponentIdx = -1;
    Object[] descriptors = myParameterInfoControllerData.getDescriptors();
    for (int i = 0; i < descriptors.length; i++) {
      context.i = i;
      final Object o = descriptors[i];

      boolean isHighlighted = descriptors[i].equals(myParameterInfoControllerData.getHighlighted());
      if (isHighlighted) {
        context.result.highlightedSignature = i;
      }
      if (singleParameterInfo && descriptors.length > 1 && !context.isHighlighted()) {
        setVisible(i, false);
      }
      else {
        setVisible(i, true);
        DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
          try (AccessToken ignore = SlowOperations.knownIssue("IDEA-305563, EA-819694")) {
            myParameterInfoControllerData.getHandler().updateUI(o, context);
          }
        });

        setVisible(i, context.isUIComponentVisible());

        // ensure that highlighted element is visible
        if (context.isHighlighted()) {
          highlightedComponentIdx = i;
        }
      }
    }

    if (myShortcutLabel != null) myShortcutLabel.setVisible(!singleParameterInfo);

    if (myCustomBottomComponent != null) {
      myParameterInfoControllerData.getHandler().updateBottomComponent(myCustomBottomComponent);
    }

    updateLabels();

    if (highlightedComponentIdx != -1) {
      myMainPanel.scrollRectToVisible(new Rectangle()); // hack to validate component tree synchronously
      myMainPanel.scrollRectToVisible(myPanels[highlightedComponentIdx].getBounds());
    }

    var project = myEditor.getProject();
    myDumbLabel.setVisible(project != null && DumbService.isDumb(project));

    return context.result;
  }

  private void updateLabels() {
    if (!mySimpleDesignMode) {
      return;
    }

    int length = myPanels.length;

    if (length == 1) {
      OneElementComponent panel = myPanels[0];
      int count = panel.getComponentCount();

      panel.myShowSelection = false;

      for (int i = 0; i < count; i++) {
        OneLineComponent line = (OneLineComponent)panel.getComponent(i);
        line.myLabel.setIcon(null);
        line.myLabel.setIconTextGap(0);
      }
    }
    else {
      for (OneElementComponent panel : myPanels) {
        if (panel.myShowSelection) {
          int count = panel.getComponentCount();
          if (count > 1) {
            for (int i = 1; i < count; i++) {
              OneLineComponent line = (OneLineComponent)panel.getComponent(i);
              line.myLabel.setIcon(EmptyIcon.ICON_16);
            }
          }
        }
      }
    }
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

  private final class OneElementComponent extends JPanel {
    private boolean myShowSelection;

    OneElementComponent() {
      super(new GridBagLayout());
      setOpaque(!mySimpleDesignMode);
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
            new GridBagConstraints(0, i, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBInsets.emptyInsets(), 0, 0));
      }
      return (OneLineComponent)getComponent(index);
    }

    private void trimComponents(int count) {
      for (int i = getComponentCount() - 1; i >= count; i--) {
        remove(i);
      }
    }

    private void setup(@NlsContexts.Label String htmlText, Color background) {
      configureColor(background);
      getOneLineComponent(0).doSetup(htmlText, background);
      trimComponents(1);
    }

    private String setup(@NlsContexts.Label String text,
                         Function<? super String, String> escapeFunction,
                         int highlightStartOffset,
                         int highlightEndOffset,
                         boolean isDisabled,
                         boolean strikeout,
                         boolean isDisabledBeforeHighlight,
                         Color background) {
      StringBuilder buf = new StringBuilder(text.length());
      configureColor(background);

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

    @Contract(pure = true)
    private static String escapeString(String line, Function<? super String, String> escapeFunction) {
      line = XmlStringUtil.escapeString(line);
      return escapeFunction == null ? line : escapeFunction.fun(line);
    }

    public @NlsContexts.Label String setup(final ParameterInfoControllerBase.Model result,
                        final String @NlsContexts.Label [] texts,
                        Function<? super String, String> escapeFunction,
                        final EnumSet<ParameterInfoUIContextEx.Flag>[] flags,
                        final Color background) {
      @NlsContexts.Label StringBuilder buf = new StringBuilder();
      configureColor(background);
      int index = 0;
      int curOffset = 0;
      final List<Integer> startOffsets = new ArrayList<>();
      final List<Integer> endOffsets = new ArrayList<>();
      TreeMap<TextRange, ParameterInfoUIContextEx.Flag> flagsMap = new TreeMap<>(TEXT_RANGE_COMPARATOR);
      StringBuilder fullLine = new StringBuilder();
      @NlsContexts.Label StringBuilder line = new StringBuilder();
      for (int i = 0; i < texts.length; i++) {
        String paramText = escapeString(texts[i], escapeFunction);
        if (paramText == null) break;
        FontMetrics fontMetrics = getFontMetrics(BOLD_FONT);
        if (fontMetrics.stringWidth(line + texts[i]) >= myMaxWrappableLengthLimit) {
          OneLineComponent component = getOneLineComponent(index);
          buf.append(component.setup(escapeString(line.toString(), escapeFunction), flagsMap, background));
          index += 1;
          flagsMap.clear();
          curOffset = 0;
          line = new StringBuilder();
        }
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
      }
      ParameterInfoControllerBase.SignatureItem item = new ParameterInfoControllerBase.SignatureItem(fullLine.toString(), false, false,
                                                                                                     startOffsets, endOffsets);
      result.signatures.add(item);
      OneLineComponent component = getOneLineComponent(index);
      buf.append(component.setup(escapeString(line.toString(), escapeFunction), flagsMap, background));
      trimComponents(index + 1);
      return buf.toString();
    }

    private void configureColor(Color background) {
      if (mySimpleDesignMode) {
        myShowSelection = background != BACKGROUND;
      }
      else {
        myShowSelection = false;
        setBackground(background);
      }
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (!myShowSelection) {
        return;
      }

      Graphics2D g2 = (Graphics2D)g.create();

      try {
        g2.setColor(JBUI.CurrentTheme.Editor.Tooltip.SELECTION_BACKGROUND);
        GraphicsUtil.setupAAPainting(g2);
        int arc = JBUI.scale(8);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
      }
      finally {
        g2.dispose();
      }
    }
  }

  private final class OneLineComponent extends JPanel {
    JLabel myLabel = new JLabel("", SwingConstants.LEFT);

    private OneLineComponent() {
      super(new GridBagLayout());
      setOpaque(!mySimpleDesignMode);
      myLabel.setOpaque(!mySimpleDesignMode);
      myLabel.setFont(NORMAL_FONT);
      if (mySimpleDesignMode) {
        myLabel.setBorder(JBUI.Borders.empty(2, 6, 2, 8));
      }
      if (myRequestFocus) {
        myLabel.setFocusable(true);
      }

      add(myLabel, new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.NONE, JBInsets.emptyInsets(), 0, 0));
    }

    @Override
    public String toString() {
      return myLabel.getText();
    }

    private String setup(@NlsContexts.Label String text,
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
    private String setup(@NotNull @NlsContexts.Label String text, @NotNull TreeMap<TextRange, ParameterInfoUIContextEx.Flag> flagsMap,
                         @NotNull Color background) {
      if (flagsMap.isEmpty()) {
        return doSetup(text, background);
      }
      else {
        String labelText = buildLabelText(text, flagsMap);
        return doSetup(labelText, background);
      }
    }

    private String doSetup(@NotNull @NlsContexts.Label String text, @NotNull Color background) {
      if (mySimpleDesignMode) {
        if (background != BACKGROUND) {
          Icon icon = LafIconLookup.getIcon("checkmark");
          myLabel.setIcon(new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
              icon.paintIcon(c, g, x, myLabel.getInsets().top + JBUI.scale(1));
            }

            @Override
            public int getIconWidth() {
              return icon.getIconWidth();
            }

            @Override
            public int getIconHeight() {
              return icon.getIconHeight();
            }
          });
        }
        else {
          myLabel.setIcon(EmptyIcon.ICON_16);
        }
        myLabel.setIconTextGap(JBUI.scale(4));
      }
      else {
        myLabel.setBackground(background);
        setBackground(background);
      }

      myLabel.setForeground(FOREGROUND);

      myLabel.setText(XmlStringUtil.wrapInHtml(text));
      return myLabel.getText();
    }

    // flagsMap is supposed to use TEXT_RANGE_COMPARATOR
    @Contract(pure = true)
    private static String buildLabelText(final @NotNull String text,
                                         final @NotNull TreeMap<TextRange, ParameterInfoUIContextEx.Flag> flagsMap) {
      final StringBuilder labelText = new StringBuilder(text);
      final Int2IntMap faultMap = new Int2IntOpenHashMap();

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
