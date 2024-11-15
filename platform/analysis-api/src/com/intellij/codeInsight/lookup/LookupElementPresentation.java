// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class LookupElementPresentation {
  private Icon myIcon;
  private Icon myTypeIcon;
  private boolean myTypeIconRightAligned;
  private String myItemText;
  private String myTypeText;
  private boolean myStrikeout;
  private Color myItemTextForeground = JBColor.namedColor("CompletionPopup.foreground", JBColor.foreground());
  private boolean myItemTextBold;
  private boolean myItemTextUnderlined;
  private boolean myItemTextItalic;
  private @Nullable List<DecoratedTextRange> myItemNameDecorations;
  private @Nullable List<DecoratedTextRange> myItemTailDecorations;
  private boolean myTypeGrayed;
  private @Nullable List<TextFragment> myTail;
  private volatile boolean myFrozen;

  public void setIcon(@Nullable Icon icon) {
    ensureMutable();
    myIcon = icon;
  }

  public void setItemText(@Nullable String text) {
    ensureMutable();
    myItemText = text;
  }

  public void setStrikeout(boolean strikeout) {
    ensureMutable();
    myStrikeout = strikeout;
  }

  public void setItemTextBold(boolean bold) {
    ensureMutable();
    myItemTextBold = bold;
  }

  public void setItemTextItalic(boolean itemTextItalic) {
    ensureMutable();
    myItemTextItalic = itemTextItalic;
  }

  /**
   * Adds a decoration to a lookup item at a specified text range. It will be applied to the name component
   */
  @ApiStatus.Internal
  public void decorateItemTextRange(@NotNull TextRange textRange, @NotNull LookupItemDecoration decoration) {
    ensureMutable();
    if (myItemNameDecorations == null) {
      myItemNameDecorations = new SmartList<>();
    }
    myItemNameDecorations.add(new DecoratedTextRange(textRange, decoration));
  }

  /**
   * Adds a decoration to a lookup item at a specified text range. It will be applied to the tail component
   */
  @ApiStatus.Internal
  public void decorateTailItemTextRange(@NotNull TextRange textRange, @NotNull LookupItemDecoration decoration) {
    ensureMutable();
    if (myItemTailDecorations == null) {
      myItemTailDecorations = new SmartList<>();
    }
    myItemTailDecorations.add(new DecoratedTextRange(textRange, decoration));
  }

  public void setTailText(@Nullable String text) {
    setTailText(text, false);
  }

  public void clearTail() {
    ensureMutable();
    myTail = null;
  }

  public void appendTailText(@NotNull String text, boolean grayed) {
    appendTailText(new TextFragment(text, grayed, false, null));
  }

  public void appendTailTextItalic(@NotNull String text, boolean grayed) {
    appendTailText(new TextFragment(text, grayed, true, null));
  }

  private void appendTailText(@NotNull TextFragment fragment) {
    ensureMutable();
    if (fragment.text.isEmpty()) return;

    if (myTail == null) {
      myTail = new SmartList<>();
    }
    myTail.add(fragment);
  }

  public void setTailText(@Nullable String text, boolean grayed) {
    clearTail();
    if (text != null) {
      appendTailText(new TextFragment(text, grayed, false, null));
    }
  }

  public void setTailText(@Nullable String text, @Nullable Color foreground) {
    clearTail();
    if (text != null) {
      appendTailText(new TextFragment(text, false, false, foreground));
    }
  }

  public void setTypeText(@Nullable String text) {
    setTypeText(text, null);
  }

  public void setTypeText(@Nullable String text, @Nullable Icon icon) {
    ensureMutable();
    myTypeText = text;
    myTypeIcon = icon;
  }

  /**
   * @deprecated Always returns true. To speed up completion by delaying rendering more expensive parts,
   * implement {@link LookupElement#getExpensiveRenderer()}.
   */
  @Deprecated(forRemoval = true)
  public boolean isReal() {
    return true;
  }

  public @Nullable Icon getIcon() {
    return myIcon;
  }

  public @Nullable Icon getTypeIcon() {
    return myTypeIcon;
  }

  public @Nullable String getItemText() {
    return myItemText;
  }

  @ApiStatus.Internal
  public @NotNull List<DecoratedTextRange> getItemNameDecorations() {
    return myItemNameDecorations == null ? Collections.emptyList() : Collections.unmodifiableList(myItemNameDecorations);
  }

  /**
   * @return decorators for tail
   */
  @ApiStatus.Internal
  public @NotNull List<DecoratedTextRange> getItemTailDecorations() {
    return myItemTailDecorations == null ? Collections.emptyList() : Collections.unmodifiableList(myItemTailDecorations);
  }

  public @NotNull List<TextFragment> getTailFragments() {
    return myTail == null ? Collections.emptyList() : Collections.unmodifiableList(myTail);
  }

  public @Nullable String getTailText() {
    if (myTail == null) return null;
    return StringUtil.join(myTail, fragment -> fragment.text, "");
  }

  public @Nullable String getTypeText() {
    return myTypeText;
  }

  public boolean isStrikeout() {
    return myStrikeout;
  }

  public boolean isItemTextBold() {
    return myItemTextBold;
  }

  public boolean isItemTextItalic() {
    return myItemTextItalic;
  }

  public boolean isItemTextUnderlined() {
    return myItemTextUnderlined;
  }

  public void setItemTextUnderlined(boolean itemTextUnderlined) {
    ensureMutable();
    myItemTextUnderlined = itemTextUnderlined;
  }

  public @NotNull Color getItemTextForeground() {
    return myItemTextForeground;
  }

  public void setItemTextForeground(@NotNull Color itemTextForeground) {
    ensureMutable();
    myItemTextForeground = itemTextForeground;
  }

  public void copyFrom(@NotNull LookupElementPresentation presentation) {
    myIcon = presentation.myIcon;
    myTypeIcon = presentation.myTypeIcon;
    myItemText = presentation.myItemText;

    List<DecoratedTextRange> thatNameDecoration = presentation.myItemNameDecorations;
    myItemNameDecorations = thatNameDecoration == null ? null : new SmartList<>(thatNameDecoration);

    List<DecoratedTextRange> thatTailDecoration = presentation.myItemTailDecorations;
    myItemTailDecorations = thatTailDecoration == null ? null : new SmartList<>(thatTailDecoration);

    List<TextFragment> thatTail = presentation.myTail;
    myTail = thatTail == null ? null : new SmartList<>(thatTail);

    myTypeText = presentation.myTypeText;
    myStrikeout = presentation.myStrikeout;
    myItemTextBold = presentation.myItemTextBold;
    myItemTextItalic = presentation.myItemTextItalic;
    myTypeGrayed = presentation.myTypeGrayed;
    myTypeIconRightAligned = presentation.myTypeIconRightAligned;
    myItemTextUnderlined = presentation.myItemTextUnderlined;
    myItemTextForeground = presentation.myItemTextForeground;
  }

  public boolean isTypeGrayed() {
    return myTypeGrayed;
  }

  public void setTypeGrayed(boolean typeGrayed) {
    ensureMutable();
    myTypeGrayed = typeGrayed;
  }

  private void ensureMutable() {
    if (myFrozen) throw new IllegalStateException("This lookup element presentation can't be changed");
  }

  public boolean isTypeIconRightAligned() {
    return myTypeIconRightAligned;
  }

  public void setTypeIconRightAligned(boolean typeIconRightAligned) {
    ensureMutable();
    myTypeIconRightAligned = typeIconRightAligned;
  }

  public static LookupElementPresentation renderElement(LookupElement element) {
    LookupElementPresentation presentation = new LookupElementPresentation();
    element.renderElement(presentation);
    return presentation;
  }

  /**
   * Disallow any further changes to this presentation object.
   */
  @ApiStatus.Internal
  public void freeze() {
    myFrozen = true;
  }

  @Override
  public String toString() {
    return "LookupElementPresentation{" +
           "itemText='" + myItemText + '\'' +
           ", tail=" + myTail +
           ", typeText='" + myTypeText + '\'' +
           '}';
  }

  public static final class TextFragment {
    public final String text;
    private final boolean myGrayed;
    private final boolean myItalic;
    private final @Nullable Color myFgColor;

    private TextFragment(String text, boolean grayed, boolean italic, @Nullable Color fgColor) {
      this.text = text;
      myGrayed = grayed;
      myItalic = italic;
      myFgColor = fgColor;
    }

    @Override
    public String toString() {
      return "TextFragment{" +
             "text='" + text + '\'' +
             (myGrayed ? ", grayed" : "") +
             (myItalic ? ", italic" : "") +
             (myFgColor != null ? ", fgColor=" + myFgColor : "") +
             '}';
    }

    public boolean isGrayed() {
      return myGrayed;
    }

    public boolean isItalic() {
      return myItalic;
    }

    public @Nullable Color getForegroundColor() {
      return myFgColor;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof TextFragment fragment)) return false;
      return myGrayed == fragment.myGrayed &&
             myItalic == fragment.myItalic &&
             Objects.equals(text, fragment.text) &&
             Objects.equals(myFgColor, fragment.myFgColor);
    }

    @Override
    public int hashCode() {
      return Objects.hash(text, myGrayed, myItalic, myFgColor);
    }
  }

  /**
   * An enumeration that defines possible decorations for the Item element of a presentation.
   * These decorations are used to indicate additional information about the item being presented.
   */
  @ApiStatus.Internal
  public enum LookupItemDecoration {
    /**
     * Indicates that the corresponding part of the item text will not be compilable right upon the insertion.
     */
    ERROR,
    /**
     * Indicates that some parts of the specified range will be highlighted according to an item pattern.
     */
    HIGHLIGHT_MATCHED,

    /**
     * Will be gray
     */
    GRAY
  }

  /**
   * Range of text with an associated decoration {@link LookupItemDecoration}.
   */
  @ApiStatus.Internal
  public record DecoratedTextRange(TextRange textRange, LookupItemDecoration decoration) {
  }
}
