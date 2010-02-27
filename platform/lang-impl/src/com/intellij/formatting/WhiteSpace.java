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

package com.intellij.formatting;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormattingDocumentModelImpl;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;

class WhiteSpace {
  private final int myStart;
  private int myEnd;

  private int mySpaces;
  private int myIndentSpaces;

  private CharSequence myInitial;
  private int myFlags;

  private static final byte FIRST = 1;
  private static final byte SAFE = 0x2;
  private static final byte KEEP_FIRST_COLUMN = 0x4;
  private static final byte LINE_FEEDS_ARE_READ_ONLY = 0x8;
  private static final byte READ_ONLY = 0x10;
  private static final byte CONTAINS_LF_INITIALLY = 0x20;
  private static final byte CONTAINS_SPACES_INITIALLY = 0x40;
  private static final int LF_COUNT_SHIFT = 7;
  private static final int MAX_LF_COUNT = 1 << 24;
  @NonNls private static final String CDATA_START = "<![CDATA[";
  @NonNls private static final String CDATA_END = "]]>";

  public WhiteSpace(int startOffset, boolean isFirst) {
    myStart = startOffset;
    myEnd = startOffset;
    setIsFirstWhiteSpace(isFirst);
  }

  public void append(int newEndOffset, FormattingDocumentModel model, CodeStyleSettings.IndentOptions options) {
    final int oldEndOffset = myEnd;
    if (newEndOffset == oldEndOffset) return;
    if (myStart >= newEndOffset) {
      InitialInfoBuilder.assertInvalidRanges(myStart,
        newEndOffset,
        model,
        "some block intersects with whitespace"
      );
    }

    myEnd = newEndOffset;
    TextRange range = new TextRange(myStart, myEnd);
    myInitial = model.getText(range);

    if (!coveredByBlock(model)) {
      InitialInfoBuilder.assertInvalidRanges(myStart,
        myEnd,
        model,
        "nonempty text is not covered by block"
      );
    }

    final int tabsize = options.TAB_SIZE;
    for (int i = oldEndOffset - myStart; i < newEndOffset - myStart; i++) {
      switch (myInitial.charAt(i)) {
        case '\n':
          setLineFeeds(getLineFeeds() + 1);
          mySpaces = 0;
          myIndentSpaces = 0;
          break;
        case ' ':
          mySpaces++;
          break;
        case '\t':
          myIndentSpaces += tabsize;
          break;
      }
    }

    if (getLineFeeds() > 0) myFlags |= CONTAINS_LF_INITIALLY;
    else myFlags &= ~CONTAINS_LF_INITIALLY;

    final int totalSpaces = getTotalSpaces();
    if (totalSpaces > 0) myFlags |= CONTAINS_SPACES_INITIALLY;
    else myFlags &=~ CONTAINS_SPACES_INITIALLY;
  }

  private boolean coveredByBlock(final FormattingDocumentModel model) {
    if (myInitial == null) return true;
    String s = myInitial.toString().trim();
    if (s.length() == 0) return true;
    if (!(model instanceof FormattingDocumentModelImpl)) return false;
    PsiFile psiFile = ((FormattingDocumentModelImpl)model).getFile();
    if (psiFile == null) return false;
    PsiElement start = psiFile.findElementAt(myStart);
    PsiElement end = psiFile.findElementAt(myEnd-1);
    if (s.startsWith(CDATA_START)) s = s.substring(CDATA_START.length());
    if (s.endsWith(CDATA_END)) s = s.substring(0, s.length() - CDATA_END.length());
    s = s.trim();
    if (s.length() == 0) return true;
    return start == end && start instanceof PsiWhiteSpace; // there maybe non-white text inside CDATA-encoded injected elements
  }

  public String generateWhiteSpace(CodeStyleSettings.IndentOptions options) {
    StringBuilder buffer = new StringBuilder();
    StringUtil.repeatSymbol(buffer, '\n', getLineFeeds());

    repeatTrailingSymbols(options, buffer, myIndentSpaces, mySpaces);

    return buffer.toString();
  }

  private static void repeatTrailingSymbols(final CodeStyleSettings.IndentOptions options,
                                     final StringBuilder buffer,
                                     final int indentSpaces,
                                     final int spaces) {
    if (options.USE_TAB_CHARACTER) {
      if (options.SMART_TABS) {
        int tabCount = indentSpaces / options.TAB_SIZE;
        int leftSpaces = indentSpaces - tabCount * options.TAB_SIZE;
        StringUtil.repeatSymbol(buffer, '\t', tabCount);
        StringUtil.repeatSymbol(buffer, ' ', leftSpaces + spaces);
      }
      else {
        int size = spaces + indentSpaces;
        while (size > 0) {
          if (size >= options.TAB_SIZE) {
            buffer.append('\t');
            size -= options.TAB_SIZE;
          }
          else {
            buffer.append(' ');
            size--;
          }
        }
      }
    }
    else {
      StringUtil.repeatSymbol(buffer, ' ', spaces + indentSpaces);
    }
  }

  public void setSpaces(final int spaces, final int indent) {
    performModification(new Runnable() {
      public void run() {
        if (!isKeepFirstColumn() || (myFlags & CONTAINS_SPACES_INITIALLY) != 0) {
          mySpaces = spaces;
          myIndentSpaces = indent;
        }
      }
    });
  }

  private boolean doesNotContainAnySpaces() {
    return getTotalSpaces() == 0 && getLineFeeds() == 0;
  }

  public int getStartOffset() {
    return myStart;
  }
  
  public int getEndOffset() {
    return myEnd;
  }

  private void performModification(Runnable action) {
    if (isIsReadOnly()) return;
    final boolean before = doesNotContainAnySpaces();
    final int lineFeedsBefore = getLineFeeds();
    action.run();
    if (isLineFeedsAreReadOnly()) {
      setLineFeeds(lineFeedsBefore);
    }
    if (isIsSafe()) {
      final boolean after = doesNotContainAnySpaces();
      if (before && !after) {
        mySpaces = 0;
        myIndentSpaces = 0;
        setLineFeeds(0);
      }
      else if (!before && after) {
        mySpaces = 1;
        myIndentSpaces = 0;
      }
    }
  }

  public void arrangeSpaces(final SpacingImpl spaceProperty) {
    performModification(new Runnable() {
      public void run() {
        if (spaceProperty != null) {
          if (getLineFeeds() == 0) {
            if (getTotalSpaces() < spaceProperty.getMinSpaces()) {
              setSpaces(spaceProperty.getMinSpaces(), 0);
            }
            if (getTotalSpaces() > spaceProperty.getMaxSpaces()) {
              setSpaces(spaceProperty.getMaxSpaces(), 0);
            }
          }
        }
      }
    });


  }

  public void arrangeLineFeeds(final SpacingImpl spaceProperty, final FormatProcessor formatProcessor) {
    performModification(new Runnable() {
      public void run() {
        if (spaceProperty != null) {
          spaceProperty.refresh(formatProcessor);

          if (spaceProperty.getMinLineFeeds() >= 0 && getLineFeeds() < spaceProperty.getMinLineFeeds()) {
            setLineFeeds(spaceProperty.getMinLineFeeds());
          }
          if (getLineFeeds() > 0) {
            if (spaceProperty.getKeepBlankLines() > 0) {
              if (getLineFeeds() >= spaceProperty.getKeepBlankLines() + 1) {
                setLineFeeds(spaceProperty.getKeepBlankLines() + 1);
              }
            }
            else {
              if (getLineFeeds() > spaceProperty.getMinLineFeeds()) {
                if (spaceProperty.shouldKeepLineFeeds()) {
                  setLineFeeds(Math.max(spaceProperty.getMinLineFeeds(), 1));
                }
                else {
                  setLineFeeds(spaceProperty.getMinLineFeeds());
                  if (getLineFeeds() == 0) mySpaces = 0;
                }
              }
            }
            if (getLineFeeds() == 1 && !spaceProperty.shouldKeepLineFeeds() && spaceProperty.getMinLineFeeds() == 0) {
              setLineFeeds(0);
              mySpaces = 0;
            }

            if (getLineFeeds() > 0 && getLineFeeds() < spaceProperty.getPrefLineFeeds()) {
              setLineFeeds(spaceProperty.getPrefLineFeeds());
            }
          }

        } else if (isFirst()) {
          setLineFeeds(0);
          mySpaces = 0;
        }
      }
    });

  }

  public boolean containsLineFeeds() {
    return isIsFirstWhiteSpace() || getLineFeeds() > 0;
  }

  public int getTotalSpaces() {
    return mySpaces + myIndentSpaces;
  }

  public void ensureLineFeed() {
    performModification(new Runnable() {
      public void run() {
        if (!containsLineFeeds()) {
          setLineFeeds(1);
          mySpaces = 0;
        }
      }
    });
  }

  public boolean isReadOnly() {
    return isIsReadOnly() || (isIsSafe() && doesNotContainAnySpaces());
  }

  public boolean equalsToString(CharSequence ws) {
    if (myInitial == null) return ws.length() == 0;
    return Comparing.equal(ws,myInitial,true);
  }

  public void setIsSafe(final boolean value) {
    setFlag(SAFE, value);
  }

  private void setFlag(final int mask, final boolean value) {
    if (value) {
      myFlags |= mask;
    }
    else {
      myFlags &= ~mask;
    }
  }

  private boolean getFlag(final int mask) {
    return (myFlags & mask) != 0;
  }

  private boolean isFirst() {
    return isIsFirstWhiteSpace();
  }

  public boolean containsLineFeedsInitially() {
    if (myInitial == null) return false;
    return (myFlags & CONTAINS_LF_INITIALLY) != 0;
  }

  public void removeLineFeeds(final Spacing spacing, final FormatProcessor formatProcessor) {
    performModification(new Runnable() {
      public void run() {
        setLineFeeds(0);
        mySpaces = 0;
        myIndentSpaces = 0;
      }
    });
    arrangeLineFeeds((SpacingImpl)spacing, formatProcessor);
    arrangeSpaces((SpacingImpl)spacing);
  }

  public int getIndentOffset() {
    return myIndentSpaces;
  }

  public int getSpaces() {
    return mySpaces;
  }

  public void setKeepFirstColumn(final boolean b) {
    setFlag(KEEP_FIRST_COLUMN, b);
  }

  public void setLineFeedsAreReadOnly() {
    setLineFeedsAreReadOnly(true);
  }

  public void setReadOnly(final boolean isReadOnly) {
    setIsReadOnly(isReadOnly);
  }

  public boolean isIsFirstWhiteSpace() {
    return getFlag(FIRST);
  }

  public boolean isIsSafe() {
    return getFlag(SAFE);
  }

  public boolean isKeepFirstColumn() {
    return getFlag(KEEP_FIRST_COLUMN);
  }

  public boolean isLineFeedsAreReadOnly() {
    return getFlag(LINE_FEEDS_ARE_READ_ONLY);
  }

  public void setLineFeedsAreReadOnly(final boolean lineFeedsAreReadOnly) {
    setFlag(LINE_FEEDS_ARE_READ_ONLY, lineFeedsAreReadOnly);
  }

  public boolean isIsReadOnly() {
    return getFlag(READ_ONLY);
  }

  public void setIsReadOnly(final boolean isReadOnly) {
    setFlag(READ_ONLY, isReadOnly);
  }

  public void setIsFirstWhiteSpace(final boolean isFirstWhiteSpace) {
    setFlag(FIRST, isFirstWhiteSpace);
  }

  public StringBuilder generateWhiteSpace(final CodeStyleSettings.IndentOptions indentOptions,
                                                  final int offset,
                                                  final IndentInfo indent) {
    final StringBuilder result = new StringBuilder();
    int currentOffset = getStartOffset();
    CharSequence[] lines = getInitialLines();
    int currentLine = 0;
    for (int i = 0; i < lines.length - 1 && currentOffset + lines[i].length() <= offset; i++) {
      result.append(lines[i]);
      currentOffset += lines[i].length();
      result.append('\n');
      currentOffset++;
      currentLine++;
      if (currentOffset == offset) {
        break;
      }
      
    }
    final String newIndentSpaces = indent.generateNewWhiteSpace(indentOptions);
    result.append(newIndentSpaces);
    appendNonWhitespaces(result, lines, currentLine);
    if (currentLine + 1 < lines.length) {
      result.append('\n');
      for (int i = currentLine + 1; i < lines.length - 1; i++) {
        result.append(lines[i]);
        result.append('\n');
      }
      appendNonWhitespaces(result, lines, lines.length-1);
      result.append(lines[lines.length - 1]);

    }
    return result;
  }

  private static void appendNonWhitespaces(StringBuilder result, CharSequence[] lines, int currentLine) {
    if (currentLine != lines.length && !lines[currentLine].toString().matches("\\s*")) {
      result.append(lines[currentLine]);
    }
  }

  private CharSequence[] getInitialLines() {
    if (myInitial == null) return new CharSequence[]{""};
    final ArrayList<CharSequence> result = new ArrayList<CharSequence>();
    StringBuffer currentLine = new StringBuffer();
    for (int i = 0; i < myInitial.length(); i++) {
      final char c = myInitial.charAt(i);
      if (c == '\n') {
        result.add(currentLine);
        currentLine = new StringBuffer();
      }
      else {
        currentLine.append(c);
      }
    }
    result.add(currentLine);
    return result.toArray(new CharSequence[result.size()]);
  }

  public int getIndentSpaces() {
    return myIndentSpaces;
  }

  public int getLength() {
    return myEnd - myStart;
  }

  public final int getLineFeeds() {
    return myFlags >>> LF_COUNT_SHIFT;
  }

  public void setLineFeeds(final int lineFeeds) {
    assert lineFeeds < MAX_LF_COUNT;
    final int flags = myFlags;
    myFlags &= ~0xFFFFFF80;
    myFlags |= (lineFeeds << LF_COUNT_SHIFT);

    assert getLineFeeds() == lineFeeds;
    assert (flags & 0x7F) == (myFlags & 0x7F);
  }

  public TextRange getTextRange() {
    return new TextRange(myStart, myEnd);
  }

  @Override
  public String toString() {
    return "WhiteSpace(" + myStart + "-" + myEnd + " spaces=" + mySpaces + " LFs=" + getLineFeeds() + ")";
  }
}

