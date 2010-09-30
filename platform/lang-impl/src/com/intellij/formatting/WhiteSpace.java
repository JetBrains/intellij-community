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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormattingDocumentModelImpl;

import java.util.ArrayList;

/**
 * Object-level representation of continuous white space at document. Either line feed or tabulation or space is considered to be <code>'white-space'</code>.
 * I.e. document text fragment like {@code '\t   \t\t\n\t   \t'}  may be considered as a continuous white space and may be represented as a {@link WhiteSpace} object.
 * <p/>
 * Provides number of properties that describe encapsulated continuous white space:
 * <ul>
 *   <li>{@link #getLineFeeds() lineFeeds};</li>
 *   <li>{@link #getSpaces() spaces};</li>
 *   <li>{@link #getIndentSpaces()};</li>
 * </ul>
 * <p/>
 * Provides ability to build string representation of the managed settings taking into consideration user settings for tabulation vs white space usage, tabulation
 * size etc.
 * <p/>
 * Not thread-safe.
 */
class WhiteSpace {

  private static final char LINE_FEED = '\n';

  private final int myStart;
  private       int myEnd;

  private int mySpaces;
  private int myIndentSpaces;

  private CharSequence myInitial;
  private int          myFlags;
  private boolean      myForceSkipTabulationsUsage;

  private static final byte FIRST = 1;
  private static final byte SAFE = 0x2;
  private static final byte KEEP_FIRST_COLUMN = 0x4;
  private static final byte LINE_FEEDS_ARE_READ_ONLY = 0x8;
  private static final byte READ_ONLY = 0x10;
  private static final byte CONTAINS_LF_INITIALLY = 0x20;
  private static final byte CONTAINS_SPACES_INITIALLY = 0x40;
  private static final int LF_COUNT_SHIFT = 7;
  private static final int MAX_LF_COUNT = 1 << 24;

  /**
   * Creates new <code>WhiteSpace</code> object with the given start offset and a flag that shows if current white space is
   * the first white space.
   * <p/>
   * <b>Note:</b> {@link #getEndOffset() end offset} value is the same as the {@link #getStartOffset() start offset} for
   * the newly constructed object. {@link #append(int, FormattingDocumentModel, CodeStyleSettings.IndentOptions)} should be
   * called in order to apply desired end offset.
   *
   * @param startOffset       start offset to use
   * @param isFirst              flag that shows if current white space is the first
   */
  public WhiteSpace(int startOffset, boolean isFirst) {
    myStart = startOffset;
    myEnd = startOffset;
    setIsFirstWhiteSpace(isFirst);
  }

  /**
   * Applies new end offset to the current {@link WhiteSpace} object.
   * <p/>
   * Namely, performs the following:
   * <ol>
   *   <li>Checks if new end offset can be applied, return in case of negative answer;</li>
   *   <li>
   *          Processes all new symbols introduced by the new end offset value, calculates number of line feeds,
   *          white spaces and tabulations between them and updates {@link #getLineFeeds() lineFeeds}, {@link #getSpaces() spaces},
   *          {@link #getIndentSpaces() indentSpaces} and {@link #getTotalSpaces() totalSpaces} properties accordingly;
   *    </li>
   * </ol>
   *
   * @param newEndOffset      new end offset value
   * @param model                 formatting model that is used to access to the underlying document text
   * @param options               indent formatting options
   */
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
    CharSequence oldText = myInitial;
    myInitial = model.getText(range);

    if (!coveredByBlock(model)) {
      InitialInfoBuilder.assertInvalidRanges(myStart,
        myEnd,
        model,
        "nonempty text is not covered by block"
      );
    }

    // There is a possible case that this method is called more than once on the same object. We want to
    if (newEndOffset > oldEndOffset) {
      refreshStateOnEndOffsetIncrease(newEndOffset, oldEndOffset, options.TAB_SIZE);
    } else {
      refreshStateOnEndOffsetDecrease(oldText, newEndOffset, oldEndOffset, options.TAB_SIZE);
    }

    if (getLineFeeds() > 0) myFlags |= CONTAINS_LF_INITIALLY;
    else myFlags &= ~CONTAINS_LF_INITIALLY;

    final int totalSpaces = getTotalSpaces();
    if (totalSpaces > 0) myFlags |= CONTAINS_SPACES_INITIALLY;
    else myFlags &=~ CONTAINS_SPACES_INITIALLY;
  }

  /**
   * Allows to check if <code>'myInitial'</code> property value stands for continuous white space text.
   * <p/>
   * The text is considered to be continuous <code>'white space'</code> at following cases:
   * <ul>
   *   <li><code>'myInitial'</code> is empty string or string that contains white spaces only;</li>
   *   <li><code>'myInitial'</code> is a <code>CDATA</code> string which content is empty or consists from white spaces only;</li>
   *   <li><code>'myInitial'</code> string belongs to the same {@link PsiWhiteSpace} element;</li>
   * </ul>
   *
   * @param model     formatting model that is used to provide access to the <code>PSI API</code> if necessary
   * @return          <code>true</code> if <code>'myInitial'</code> property value stands for white space;
   *                  <code>false</code> otherwise
   */
  private boolean coveredByBlock(final FormattingDocumentModel model) {
    if (myInitial == null) return true;
    if (model.containsWhiteSpaceSymbolsOnly(myStart, myEnd)) return true;

    if (!(model instanceof FormattingDocumentModelImpl)) return false;
    PsiFile psiFile = ((FormattingDocumentModelImpl)model).getFile();
    if (psiFile == null) return false;
    PsiElement start = psiFile.findElementAt(myStart);
    PsiElement end = psiFile.findElementAt(myEnd-1);
    return start == end && start instanceof PsiWhiteSpace; // there maybe non-white text inside CDATA-encoded injected elements
  }

  private void refreshStateOnEndOffsetIncrease(int newEndOffset, int oldEndOffset, int tabSize) {
    assert newEndOffset > oldEndOffset;
    for (int i = oldEndOffset - myStart; i < newEndOffset - myStart; i++) {
      switch (myInitial.charAt(i)) {
        case LINE_FEED:
          setLineFeeds(getLineFeeds() + 1);
          mySpaces = 0;
          myIndentSpaces = 0;
          break;
        case '\t':
          myIndentSpaces += tabSize;
          break;
        default: mySpaces++;
      }
    }
  }

  private void refreshStateOnEndOffsetDecrease(CharSequence oldText, int newEndOffset, int oldEndOffset, int tabSize) {
    assert newEndOffset < oldEndOffset;
    int lineFeedsNumberAtRemovedText = 0;
    int spacesNumberAtRemovedText = 0;
    int indentSpacesNumberAtRemovedText = 0;
    for (int i = oldEndOffset - 1; i >= newEndOffset; i--) {
      switch (oldText.charAt(i)) {
        case LINE_FEED: ++lineFeedsNumberAtRemovedText; break;
        case ' ': ++spacesNumberAtRemovedText; break;
        case '\t': indentSpacesNumberAtRemovedText += tabSize; break;
      }
    }

    // There are no line feeds at remained text, hence, we can just subtract number of spaces and indent spaces
    // from removed text and finish.
    if (getLineFeeds() - lineFeedsNumberAtRemovedText <= 0) {
      setLineFeeds(0);
      mySpaces -= spacesNumberAtRemovedText;
      myIndentSpaces -= indentSpacesNumberAtRemovedText;
      return;
    }

    // There are white spaces at remained text, hence, we need to calculate number of spaces and indent spaces between
    // last line feed and new end offset.
    int newLineFeedsNumber = getLineFeeds() - lineFeedsNumberAtRemovedText;
    assert newLineFeedsNumber >= 0;
    newLineFeedsNumber = newLineFeedsNumber < 0 ? 0 : newLineFeedsNumber; // Never expect the defense to be triggered. 
    setLineFeeds(newLineFeedsNumber);
    mySpaces = 0;
    myIndentSpaces = 0;
    for (int i = newEndOffset - 1, c = oldText.charAt(i); c != LINE_FEED; c = oldText.charAt(--i)) {
      switch (c) {
        case ' ': ++mySpaces; break;
        case '\t': myIndentSpaces += tabSize; break;
      }
    }
  }

  /**
   * Builds string that contains line feeds, white spaces and tabulation symbols known to the current {@link WhiteSpace} object.
   *
   * @param options     indentation formatting options
   * @return            string that contains line feeds, white spaces and tabulation symbols known to the current
   *                    {@link WhiteSpace} object
   */
  public String generateWhiteSpace(CodeStyleSettings.IndentOptions options) {
    return new IndentInfo(getLineFeeds(), myIndentSpaces, mySpaces, myForceSkipTabulationsUsage).generateNewWhiteSpace(options);
  }

  /**
   * Tries to apply given values to {@link #getSpaces() spaces} and {@link #getIndentSpaces() indentSpaces} properties accordingly.
   * <p/>
   * The action is not guaranteed to be executed (i.e. the it's not guaranteed that target properties return given values after
   * this method call - see {@link #performModification(Runnable)} for more details).
   * <p/>
   * Moreover, the action is guaranteed to be <b>not</b> executed  if {@link #isKeepFirstColumn() keepFirstColumn} property
   * is unset and target document string doesn't contain spaces.
   *
   * @param spaces      new value for the {@link #getSpaces() spaces} property
   * @param indent      new value for the {@link #getIndentSpaces()}  indentSpaces} property
   */
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

  /**
   * Execute given action in a safe manner.
   * <p/>
   * <code>'Safe manner'</code> here means the following:
   * <ul>
   *   <li>don't execute the action if {@link #isIsReadOnly() isReadOnly} property value is set to <code>true</code>;</li>
   *   <li>
   *        ensure that number of line feeds after action execution is preserved if line feeds are
   *        {@link #isLineFeedsAreReadOnly() read only};
   *   </li>
   *   <li>
   *        ensure the following if {@link #isIsSafe() isSafe} property is set to <code>true</code>:
   *        <ul>
   *          <li>
   *            cut all white spaces and line feeds appeared after given action execution to single white space if there
   *            were no line feeds and white spaces before;
   *          </li>
   *        </ul>
   *    </li>
   * </ul>
   *
   * @param action      action that should be performed in a safe manner
   */
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
        // Actions below seem to be useless if 'after' value is 'false'. Are kept as historical heritage.
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

  /**
   * Tries to arrange {@link #getSpaces() spaces} property value to belong to
   * [{@link SpacingImpl#getMinSpaces() min}; {@link SpacingImpl#getMaxSpaces()}] bounds defined by the given spacing object
   * if {@link #getTotalSpaces() totalSpaces} property value lays out of the same bounds.
   * <p/>
   * The action is <b>not</b> performed if there are line feeds configured for the
   * current {@link WhiteSpace} object ({@link #getSpaces()} != 0).
   *
   * @param spaceProperty     spacing settings holder
   */
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

  /**
   * Tries to ensure that number of line feeds managed by the current {@link WhiteSpace} is consistent to the settings
   * defined at the given spacing property.
   *
   * @param spaceProperty       space settings holder
   * @param formatProcessor    format processor to use for space settings state refreshing
   */
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

  /**
   * There is a possible case that particular indent info is applied to the code block that is not the first block on a line.
   * E.g. we may want to align field name during <code>'align fields in columns'</code> processing:
   *     <pre>
   *         public class Test {
   *             private Object o;
   *             private int {@code <white space to align>}i;
   *         }
   *     </pre>
   * We may not want to use tabulation characters then even if user configured
   * {@link CodeStyleSettings.IndentOptions#USE_TAB_CHARACTER their usage} because we can't be sure how many visual columns
   * will be used for tab representation if there are non-white space symbols before it (IJ editor may use different number of columns
   * for single tabulation symbol representation).
   * <p/>
   * Hence, we can ask current white space object to avoid using tabulation symbols.
   *
   * @param skip   indicates if tabulations symbols usage should be suppressed
   */
  public void setForceSkipTabulationsUsage(boolean skip) {
    myForceSkipTabulationsUsage = skip;
  }

  /**
   * Allows to get information if target text document continuous 'white space' represented by the current object contained line feed
   * symbol(s) initially or contains line feed(s) now.
   *
   * @return    <code>true</code> if this object contained line feeds initially or contains them now; <code>false</code> otherwise
   * @see #containsLineFeedsInitially()
   */
  public boolean containsLineFeeds() {
    return isIsFirstWhiteSpace() || getLineFeeds() > 0;
  }

  public int getTotalSpaces() {
    return mySpaces + myIndentSpaces;
  }

  /**
   * Tries to ensure that current {@link WhiteSpace} object contains at least one line feed.
   */
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

  /**
   * @param ws      char sequence to check
   * @return        <code>true</code> if given char sequence is equal to the target document text identified by
   *                start/end offsets managed by the current {@link WhiteSpace} object; <code>false</code> otherwise
   */
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

  /**
   * Allows to get information if current object contained line feed(s) initially. It's considered to contain them if
   * line feeds were found at the target text document fragment after
   * new {@link #append(int, FormattingDocumentModel, CodeStyleSettings.IndentOptions) end offset appliance}.
   *
   * @return    <code>true</code> if current object contained line feeds initially; <code>false</code> otherwise
   */
  public boolean containsLineFeedsInitially() {
    if (myInitial == null) return false;
    return (myFlags & CONTAINS_LF_INITIALLY) != 0;
  }

  /**
   * Tries to ensure that number of line feeds and white spaces managed by the given {@link WhiteSpace} object is the
   * same as the one defined by the given <code>'spacing'</code> object.
   * <p/>
   * This method may be considered a shortcut for calling {@link #arrangeLineFeeds(SpacingImpl, FormatProcessor)} and
   * {@link #arrangeSpaces(SpacingImpl)}.
   *
   * @param spacing             spacing settings holder
   * @param formatProcessor     format processor to use to refresh state of the given <code>'spacing'</code> object
   */
  public void removeLineFeeds(final SpacingImpl spacing, final FormatProcessor formatProcessor) {
    performModification(new Runnable() {
      public void run() {
        setLineFeeds(0);
        mySpaces = 0;
        myIndentSpaces = 0;
      }
    });
    arrangeLineFeeds(spacing, formatProcessor);
    arrangeSpaces(spacing);
  }

  public int getIndentOffset() {
    return myIndentSpaces;
  }

  /**
   * Allows to get information about number of 'pure' white space symbols at the last line of continuous white space document
   * text fragment represented by the current {@link WhiteSpace} object.
   * <p/>
   * <b>Note:</b> pay special attention to <code>'last line'</code> qualification here. Consider the following target continuous
   * white space document fragment:
   * <pre>
   *        ' ws<sub>11</sub>ws<sub>12</sub>
   *        'ws<sub>21</sub>'
   * </pre>
   * <p/>
   * Here <code>'ws<sub>nm</sub>'</code> is a m-th white space symbol at the n-th line. <code>'Spaces'</code> property of
   * {@link WhiteSpace} object for such white-space text has a value not <b>2</b> or <b>3</b> but <b>1</b>.
   *
   * @return      number of white spaces at the last line of target continuous white space text document fragment
   */
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
      result.append(LINE_FEED);
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
      result.append(LINE_FEED);
      for (int i = currentLine + 1; i < lines.length - 1; i++) {
        result.append(lines[i]);
        result.append(LINE_FEED);
      }
      appendNonWhitespaces(result, lines, lines.length-1);
      result.append(lines[lines.length - 1]);

    }
    return result;
  }

  private static void appendNonWhitespaces(StringBuilder result, CharSequence[] lines, int currentLine) {
    // It looks like regexp usage is too heavy here.
    if (currentLine != lines.length && !lines[currentLine].toString().matches("\\s*")) {
      result.append(lines[currentLine]);
    }
  }

  /**
   * Shows target document text fragment identified by start/end offsets as a sequence of strings.
   * <p/>
   * <b>Note:</b> arrays usage here is considered to be a historical heritage.
   *
   * @return      target document text fragment as a sequence of strings
   */
  private CharSequence[] getInitialLines() {
    if (myInitial == null) return new CharSequence[]{""};
    final ArrayList<CharSequence> result = new ArrayList<CharSequence>();
    StringBuilder currentLine = new StringBuilder();
    for (int i = 0; i < myInitial.length(); i++) {
      final char c = myInitial.charAt(i);
      if (c == LINE_FEED) {
        result.add(currentLine);
        currentLine = new StringBuilder();
      }
      else {
        currentLine.append(c);
      }
    }
    result.add(currentLine);
    return result.toArray(new CharSequence[result.size()]);
  }

  /**
   * Provides access to the information about indent white spaces at last line of continuous white space text document
   * fragment represented by the current {@link WhiteSpace} object.
   * <p/>
   * <code>'Indent white space'</code> here is a white space representation of tabulation symbol. User may define that
   * he or she wants to use particular number of white spaces instead of tabulation
   * ({@link CodeStyleSettings.IndentOptions#TAB_SIZE}). So, {@link WhiteSpace} object uses corresponding
   * number of 'indent white spaces' for each tabulation symbol encountered at target continuous white space text document fragment.
   * <p/>
   * <b>Note:</b> pay special attention to <code>'last line'</code> qualification here. Consider the following target
   * continuous white space document fragment:
   * <pre>
   *        ' \t\t
   *        '\t'
   * </pre>
   * <p/>
   * Let's consider that <code>'tab size'</code> is defined as four white spaces (default setting).
   * <code>'IndentSpaces'</code> property of {@link WhiteSpace} object for such white-space text has a value not
   * <b>8</b> or <b>12</b> but <b>4</b>, i.e. tabulation symbols from last line
   * only are counted.
   *
   * @return        number of indent spaces at the last line of target continuous white space text document fragment
   */
  public int getIndentSpaces() {
    return myIndentSpaces;
  }

  public int getLength() {
    return myEnd - myStart;
  }

  /**
   * Provides access to the line feed symbols number at continuous white space text document fragment represented
   * by the current {@link WhiteSpace} object.
   *
   * @return      line feed symbols number
   */
  public final int getLineFeeds() {
    return myFlags >>> LF_COUNT_SHIFT;
  }

  public void setLineFeeds(final int lineFeeds) {
    assert lineFeeds < MAX_LF_COUNT;
    final int flags = myFlags;
    myFlags &= ~0xFFFFFF80; // keep only seven lower bits, i.e. drop all line feeds registered before if any
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

