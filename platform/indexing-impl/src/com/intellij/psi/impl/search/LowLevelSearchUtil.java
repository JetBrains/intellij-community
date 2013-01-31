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

package com.intellij.psi.impl.search;

import com.intellij.lang.ASTNode;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.search.TextOccurenceProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.StringSearcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LowLevelSearchUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.search.LowLevelSearchUtil");

  private LowLevelSearchUtil() {
  }

  // TRUE/FALSE -> injected psi has been discovered and processor returned true/false;
  // null -> there were nothing injected found
  private static Boolean processInjectedFile(PsiElement element,
                                             final TextOccurenceProcessor processor,
                                             final StringSearcher searcher,
                                             ProgressIndicator progress) {
    if (!(element instanceof PsiLanguageInjectionHost)) return null;
    InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(element.getProject());
    if (injectedLanguageManager == null) return null;
    List<Pair<PsiElement,TextRange>> list = injectedLanguageManager.getInjectedPsiFiles(element);
    if (list == null) return null;
    for (Pair<PsiElement, TextRange> pair : list) {
      final PsiElement injected = pair.getFirst();
      if (!processElementsContainingWordInElement(processor, injected, searcher, false, progress)) return Boolean.FALSE;
    }
    return Boolean.TRUE;
  }

  private static boolean processTreeUp(@NotNull TextOccurenceProcessor processor,
                                       @NotNull PsiElement scope,
                                       @NotNull StringSearcher searcher,
                                       final int offset,
                                       final boolean processInjectedPsi,
                                       ProgressIndicator progress) {
    final int scopeStartOffset = scope.getTextRange().getStartOffset();
    final int patternLength = searcher.getPatternLength();
    ASTNode scopeNode = scope.getNode();
    boolean useTree = scopeNode != null;
    assert scope.isValid();

    int start;
    TreeElement leafNode = null;
    PsiElement leafElement = null;
    if (useTree) {
      leafNode = (LeafElement)scopeNode.findLeafElementAt(offset);
      if (leafNode == null) return true;
      start = offset - leafNode.getStartOffset() + scopeStartOffset;
    }
    else {
      if (scope instanceof PsiFile) {
        leafElement = ((PsiFile)scope).getViewProvider().findElementAt(offset, scope.getLanguage());
      }
      else {
        leafElement = scope.findElementAt(offset);
      }
      if (leafElement == null) return true;
      assert leafElement.isValid();
      start = offset - leafElement.getTextRange().getStartOffset() + scopeStartOffset;
    }
    if (start < 0) {
      LOG.error("offset=" + offset + " scopeStartOffset=" + scopeStartOffset + " leafElement=" + leafElement + " " +
                                  " scope=" + scope.toString());
    }
    boolean contains = false;
    PsiElement prev = null;
    TreeElement prevNode = null;
    PsiElement run = null;
    while (run != scope) {
      if (progress != null) progress.checkCanceled();
      if (useTree) {
        start += prevNode == null ? 0 : prevNode.getStartOffsetInParent();
        prevNode = leafNode;
        run = leafNode.getPsi();
      }
      else {
        start += prev == null ? 0 : prev.getStartOffsetInParent();
        prev = run;
        run = leafElement;
      }
      if (!contains) contains = run.getTextLength() - start >= patternLength;  //do not compute if already contains
      if (contains) {
        if (processInjectedPsi) {
          Boolean result = processInjectedFile(run, processor, searcher, progress);
          if (result != null) {
            return result.booleanValue();
          }
        }
        if (!processor.execute(run, start)) {
          return false;
        }
      }
      if (useTree) {
        leafNode = leafNode.getTreeParent();
        if (leafNode == null) break;
      }
      else {
        leafElement = leafElement.getParent();
        if (leafElement == null) break;
      }
    }
    assert run == scope: "Malbuilt PSI: scopeNode="+scope+"; leafNode="+run+"; isAncestor="+ PsiTreeUtil.isAncestor(scope, run, false);

    return true;
  }
  //@RequiresReadAction
  public static boolean processElementsContainingWordInElement(@NotNull TextOccurenceProcessor processor,
                                                               @NotNull PsiElement scope,
                                                               @NotNull StringSearcher searcher,
                                                               final boolean processInjectedPsi,
                                                               ProgressIndicator progress) {
    if (progress != null) progress.checkCanceled();

    PsiFile file = scope.getContainingFile();
    final CharSequence buffer = file.getViewProvider().getContents();

    TextRange range = scope.getTextRange();
    if (range == null) {
      throw new AssertionError("Element " + scope + " of class " + scope.getClass() + " has null range");
    }

    int scopeStart = range.getStartOffset();
    int startOffset = scopeStart;
    int endOffset = range.getEndOffset();
    if (endOffset > buffer.length()) {
      LOG.error("Range for element: '"+scope+"' = "+range+" is out of file '" + file + "' range: " + file.getTextLength());
    }

    final char[] bufferArray = CharArrayUtil.fromSequenceWithoutCopying(buffer);

    do {
      if (progress != null) progress.checkCanceled();
      startOffset  = searchWord(buffer, bufferArray, startOffset, endOffset, searcher, progress);
      if (startOffset < 0) {
        return true;
      }
      if (!processTreeUp(processor, scope, searcher, startOffset - scopeStart, processInjectedPsi, progress)) return false;

      startOffset++;
    }
    while (startOffset < endOffset);

    return true;
  }

  public static int searchWord(@NotNull CharSequence text,
                               int startOffset,
                               int endOffset,
                               @NotNull StringSearcher searcher,
                               @Nullable ProgressIndicator progress) {
    return searchWord(text, null, startOffset, endOffset, searcher, progress);
  }

  public static int searchWord(@NotNull CharSequence text,
                               @Nullable char[] textArray,
                               int startOffset,
                               int endOffset,
                               @NotNull StringSearcher searcher,
                               @Nullable ProgressIndicator progress) {
    LOG.assertTrue(endOffset <= text.length());

    for (int index = startOffset; index < endOffset; index++) {
      if (progress != null) progress.checkCanceled();
      //noinspection AssignmentToForLoopParameter
      index = searcher.scan(text, textArray, index, endOffset);
      if (index < 0) return -1;
      if (!searcher.isJavaIdentifier()) {
        return index;
      }

      if (index > startOffset) {
        char c = textArray != null ? textArray[index - 1]:text.charAt(index - 1);
        if (Character.isJavaIdentifierPart(c) && c != '$') {
          if (searcher.isHandleEscapeSequences() && (index < 2 || !isNotEscapedBackslash(text, textArray, startOffset, index-2))) { //escape sequence
            continue;
          }
        }
        else if (index > 0 && searcher.isHandleEscapeSequences() && isNotEscapedBackslash(text, textArray, startOffset, index-1)) {
          continue;
        }
      }

      final int patternLength = searcher.getPattern().length();
      if (index + patternLength < endOffset) {
        char c = textArray != null ? textArray[index + patternLength]:text.charAt(index + patternLength);
        if (Character.isJavaIdentifierPart(c) && c != '$') {
          continue;
        }
      }
      return index;
    }
    return -1;
  }

  private static boolean isNotEscapedBackslash(CharSequence text, char[] textArray, int startOffset, int index) {
    return textArray != null
                 ? StringUtil.isNotEscapedBackslash(textArray, startOffset, index)
                 : StringUtil.isNotEscapedBackslash(text, startOffset, index);
  }
}
