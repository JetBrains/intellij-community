// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupElementRenderer;
import com.intellij.codeInsight.lookup.LookupFocusDegree;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.markup.TextAttributesEffectsBuilder;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.icons.RowIcon;
import com.intellij.ui.popup.list.SelectablePanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import it.unimi.dsi.fastutil.ints.Int2BooleanMap;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Stream;

import static com.intellij.codeInsight.lookup.Lookup.LOOKUP_COLOR;

/**
 * @author Konstantin Bulenkov
 */
public final class LookupCellRenderer implements ListCellRenderer<LookupElement> {
  private Icon myEmptyIcon = EmptyIcon.ICON_0;
  private final Font myNormalFont;
  private final Font myBoldFont;
  private final FontMetrics myNormalMetrics;
  private final FontMetrics myBoldMetrics;

  private static final Key<Font> CUSTOM_NAME_FONT = Key.create("CustomLookupElementNameFont");
  private static final Key<Font> CUSTOM_TAIL_FONT = Key.create("CustomLookupElementTailFont");
  private static final Key<Font> CUSTOM_TYPE_FONT = Key.create("CustomLookupElementTypeFont");

  public static final Color BACKGROUND_COLOR =
    JBColor.lazy(() -> Objects.requireNonNullElse(EditorColorsUtil.getGlobalOrDefaultColor(LOOKUP_COLOR),
                                                 JBColor.namedColor("CompletionPopup.background",
                                                                    new JBColor(new Color(235, 244, 254), JBColor.background()))));
  public static final Color MATCHED_FOREGROUND_COLOR = JBColor.namedColor("CompletionPopup.matchForeground", JBUI.CurrentTheme.Link.Foreground.ENABLED);
  public static final Color SELECTED_BACKGROUND_COLOR = JBColor.namedColor("CompletionPopup.selectionBackground", new JBColor(0xc5dffc, 0x113a5c));
  public static final Color SELECTED_NON_FOCUSED_BACKGROUND_COLOR = JBColor.namedColor("CompletionPopup.selectionInactiveBackground", new JBColor(0xE0E0E0, 0x515457));
  private static final Color NON_FOCUSED_MASK_COLOR = JBColor.namedColor("CompletionPopup.nonFocusedMask", Gray._0.withAlpha(0));

  static Insets bodyInsets() {
    return JBUI.insets("CompletionPopup.Body.insets", JBUI.insets(4));
  }

  private static Insets selectionInsets() {
    Insets innerInsets = JBUI.CurrentTheme.CompletionPopup.selectionInnerInsets();
    Insets bodyInsets = bodyInsets();
    //noinspection UseDPIAwareInsets
    return new Insets(innerInsets.top, innerInsets.left + bodyInsets.left, innerInsets.bottom, innerInsets.right + bodyInsets.right);
  }

  public static final SimpleTextAttributes REGULAR_MATCHED_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, MATCHED_FOREGROUND_COLOR);

  private final LookupImpl myLookup;

  private final SimpleColoredComponent myNameComponent;
  private final SimpleColoredComponent myTailComponent;
  private final SimpleColoredComponent myTypeLabel;
  private final LookupPanel myPanel;
  private final Int2BooleanMap mySelected = new Int2BooleanOpenHashMap();

  private static final String ELLIPSIS = "\u2026";
  private int myMaxWidth = -1;
  private volatile int myLookupTextWidth = 50;
  private final Object myWidthLock = ObjectUtils.sentinel("lookup width lock");
  private final Runnable myLookupWidthUpdater;
  private final boolean myShrinkLookup;

  private final AsyncRendering myAsyncRendering;

  private final List<ItemPresentationCustomizer> myCustomizers = ContainerUtil.createLockFreeCopyOnWriteList();

  public LookupCellRenderer(LookupImpl lookup, @NotNull JComponent editorComponent) {
    EditorColorsScheme scheme = lookup.getTopLevelEditor().getColorsScheme();
    myNormalFont = scheme.getFont(EditorFontType.PLAIN);
    myBoldFont = scheme.getFont(EditorFontType.BOLD);

    myLookup = lookup;
    myNameComponent = new MySimpleColoredComponent();
    myNameComponent.setOpaque(false);
    myNameComponent.setIconTextGap(JBUIScale.scale(4));
    myNameComponent.setIpad(JBUI.insetsLeft(1));
    myNameComponent.setMyBorder(null);

    myTailComponent = new MySimpleColoredComponent();
    myTailComponent.setOpaque(false);
    myTailComponent.setIpad(JBInsets.emptyInsets());
    myTailComponent.setBorder(JBUI.Borders.emptyRight(10));

    myTypeLabel = new MySimpleColoredComponent();
    myTypeLabel.setOpaque(false);
    myTypeLabel.setIpad(JBInsets.emptyInsets());
    myTypeLabel.setBorder(JBUI.Borders.emptyRight(10));

    myPanel = new LookupPanel();
    myPanel.add(myNameComponent, BorderLayout.WEST);
    myPanel.add(myTailComponent, BorderLayout.CENTER);
    myPanel.add(myTypeLabel, BorderLayout.EAST);

    myNormalMetrics = myLookup.getTopLevelEditor().getComponent().getFontMetrics(myNormalFont);
    myBoldMetrics = myLookup.getTopLevelEditor().getComponent().getFontMetrics(myBoldFont);
    myAsyncRendering = new AsyncRendering(myLookup);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      // Avoid delay in unit tests
      myLookupWidthUpdater = () -> ApplicationManager.getApplication().invokeLater(this::updateLookupWidthFromVisibleItems);
    } else {
      SingleAlarm alarm = new SingleAlarm(this::updateLookupWidthFromVisibleItems, 50, lookup, Alarm.ThreadToUse.SWING_THREAD,
                                          ModalityState.stateForComponent(editorComponent));
      myLookupWidthUpdater = () -> {
        synchronized (alarm) {
          if (!alarm.isDisposed()) {
            alarm.request();
          }
        }
      };
    }

    myShrinkLookup = Registry.is("ide.lookup.shrink");
  }

  private boolean myIsSelected = false;
  @Override
  public Component getListCellRendererComponent(
      final JList list,
      LookupElement item,
      int index,
      boolean isSelected,
      boolean hasFocus) {

    boolean nonFocusedSelection = isSelected && myLookup.getLookupFocusDegree() == LookupFocusDegree.SEMI_FOCUSED;
    if (!myLookup.isFocused()) {
      isSelected = false;
    }

    myIsSelected = isSelected;
    myPanel.setSelectionColor(nonFocusedSelection ? SELECTED_NON_FOCUSED_BACKGROUND_COLOR :
                              isSelected ? SELECTED_BACKGROUND_COLOR : null);

    int allowedWidth = list.getWidth() - calcSpacing(myNameComponent, myEmptyIcon) - calcSpacing(myTailComponent, null) - calcSpacing(myTypeLabel, null);

    LookupElementPresentation presentation = myAsyncRendering.getLastComputed(item);
    for (ItemPresentationCustomizer customizer : myCustomizers) {
      presentation = customizer.customizePresentation(item, presentation);
    }
    if (presentation.getIcon() != null) {
      setIconInsets(myNameComponent);
    }

    myNameComponent.clear();

    Color itemColor = presentation.getItemTextForeground();
    allowedWidth -= setItemTextLabel(item, itemColor, presentation, allowedWidth);

    myTailComponent.setFont(ObjectUtils.notNull(getCustomFont(item, false, CUSTOM_TAIL_FONT), myNormalFont));
    myTypeLabel.setFont(ObjectUtils.notNull(getCustomFont(item, false, CUSTOM_TYPE_FONT), myNormalFont));
    myNameComponent.setIcon(augmentIcon(myLookup.getTopLevelEditor(), presentation.getIcon(), myEmptyIcon));

    final Color grayedForeground = getGrayedForeground(isSelected);
    myTypeLabel.clear();
    if (allowedWidth > 0) {
      allowedWidth -= setTypeTextLabel(item, grayedForeground, presentation, isSelected ? getMaxWidth() : allowedWidth, isSelected, nonFocusedSelection,
              getRealFontMetrics(item, false, CUSTOM_TYPE_FONT));
    }

    myTailComponent.clear();
    if (isSelected || allowedWidth >= 0) {
      setTailTextLabel(item, isSelected, presentation, grayedForeground, isSelected ? getMaxWidth() : allowedWidth, nonFocusedSelection,
              getRealFontMetrics(item, false, CUSTOM_TAIL_FONT));
    }

    if (mySelected.containsKey(index)) {
      if (!isSelected && mySelected.get(index)) {
        myPanel.setUpdateExtender(true);
      }
    }
    mySelected.put(index, isSelected);

    final double w = myNameComponent.getPreferredSize().getWidth() +
                     myTailComponent.getPreferredSize().getWidth() +
                     myTypeLabel.getPreferredSize().getWidth();

    boolean useBoxLayout = isSelected && w > list.getWidth() && ((JBList<?>)list).getExpandableItemsHandler().isEnabled();
    if (useBoxLayout != myPanel.getLayout() instanceof BoxLayout) {
      myPanel.removeAll();
      if (useBoxLayout) {
        myPanel.setLayout(new BoxLayout(myPanel, BoxLayout.X_AXIS));
        myPanel.add(myNameComponent);
        myPanel.add(myTailComponent);
        myPanel.add(myTypeLabel);
      } else {
        myPanel.setLayout(new BorderLayout());
        myPanel.add(myNameComponent, BorderLayout.WEST);
        myPanel.add(myTailComponent, BorderLayout.CENTER);
        myPanel.add(myTypeLabel, BorderLayout.EAST);
      }
    }

    AccessibleContextUtil.setCombinedName(myPanel, myNameComponent, "", myTailComponent, " - ", myTypeLabel);
    AccessibleContextUtil.setCombinedDescription(myPanel, myNameComponent, "", myTailComponent, " - ", myTypeLabel);
    return myPanel;
  }

  @VisibleForTesting
  public int getLookupTextWidth() {
    return myLookupTextWidth;
  }

  void addPresentationCustomizer(@NotNull ItemPresentationCustomizer customizer) {
    myCustomizers.add(customizer);
  }

  private static int calcSpacing(@NotNull SimpleColoredComponent component, @Nullable Icon icon) {
    Insets iPad = component.getIpad();
    int width = iPad.left + iPad.right;
    Border myBorder = component.getMyBorder();
    if (myBorder != null) {
      Insets insets = myBorder.getBorderInsets(component);
      width += insets.left + insets.right;
    }
    Insets insets = component.getInsets();
    if (insets != null) {
      width += insets.left + insets.right;
    }
    if (icon != null) {
      width += icon.getIconWidth() + component.getIconTextGap();
    }
    return width;
  }

  private int getMaxWidth() {
    if (myMaxWidth < 0) {
      final Point p = myLookup.getComponent().getLocationOnScreen();
      final Rectangle rectangle = ScreenUtil.getScreenRectangle(p);
      myMaxWidth = rectangle.x + rectangle.width - p.x - 111;
    }
    return myMaxWidth;
  }

  private void setTailTextLabel(LookupElement item, boolean isSelected,
                                LookupElementPresentation presentation,
                                Color foreground,
                                int allowedWidth,
                                boolean nonFocusedSelection, FontMetrics fontMetrics) {
    int style = getStyle(presentation.isStrikeout(), false, false);

    for (LookupElementPresentation.TextFragment fragment : presentation.getTailFragments()) {
      if (allowedWidth < 0) {
        return;
      }

      String trimmed = trimLabelText(fragment.text, allowedWidth, fontMetrics);
      @SimpleTextAttributes.StyleAttributeConstant int fragmentStyle = fragment.isItalic() ? style | SimpleTextAttributes.STYLE_ITALIC : style;
      SimpleTextAttributes baseAttributes = new SimpleTextAttributes(fragmentStyle, getTailTextColor(isSelected, fragment, foreground, nonFocusedSelection));
      myTailComponent.append(trimmed, baseAttributes);
      allowedWidth -= getStringWidth(trimmed, fontMetrics);
    }
    renderItemNameDecoration(myTailComponent, presentation.getItemTailDecorations(), item);
  }

  @NlsSafe
  private String trimLabelText(@Nullable String text, int maxWidth, FontMetrics metrics) {
    if (text == null || StringUtil.isEmpty(text)) {
      return "";
    }

    final int strWidth = getStringWidth(text, metrics);
    if (strWidth <= maxWidth || myIsSelected) {
      return text;
    }

    if (getStringWidth(ELLIPSIS, metrics) > maxWidth) {
      return "";
    }

    int insIndex = ObjectUtils.binarySearch(0, text.length(), mid ->{
      final String candidate = text.substring(0, mid) + ELLIPSIS;
      final int width = getStringWidth(candidate, metrics);
      return width <= maxWidth ? -1 : 1;
    });
    int i = Math.max(0,-insIndex-2);

    return text.substring(0, i) + ELLIPSIS;
  }

  private static Color getTypeTextColor(LookupElement item, Color foreground, LookupElementPresentation presentation, boolean selected, boolean nonFocusedSelection) {
    if (nonFocusedSelection) {
      return foreground;
    }

    return presentation.isTypeGrayed() ? getGrayedForeground(selected) : item instanceof EmptyLookupItem ? JBColor.foreground() : foreground;
  }

  private static Color getTailTextColor(boolean isSelected, LookupElementPresentation.TextFragment fragment, Color defaultForeground, boolean nonFocusedSelection) {
    if (nonFocusedSelection) {
      return defaultForeground;
    }

    if (fragment.isGrayed()) {
      return getGrayedForeground(isSelected);
    }

    if (!isSelected) {
      final Color tailForeground = fragment.getForegroundColor();
      if (tailForeground != null) {
        return tailForeground;
      }
    }

    return defaultForeground;
  }

  @SuppressWarnings("unused")
  public static Color getGrayedForeground(boolean isSelected) {
    return UIUtil.getContextHelpForeground();
  }

  private int setItemTextLabel(LookupElement item, final Color foreground, LookupElementPresentation presentation, int allowedWidth) {
    boolean bold = presentation.isItemTextBold();

    Font customItemFont = getCustomFont(item, bold, CUSTOM_NAME_FONT);
    myNameComponent.setFont(customItemFont != null ? customItemFont : bold ? myBoldFont : myNormalFont);
    int style = getStyle(presentation.isStrikeout(), presentation.isItemTextUnderlined(), presentation.isItemTextItalic());

    final FontMetrics metrics = getRealFontMetrics(item, bold, CUSTOM_NAME_FONT);
    final String name = trimLabelText(presentation.getItemText(), allowedWidth, metrics);
    int used = getStringWidth(name, metrics);

    renderItemName(item, foreground, style, name, myNameComponent, presentation.getItemNameDecorations());
    return used;
  }

  private FontMetrics getRealFontMetrics(LookupElement item, boolean bold, Key<Font> key) {
    Font customFont = getCustomFont(item, bold, key);
    if (customFont != null) {
      return myLookup.getTopLevelEditor().getComponent().getFontMetrics(customFont);
    }

    return bold ? myBoldMetrics : myNormalMetrics;
  }

  @SimpleTextAttributes.StyleAttributeConstant
  private static int getStyle(boolean strikeout, boolean underlined, boolean italic) {
    int style = SimpleTextAttributes.STYLE_PLAIN;
    if (strikeout) {
      style |= SimpleTextAttributes.STYLE_STRIKEOUT;
    }
    if (underlined) {
      style |= SimpleTextAttributes.STYLE_UNDERLINE;
    }
    if (italic) {
      style |= SimpleTextAttributes.STYLE_ITALIC;
    }
    return style;
  }

  private void renderItemName(LookupElement item,
                              Color foreground,
                              @SimpleTextAttributes.StyleAttributeConstant int style,
                              @Nls String name,
                              final SimpleColoredComponent nameComponent,
                              @NotNull List<LookupElementPresentation.DecoratedTextRange> itemNameDecorations) {
    final SimpleTextAttributes base = new SimpleTextAttributes(style, foreground);

    final String prefix = item instanceof EmptyLookupItem ? "" : myLookup.itemPattern(item);
    if (prefix.length() > 0) {
      Iterable<TextRange> ranges = getMatchingFragments(prefix, name);
      if (ranges != null) {
        SimpleTextAttributes highlighted = new SimpleTextAttributes(style, MATCHED_FOREGROUND_COLOR);
        SpeedSearchUtil.appendColoredFragments(nameComponent, name, ranges, base, highlighted);
        renderItemNameDecoration(nameComponent, itemNameDecorations, item);
        return;
      }
    }
    nameComponent.append(name, base);
    renderItemNameDecoration(nameComponent, itemNameDecorations, item);
  }

  /**
   * Splits the nameComponent into fragments based on the offsets of the decorated text ranges,
   * then applies the appropriate decorations to each fragment.
   */
  private void renderItemNameDecoration(SimpleColoredComponent nameComponent,
                                               @NotNull List<LookupElementPresentation.DecoratedTextRange> itemNameDecorations,
                                               @NotNull LookupElement item) {
    if (nameComponent.getFragmentCount() == 0 || itemNameDecorations.isEmpty()) {
      return;
    }
    List<Integer> offsetsToSplit = itemNameDecorations.stream()
      .map(decoratedTextRange -> decoratedTextRange.textRange())
      .flatMap(textRange -> Stream.of(textRange.getStartOffset(), textRange.getEndOffset()))
      .sorted()
      .distinct()
      .toList();
    splitSimpleColoredComponentAtLeastByOffsets(nameComponent, offsetsToSplit);

    EditorColorsScheme editorColorsScheme = EditorColorsManager.getInstance().getGlobalScheme();

    SimpleColoredComponent.ColoredIterator iterator = nameComponent.iterator();
    while (iterator.hasNext()) {
      iterator.next();
      List<LookupElementPresentation.LookupItemDecoration> decorations = itemNameDecorations.stream()
        .filter(decoratedTextRange -> decoratedTextRange.textRange().intersectsStrict(iterator.getOffset(), iterator.getEndOffset()))
        .map(LookupElementPresentation.DecoratedTextRange::decoration)
        .toList();
      if (decorations.isEmpty()) {
        continue;
      }
      for (LookupElementPresentation.LookupItemDecoration decoration : decorations) {
        TextAttributes newAttributes = iterator.getTextAttributes().toTextAttributes();
        if (decoration == LookupElementPresentation.LookupItemDecoration.ERROR) {
          Color color = editorColorsScheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES).getEffectColor();
          TextAttributesEffectsBuilder.create().coverWith(EffectType.WAVE_UNDERSCORE, color).applyTo(newAttributes);
          iterator.setTextAttributes(SimpleTextAttributes.fromTextAttributes(newAttributes));
        }

        //must be last
        if (decoration == LookupElementPresentation.LookupItemDecoration.HIGHLIGHT_MATCHED) {
          final String prefix = item instanceof EmptyLookupItem ? "" : myLookup.itemPattern(item);
          String fragment = iterator.getFragment();
          Iterable<TextRange> ranges = getMatchingFragments(prefix, fragment);
          if (ranges != null) {
            SimpleTextAttributes highlighted = new SimpleTextAttributes(iterator.getTextAttributes().getStyle(), MATCHED_FOREGROUND_COLOR);
            int nextStartPoint = 0;
            for (TextRange nextHighlightedRange : ranges) {
              iterator.split(nextHighlightedRange.getStartOffset() - nextStartPoint, iterator.getTextAttributes());
              nextStartPoint = nextHighlightedRange.getStartOffset();
              iterator.split(nextHighlightedRange.getEndOffset() - nextStartPoint, highlighted);
              nextStartPoint = nextHighlightedRange.getEndOffset();
            }
          }
        }
      }
    }
  }

  private static void splitSimpleColoredComponentAtLeastByOffsets(SimpleColoredComponent component, @NotNull List<Integer> offsets) {
    SimpleColoredComponent.ColoredIterator iterator = component.iterator();
    iterator.next();
    for (int offset : offsets) {
      while (iterator.hasNext() && offset >= iterator.getEndOffset()) {
        iterator.next();
      }
      if (offset > iterator.getOffset() && offset < iterator.getEndOffset()) {
        iterator.split(offset - iterator.getOffset(), iterator.getTextAttributes());
      }
    }
  }

  public static FList<TextRange> getMatchingFragments(String prefix, String name) {
    return NameUtil.buildMatcher("*" + prefix).build().matchingFragments(name);
  }

  private int setTypeTextLabel(LookupElement item,
                               Color foreground,
                               final LookupElementPresentation presentation,
                               int allowedWidth,
                               boolean selected, boolean nonFocusedSelection, FontMetrics normalMetrics) {
    final String givenText = presentation.getTypeText();
    final String labelText = trimLabelText(StringUtil.isEmpty(givenText) ? "" : " " + givenText, allowedWidth, normalMetrics);

    int used = getStringWidth(labelText, normalMetrics);

    final Icon icon = presentation.getTypeIcon();
    if (icon != null) {
      myTypeLabel.setIcon(icon);
      used += icon.getIconWidth();
    }

    myTypeLabel.append(labelText);
    myTypeLabel.setForeground(getTypeTextColor(item, foreground, presentation, selected, nonFocusedSelection));
    myTypeLabel.setIconOnTheRight(presentation.isTypeIconRightAligned());
    return used;
  }

  @NotNull
  private static Icon removeVisibilityIfNeeded(@Nullable Editor editor, @NotNull Icon icon, @NotNull Icon standard) {
    if (!Registry.is("ide.completion.show.visibility.icon")) {
      return removeVisibility(editor, icon, standard);
    }
    return icon;
  }

  @NotNull
  private static Icon removeVisibility(@Nullable Editor editor, @NotNull Icon icon, @NotNull Icon standard) {
    if (icon instanceof IconDecorator decoratorIcon) {
      Icon delegateIcon = decoratorIcon.getDelegate();
      if (delegateIcon != null) {
        return decoratorIcon.withDelegate(removeVisibility(editor, delegateIcon, standard));
      }
    }
    else if (icon instanceof RowIcon rowIcon) {
      if (rowIcon.getIconCount() >= 1) {
        Icon firstIcon = rowIcon.getIcon(0);
        if (firstIcon != null) {
          return Registry.is("editor.scale.completion.icons") ?
                 EditorUtil.scaleIconAccordingEditorFont(firstIcon, editor) : firstIcon;
        }
      }
    }
    else if (icon.getIconWidth() > standard.getIconWidth() || icon.getIconHeight() > standard.getIconHeight()) {
      return IconUtil.cropIcon(icon, new Rectangle(standard.getIconWidth(), standard.getIconHeight()));
    }
    return icon;
  }

  public static Icon augmentIcon(@Nullable Editor editor, @Nullable Icon icon, @NotNull Icon standard) {
    if (Registry.is("editor.scale.completion.icons")) {
      standard = EditorUtil.scaleIconAccordingEditorFont(standard, editor);
      icon = EditorUtil.scaleIconAccordingEditorFont(icon, editor);
    }
    if (icon == null) {
      return standard;
    } else if (icon instanceof IconDecorator decoratorIcon) {
      return decoratorIcon.withDelegate(augmentIcon(editor, decoratorIcon.getDelegate(), standard));
    }

    icon = removeVisibilityIfNeeded(editor, icon, standard);

    if (icon.getIconHeight() < standard.getIconHeight() || icon.getIconWidth() < standard.getIconWidth()) {
      final LayeredIcon layeredIcon = new LayeredIcon(2);
      layeredIcon.setIcon(icon, 0, 0, (standard.getIconHeight() - icon.getIconHeight()) / 2);
      layeredIcon.setIcon(standard, 1);
      return layeredIcon;
    }

    return icon;
  }

  @Nullable
  private Font getFontAbleToDisplay(@Nullable String sampleString) {
    if (sampleString == null) return null;

    // assume a single font can display all chars
    Set<Font> fonts = new HashSet<>();
    FontPreferences fontPreferences = myLookup.getFontPreferences();
    for (int i = 0; i < sampleString.length(); i++) {
      fonts.add(ComplementaryFontsRegistry.getFontAbleToDisplay(sampleString.charAt(i), Font.PLAIN, fontPreferences, null).getFont());
    }

    eachFont: for (Font font : fonts) {
      if (font.equals(myNormalFont)) continue;

      for (int i = 0; i < sampleString.length(); i++) {
        if (!font.canDisplay(sampleString.charAt(i))) {
          continue eachFont;
        }
      }
      return font;
    }
    return null;
  }

  @Nullable
  private static Font getCustomFont(LookupElement item, boolean bold, Key<Font> key) {
    Font font = item.getUserData(key);
    return font == null ? null : bold ? font.deriveFont(Font.BOLD) : font;
  }

  /**
   * Update lookup width due to visible in lookup items
   */
  void updateLookupWidthFromVisibleItems() {
    List<LookupElement> visibleItems = myLookup.getVisibleItems();

    int maxWidth = myShrinkLookup ? 0 : myLookupTextWidth;
    for (var item : visibleItems) {
      LookupElementPresentation presentation = myAsyncRendering.getLastComputed(item);

      item.putUserData(CUSTOM_NAME_FONT, getFontAbleToDisplay(presentation.getItemText()));
      item.putUserData(CUSTOM_TAIL_FONT, getFontAbleToDisplay(presentation.getTailText()));
      item.putUserData(CUSTOM_TYPE_FONT, getFontAbleToDisplay(presentation.getTypeText()));

      int itemWidth = updateMaximumWidth(presentation, item);
      if (itemWidth > maxWidth) {
        maxWidth = itemWidth;
      }
    }

    synchronized (myWidthLock) {
      if (myShrinkLookup || maxWidth > myLookupTextWidth) {
        myLookupTextWidth = maxWidth;
        myLookup.requestResize();
        try (AccessToken ignore = SlowOperations.startSection(SlowOperations.RENDERING)) {
          myLookup.refreshUi(false, false);
        }
      }
    }
  }

  void scheduleUpdateLookupWidthFromVisibleItems(){
    myLookupWidthUpdater.run();
  }

  void itemAdded(@NotNull LookupElement element, @NotNull LookupElementPresentation fastPresentation) {
    updateIconWidth(fastPresentation);
    scheduleUpdateLookupWidthFromVisibleItems();
    AsyncRendering.rememberPresentation(element, fastPresentation);

    updateItemPresentation(element);
  }

  void updateItemPresentation(@NotNull LookupElement element){
    LookupElementRenderer<? extends LookupElement> renderer = element.getExpensiveRenderer();
    if (renderer != null) {
      myAsyncRendering.scheduleRendering(element, renderer);
    }
  }

  private void updateIconWidth(LookupElementPresentation p){
    Icon icon = p.getIcon();
    if (icon != null && (icon.getIconWidth() > myEmptyIcon.getIconWidth() || icon.getIconHeight() > myEmptyIcon.getIconHeight())) {
      if (icon instanceof DeferredIcon) {
        icon = ((DeferredIcon)icon).getBaseIcon();
      }
      icon = removeVisibilityIfNeeded(myLookup.getEditor(), icon, myEmptyIcon);

      myEmptyIcon = EmptyIcon.create(Math.max(icon.getIconWidth(), myEmptyIcon.getIconWidth()),
                                     Math.max(icon.getIconHeight(), myEmptyIcon.getIconHeight()));
      setIconInsets(myNameComponent);
    }
  }

  private static void setIconInsets(@NotNull SimpleColoredComponent component) {
    component.setIpad(JBUI.insetsLeft(6));
  }

  private int updateMaximumWidth(LookupElementPresentation p, LookupElement item) {
    updateIconWidth(p);
    return calculateWidth(p, getRealFontMetrics(item, false, CUSTOM_NAME_FONT), getRealFontMetrics(item, true, CUSTOM_NAME_FONT)) +
           calcSpacing(myTailComponent, null) + calcSpacing(myTypeLabel, null);
  }

  int getTextIndent() {
    return myNameComponent.getIpad().left + myEmptyIcon.getIconWidth() + myNameComponent.getIconTextGap();
  }

  private static int calculateWidth(LookupElementPresentation presentation, FontMetrics normalMetrics, FontMetrics boldMetrics) {
    int result;
    if (ExperimentalUI.isNewUI()) {
      Insets insets = selectionInsets();
      result = insets.left + insets.right;
    }
    else {
      result = 0;
    }
    result += getStringWidth(presentation.getItemText(), presentation.isItemTextBold() ? boldMetrics : normalMetrics);
    result += getStringWidth(presentation.getTailText(), normalMetrics);
    final String typeText = presentation.getTypeText();
    if (StringUtil.isNotEmpty(typeText)) {
      result += getStringWidth("W", normalMetrics); // nice tail-type separation
      result += getStringWidth(typeText, normalMetrics);
    }
    result += getStringWidth("W", boldMetrics); //for unforeseen Swing size adjustments
    final Icon typeIcon = presentation.getTypeIcon();
    if (typeIcon != null) {
      result += typeIcon.getIconWidth();
    }
    return result;
  }

  private static int getStringWidth(@Nullable final String text, FontMetrics metrics) {
    if (text != null) {
      return metrics.stringWidth(text);
    }
    return 0;
  }

  private static final class MySimpleColoredComponent extends SimpleColoredComponent {
    private MySimpleColoredComponent() {
      setFocusBorderAroundIcon(true);
    }
  }

  private class LookupPanel extends SelectablePanel {
    boolean myUpdateExtender;
    LookupPanel() {
      setLayout(new BorderLayout());
      setBackground(BACKGROUND_COLOR);
      if (ExperimentalUI.isNewUI()) {
        Insets bodyInsets = bodyInsets();
        setBorder(new EmptyBorder(selectionInsets()));
        //noinspection UseDPIAwareInsets
        setSelectionInsets(new Insets(0, bodyInsets.left, 0, bodyInsets.right));
        setSelectionArc(JBUI.CurrentTheme.Popup.Selection.ARC.get());
      }
    }

    public void setUpdateExtender(boolean updateExtender) {
      myUpdateExtender = updateExtender;
    }

    @Override
    public Dimension getPreferredSize() {
      return UIUtil.updateListRowHeight(super.getPreferredSize());
    }

    @Override
    public void paint(Graphics g) {
      super.paint(g);
      if (NON_FOCUSED_MASK_COLOR.getAlpha() > 0 && !myLookup.isFocused() && myLookup.isCompletion()) {
        g = g.create();
        try {
          g.setColor(NON_FOCUSED_MASK_COLOR);
          g.fillRect(0, 0, getWidth(), getHeight());
        }
        finally {
          g.dispose();
        }
      }
    }
  }

  /**
   * Allows to update element's presentation during completion session.
   * <p>
   * Be careful, the lookup won't be resized according to the changes inside {@link #customizePresentation}.
   */
  @ApiStatus.Internal
  public interface ItemPresentationCustomizer {
    /**
     * Invoked from EDT thread every time lookup element is preparing to be shown. Must be very fast.
     *
     * @return presentation to show
     */
    @NotNull
    LookupElementPresentation customizePresentation(@NotNull LookupElement item,
                                                    @NotNull LookupElementPresentation presentation);
  }

  /**
   * Allows to extend the original icon
   */
  public interface IconDecorator extends Icon {
    /**
     * Returns the original icon
     */
    @Nullable
    Icon getDelegate();

    /**
     * Returns a new decorator with {@code icon} instead of the original icon
     */
    @NotNull
    IconDecorator withDelegate(@Nullable Icon icon);
  }
}
