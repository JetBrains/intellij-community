/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.impl.source.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class handles a use-case when reformatted text conflicts with 'use tab' code style setting. E.g. target text uses
 * tabs for indentation but our code style is configured to use spaces.
 * <p/>
 * We already have corresponding support at the block level but it's possible that multiline text is treated as a single block,
 * i.e. all its internal indents are not visible to the formatter. That's why current class is introduced.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/1/12 2:38 PM
 */
public class TabPostFormatProcessor implements PostFormatProcessor {

  @Override
  public PsiElement processElement(@NotNull PsiElement source, @NotNull CodeStyleSettings settings) {
    doProcess(source, TextRange.from(source.getTextRange().getStartOffset(), source.getTextLength()), settings);
    return source;
  }

  @Override
  public TextRange processText(@NotNull PsiFile source, @NotNull TextRange rangeToReformat, @NotNull CodeStyleSettings settings) {
    return doProcess(source, rangeToReformat, settings);
  }

  @NotNull
  private static TextRange doProcess(@NotNull PsiElement source, @NotNull TextRange range, @NotNull CodeStyleSettings settings) {
    ASTNode node = source.getNode();
    if (node == null) {
      return range;
    }

    Language language = source.getLanguage();
    if (language != JavaLanguage.INSTANCE) {
      // We had the only complaint for tabs not being converted to spaces for now. It was for the java code which has
      // a single block for the multi-line comment. This check should be removed if it is decided to generalize
      // this logic to other languages as well.
      return range;
    }

    if (!source.isValid()) return range;
    PsiFile file = source.getContainingFile();
    CommonCodeStyleSettings.IndentOptions indentOptions = settings.getIndentOptionsByFile(file, range);

    boolean useTabs = indentOptions.USE_TAB_CHARACTER;
    boolean smartTabs = indentOptions.SMART_TABS;
    int tabWidth = indentOptions.TAB_SIZE;
    return processViaPsi(node, range, new TreeHelperImpl(), useTabs, smartTabs, tabWidth);
  }

  @NotNull
  static TextRange processViaPsi(@NotNull ASTNode node,
                                 @NotNull TextRange range,
                                 @NotNull TreeHelper treeHelper,
                                 boolean useTabs,
                                 boolean smartTabs,
                                 int tabWidth)
  {
    AstHelper helper = new AstHelper(node, treeHelper);
    do {
      if (useTabs) {
        if (smartTabs) {
          range = processSmartTabs(helper, range, tabWidth);
        }
        else {
          range = processTabs(helper, range, tabWidth);
        }
      }
      else {
        range = processSpaces(helper, range, tabWidth);
      }
    }
    while (helper.nextLine());
    return range;
  }
  
  @NotNull
  static TextRange processViaDocument(@NotNull Document document,
                                      @NotNull TextRange range,
                                      boolean useTabs,
                                      boolean useSmartTabs,
                                      int tabWidth)
  {
    TextRange result = range;
    int startLine = document.getLineNumber(Math.min(document.getTextLength(), range.getStartOffset()));
    int endLine = document.getLineNumber(Math.max(0, Math.min(document.getTextLength(), range.getEndOffset()) - 1));
    DocumentHelper helper = new DocumentHelper(document, startLine);
    for (int line = startLine; line <= endLine; line++) {
      helper.setLine(line);
      if (useTabs) {
        if (useSmartTabs) {
          result = processSmartTabs(helper, result, tabWidth);
        }
        else {
          result = processTabs(helper, result, tabWidth);
        }
      }
      else {
        result = processSpaces(helper, result, tabWidth);
      }
    }
    return result;
  }

  /**
   * Converts tabulations to white spaces at the target line's indent space.
   *
   * @param helper    data facade
   * @param range     target range allowed for modification
   * @param tabWidth  tab width in columns to use during conversion (each tab symbol is replaced by white spaces which number is 
   *                  equal to tab width)
   * @return          given text range if no modification to the target line's indent space has been performed:
   *                  adjusted range that points to semantically the same region otherwise
   */
  @NotNull
  private static TextRange processSpaces(@NotNull Helper helper, @NotNull TextRange range, int tabWidth) {
    CharSequence indent = helper.getCurrentLineIndent();
    int start = Math.max(0, range.getStartOffset() - helper.getCurrentLineStartOffset());
    int end = Math.min(indent.length(), range.getEndOffset() - helper.getCurrentLineStartOffset());
    int tabsNumber = 0;
    int indentOffset = end;
    for (int i = start; i < end; i++) {
      char c = indent.charAt(i);
      if (c == '\t') {
        tabsNumber++;
      }
      else if (c != ' ') {
        indentOffset = i;
        break;
      }
    }
    if (tabsNumber > 0) {
      helper.replace(start, indentOffset, StringUtil.repeat(" ", indentOffset - start - tabsNumber + tabsNumber * tabWidth));
      return TextRange.create(range.getStartOffset(), range.getEndOffset() - tabsNumber + tabsNumber * tabWidth);
    }
    else {
      return range;
    }
  }

  /**
   * Converts white spaces to tabulations at the target line's indent space.
   *
   * @param helper    data facade
   * @param range     target range allowed for modification
   * @param tabWidth  tab width in columns to use during conversion (each tab symbol is replaced by white spaces which number is 
   *                  equal to tab width)
   * @return          given text range if no modification to the target line's indent space has been performed:
   *                  adjusted range that points to semantically the same region otherwise
   */
  @NotNull
  private static TextRange processTabs(@NotNull Helper helper, @NotNull TextRange range, int tabWidth) {
    CharSequence indent = helper.getCurrentLineIndent();
    int start = Math.max(0, range.getStartOffset() - helper.getCurrentLineStartOffset());
    int end = Math.min(indent.length(), range.getEndOffset() - helper.getCurrentLineStartOffset());
    int replacementsNumber = 0;
    int consecutiveSpaces = 0;
    for (int i = start; i < end; i++) {
      char c = indent.charAt(i);
      if (c == ' ') {
        ++consecutiveSpaces;
      }
      else {
        int tabsNumber = consecutiveSpaces / tabWidth;
        if (tabsNumber > 0) {
          helper.replace(i - consecutiveSpaces, i - consecutiveSpaces + tabsNumber * tabWidth, StringUtil.repeat("\t", tabsNumber));
          replacementsNumber++;
          consecutiveSpaces = 0;
        }
        if (c != '\t') {
          break;
        }
      }
    }

    int tabsNumber = consecutiveSpaces / tabWidth;
    if (tabsNumber > 0) {
      helper.replace(end - consecutiveSpaces, end - consecutiveSpaces + tabsNumber * tabWidth, StringUtil.repeat("\t", tabsNumber));
    }

    if (replacementsNumber > 0) {
      return TextRange.create(range.getStartOffset(), range.getEndOffset() - replacementsNumber * (tabWidth - 1));
    }
    else {
      return range;
    }
  }

  /**
   * Converts tabulations to white spaces at the target line's indent space.
   *
   * @param helper    data facade
   * @param range     target range allowed for modification
   * @param tabWidth  tab width in columns to use during conversion (every group of 'tab width' white spaces from the indent space might
   *                  be replaced by a tab symbol)
   * @return          given text range if no modification to the target line's indent space has been performed:
   *                  adjusted range that points to semantically the same region otherwise
   */
  @SuppressWarnings("AssignmentToForLoopParameter")
  @NotNull
  private static TextRange processSmartTabs(@NotNull Helper helper, @NotNull TextRange range, int tabWidth) {
    // Adjust current line indent. The general idea is to replace white spaces by tab symbols if that maps to the previous line indent.
    CharSequence prevLineIndent = helper.getPrevLineIndent();
    if (prevLineIndent == null) {
      return processTabs(helper, range, tabWidth);
    }

    CharSequence currentLineIndent = helper.getCurrentLineIndent();
    int lineStart = 0;
    int start = Math.max(0, range.getStartOffset() - helper.getCurrentLineStartOffset());
    int end = Math.min(currentLineIndent.length(), range.getEndOffset() - helper.getCurrentLineStartOffset());
    int indentOffset = 0;
    int tabsReplaced = 0;
    for (int i = lineStart; i < end && indentOffset < prevLineIndent.length(); i++, indentOffset++) {
      char c = currentLineIndent.charAt(i);
      if (prevLineIndent.charAt(indentOffset) == ' ') {
        if (c == ' ') {
          continue;
        }
        else {
          break;
        }
      }

      // Assuming that target prevLineIndent symbol is tab then.
      if (c == '\t') {
        continue;
      }

      if (end - i < tabWidth) {
        break;
      }

      boolean canReplace = true;
      for (int j = i + 1, max = Math.min(end, i + tabWidth); j < max; j++) {
        if (currentLineIndent.charAt(j) != ' ') {
          canReplace = false;
          break;
        }
      }

      if (!canReplace) {
        break;
      }

      if (i < start) {
        // Continue processing if target range doesn't cover the whole white spaces which are intended to replace tab symbol.
        i += tabWidth - 1; // -1 because of 'for' loop increment
        continue;
      }
      
      helper.replace(i, i + tabWidth, "\t");
      tabsReplaced++;
      end -= tabWidth - 1;
    }

    return tabsReplaced > 0 ? TextRange.create(range.getStartOffset(), range.getEndOffset() - tabsReplaced * (tabWidth - 1)) : range;
  }

  /**
   * There are two possible processing use-cases:
   * <pre>
   * <ul>
   *   <li>document-based processing;</li>
   *   <li>PSI-based processing;</li>
   * </ul>
   * </pre>
   * That's why we hide implementation-specific processing behind the current interface and use it at the generic 'engine'.
   * <p/>
   * The general idea is to process indent spaces line-by-line from top to bottom.
   */
  interface Helper {

    /**
     * @return    previous line indent space if current line is not the first one; <code>null</code> otherwise
     */
    @Nullable CharSequence getPrevLineIndent();
    
    int getCurrentLineStartOffset();

    /** @return    current line's indent space */
    @NotNull CharSequence getCurrentLineIndent();

    /**
     * Asks current helper to modify target line's indent space.
     * 
     * @param start    start offset of the indent range to modify (counts from the line start, i.e. doesn't take into
     *                 consideration line start offset at the document)
     * @param end      end offset of the indent range to modify (counts from the line start, i.e. doesn't take into
     *                 consideration line start offset at the document)
     * @param newText  replacement text
     */
    void replace(int start, int end, @NotNull String newText);
  }
  
  private static class DocumentHelper implements Helper {
    
    @NotNull private final Document myDocument;
    private int myLine;
    private int myLineStartOffset;

    DocumentHelper(@NotNull Document document, int line) {
      myDocument = document;
      setLine(line);
    }

    @Nullable
    @Override
    public CharSequence getPrevLineIndent() {
      if (myLine <= 0) {
        return null;
      }
      int prevLineStart = myDocument.getLineStartOffset(myLine - 1);
      int prevLineIndentEnd = prevLineStart;
      int prevLineEnd = myDocument.getLineEndOffset(myLine - 1);
      CharSequence text = myDocument.getCharsSequence();
      for (; prevLineIndentEnd < prevLineEnd; prevLineIndentEnd++) {
        char c = text.charAt(prevLineIndentEnd);
        if (c != '\t' && c != ' ') {
          break;
        }
      }
      return text.subSequence(prevLineStart, prevLineIndentEnd);
    }

    @Override
    public int getCurrentLineStartOffset() {
      return myLineStartOffset;
    }

    @NotNull
    @Override
    public CharSequence getCurrentLineIndent() {
      int end = myDocument.getLineEndOffset(myLine);
      CharSequence text = myDocument.getCharsSequence();
      for (int i = myLineStartOffset; i < end; i++) {
        char c = text.charAt(i);
        if (c != ' ' && c != '\t') {
          return text.subSequence(myLineStartOffset, i);
        }
      }
      return text.subSequence(myLineStartOffset, end);
    }

    @Override
    public void replace(int start, int end, @NotNull String newText) {
      myDocument.replaceString(myLineStartOffset + start, myLineStartOffset + end, newText);
    }

    public void setLine(int line) {
      myLine = line;
      myLineStartOffset = myDocument.getLineStartOffset(line);
    }
  }
  
  private static class AstHelper implements Helper {

    @NotNull private final TreeHelper myHelper;
    @Nullable private      ASTNode    myCurrentIndentHolder;

    private int myLineStartOffset;

    AstHelper(@NotNull ASTNode startNode, @NotNull TreeHelper helper) {
      myHelper = helper;
      myCurrentIndentHolder = myHelper.firstLeaf(startNode);
      if (startNode.getStartOffset() <= 0) {
        return;
      }
      nextLine();
    }

    @SuppressWarnings("LoopStatementThatDoesntLoop")
    @Override
    public CharSequence getPrevLineIndent() {
      if (myCurrentIndentHolder == null) {
        return null;
      }

      // Check if current white space is multiline.
      int end = myLineStartOffset - 1;
      CharSequence text = myCurrentIndentHolder.getChars();
      for (int i = end - 1; i >= 0; i--) {
        if (text.charAt(i) == '\n') {
          return text.subSequence(i + 1, end);
        }
      }
      for (ASTNode prev = prevIndentNode(myCurrentIndentHolder); prev != null; prev = prevIndentNode(prev)) {
        CharSequence chars = prev.getChars();
        for (int i = chars.length() - 1; i >= 0; i--) {
          if (chars.charAt(i) == '\n') {
            return chars.subSequence(i + 1, chars.length());
          }
        }
        return chars;
      }
      return null;
    }

    @Override
    public int getCurrentLineStartOffset() {
      ASTNode whiteSpace = myCurrentIndentHolder;
      return whiteSpace == null ? 0 : whiteSpace.getStartOffset() + myLineStartOffset;
    }

    @SuppressWarnings("UnusedAssignment")
    @NotNull
    @Override
    public CharSequence getCurrentLineIndent() {
      if (myCurrentIndentHolder == null || myLineStartOffset < 0) {
        return "";
      }

      CharSequence text = myCurrentIndentHolder.getChars();
      for (int i = myLineStartOffset; i < text.length(); i++) {
        char c = text.charAt(i);
        if (c == '\n' || (c != ' ' && c != '\t')) {
          return text.subSequence(myLineStartOffset, i);
        }
      }
      return text.subSequence(myLineStartOffset, text.length());
    }

    @Override
    public void replace(int start, int end, @NotNull String newText) {
      if (myCurrentIndentHolder != null) {
        myHelper.replace(newText, TextRange.create(start, end).shiftRight(getCurrentLineStartOffset()), myCurrentIndentHolder);
      }
    }

    public boolean nextLine() {
      if (myCurrentIndentHolder == null) {
        return false;
      }
      for (ASTNode node = myHelper.nextLeaf(myCurrentIndentHolder); node != null; node = myHelper.nextLeaf(node)) {
        if (myCurrentIndentHolder.getTextLength() <= 0) {
          continue;
        }
        CharSequence text = node.getChars();
        for (myLineStartOffset = 0; myLineStartOffset < text.length(); myLineStartOffset++) {
          char c = text.charAt(myLineStartOffset);
          if (c == '\n' && myLineStartOffset < text.length() - 1) {
            myCurrentIndentHolder = node;
            myLineStartOffset++;
            return true;
          }
        }
      }

      myCurrentIndentHolder = null;
      return false;
    }

    @Nullable
    private ASTNode prevIndentNode(@NotNull ASTNode current) {
      for (ASTNode candidate = myHelper.prevLeaf(current); candidate != null; candidate = myHelper.prevLeaf(candidate)) {
        if (candidate.getStartOffset() <= 0 || StringUtil.contains(candidate.getChars(), 0, candidate.getTextLength(), '\n')) {
          return candidate;
        }
      }
      return null;
    }
  }

  interface TreeHelper {
    @Nullable
    ASTNode prevLeaf(@NotNull ASTNode current);

    @Nullable
    ASTNode nextLeaf(@NotNull ASTNode current);

    @Nullable
    ASTNode firstLeaf(@NotNull ASTNode startNode);

    void replace(@NotNull String newText, @NotNull TextRange range, @NotNull ASTNode leaf);
  }

  private static class TreeHelperImpl implements TreeHelper {

    @Override
    public ASTNode prevLeaf(@NotNull ASTNode current) {
      return TreeUtil.prevLeaf(current);
    }

    @Nullable
    @Override
    public ASTNode nextLeaf(@NotNull ASTNode current) {
      return TreeUtil.nextLeaf(current);
    }

    @Nullable
    @Override
    public ASTNode firstLeaf(@NotNull ASTNode startNode) {
      return TreeUtil.findFirstLeaf(startNode);
    }

    @Override
    public void replace(@NotNull String newText, @NotNull TextRange range, @NotNull ASTNode leaf) {
      FormatterUtil.replaceInnerWhiteSpace(newText, leaf, range);
    }
  }
}
