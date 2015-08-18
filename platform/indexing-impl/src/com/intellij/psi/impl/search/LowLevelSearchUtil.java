/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.search.TextOccurenceProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.text.StringSearcher;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LowLevelSearchUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.search.LowLevelSearchUtil");

  // TRUE/FALSE -> injected psi has been discovered and processor returned true/false;
  // null -> there were nothing injected found
  private static Boolean processInjectedFile(PsiElement element,
                                             final TextOccurenceProcessor processor,
                                             final StringSearcher searcher,
                                             ProgressIndicator progress,
                                             InjectedLanguageManager injectedLanguageManager) {
    if (!(element instanceof PsiLanguageInjectionHost)) return null;
    if (injectedLanguageManager == null) return null;
    List<Pair<PsiElement,TextRange>> list = injectedLanguageManager.getInjectedPsiFiles(element);
    if (list == null) return null;
    for (Pair<PsiElement, TextRange> pair : list) {
      final PsiElement injected = pair.getFirst();
      if (!processElementsContainingWordInElement(processor, injected, searcher, false, progress)) return Boolean.FALSE;
    }
    return Boolean.TRUE;
  }

  /**
   * @return null to stop or last found TreeElement
   * to be reused via <code>lastElement<code/> param in subsequent calls to avoid full tree rescan (n^2->n).
   */
  private static TreeElement processTreeUp(@NotNull Project project,
                                       @NotNull TextOccurenceProcessor processor,
                                       @NotNull PsiElement scope,
                                       @NotNull StringSearcher searcher,
                                       final int offset,
                                       final boolean processInjectedPsi,
                                       ProgressIndicator progress,
                                       TreeElement lastElement) {
    final int scopeStartOffset = scope.getTextRange().getStartOffset();
    final int patternLength = searcher.getPatternLength();
    ASTNode scopeNode = scope.getNode();
    boolean useTree = scopeNode != null;
    assert scope.isValid();

    int start;
    TreeElement leafNode = null;
    PsiElement leafElement = null;
    if (useTree) {
      leafNode = findNextLeafElementAt(scopeNode, lastElement, offset);
      if (leafNode == null) return lastElement;
      start = offset - leafNode.getStartOffset() + scopeStartOffset;
    }
    else {
      if (scope instanceof PsiFile) {
        leafElement = ((PsiFile)scope).getViewProvider().findElementAt(offset, scope.getLanguage());
      }
      else {
        leafElement = scope.findElementAt(offset);
      }
      if (leafElement == null) return lastElement;
      assert leafElement.isValid();
      start = offset - leafElement.getTextRange().getStartOffset() + scopeStartOffset;
    }
    if (start < 0) {
      throw new AssertionError("offset=" + offset + " scopeStartOffset=" + scopeStartOffset + " leafElement=" + leafElement + "  scope=" + scope);
    }
    InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(project);
    lastElement = leafNode;
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
          Boolean result = processInjectedFile(run, processor, searcher, progress, injectedLanguageManager);
          if (result != null) {
            return result.booleanValue() ? lastElement : null;
          }
        }
        if (!processor.execute(run, start)) {
          return null;
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

    return lastElement;
  }

  private static TreeElement findNextLeafElementAt(ASTNode scopeNode, TreeElement last, int offset) {
    int offsetR = offset;
    if (last !=null) {
      offsetR -= last.getStartOffset() - scopeNode.getStartOffset() + last.getTextLength();
      while (offsetR >= 0) {
        TreeElement next = last.getTreeNext();
        if (next == null) {
          last = last.getTreeParent();
          continue;
        }
        int length = next.getTextLength();
        offsetR -= length;
        last = next;
      }
      scopeNode = last;
      offsetR += scopeNode.getTextLength();
    }
    return (LeafElement)scopeNode.findLeafElementAt(offsetR);
  }

  //@RequiresReadAction
  public static boolean processElementsContainingWordInElement(@NotNull final TextOccurenceProcessor processor,
                                                               @NotNull final PsiElement scope,
                                                               @NotNull final StringSearcher searcher,
                                                               final boolean processInjectedPsi,
                                                               final ProgressIndicator progress) {
    if (progress != null) progress.checkCanceled();

    PsiFile file = scope.getContainingFile();
    FileViewProvider viewProvider = file.getViewProvider();
    final CharSequence buffer = viewProvider.getContents();

    TextRange range = scope.getTextRange();
    if (range == null) {
      LOG.error("Element " + scope + " of class " + scope.getClass() + " has null range");
      return true;
    }

    final int scopeStart = range.getStartOffset();
    final int startOffset = scopeStart;
    int endOffset = range.getEndOffset();
    if (endOffset > buffer.length()) {
      diagnoseInvalidRange(scope, file, viewProvider, buffer, range);
      return true;
    }

    final Project project = file.getProject();
    final TreeElement[] lastElement = {null};
    return processTextOccurrences(buffer, startOffset, endOffset, searcher, progress, new TIntProcedure() {
      @Override
      public boolean execute(int offset) {
        if (progress != null) progress.checkCanceled();
        lastElement[0] = processTreeUp(project, processor, scope, searcher, offset - scopeStart, processInjectedPsi, progress,
                                       lastElement[0]);
        return lastElement[0] != null;
      }
    });
  }

  private static void diagnoseInvalidRange(@NotNull PsiElement scope,
                                           PsiFile file,
                                           FileViewProvider viewProvider,
                                           CharSequence buffer,
                                           TextRange range) {
    String msg = "Range for element: '" + scope + "' = " + range + " is out of file '" + file + "' range: " + file.getTextRange();
    msg += "; file contents length: " + buffer.length();
    msg += "\n file provider: " + viewProvider;
    Document document = viewProvider.getDocument();
    if (document != null) {
      msg += "\n committed=" + PsiDocumentManager.getInstance(file.getProject()).isCommitted(document);
    }
    for (Language language : viewProvider.getLanguages()) {
      final PsiFile root = viewProvider.getPsi(language);
      msg += "\n root " + language + " length=" + root.getTextLength() + (root instanceof PsiFileImpl
                                                                          ? "; contentsLoaded=" + ((PsiFileImpl)root).isContentsLoaded() : "");
    }

    LOG.error(msg);
  }

  public static boolean processTextOccurrences(@NotNull CharSequence text,
                                               int startOffset,
                                               int endOffset,
                                               @NotNull StringSearcher searcher,
                                               @Nullable ProgressIndicator progress,
                                               @NotNull TIntProcedure processor) {
    if (endOffset > text.length()) {
      throw new AssertionError("end>length");
    }
    for (int index = startOffset; index < endOffset; index++) {
      if (progress != null) progress.checkCanceled();
      //noinspection AssignmentToForLoopParameter
      index = searcher.scan(text, index, endOffset);
      if (index < 0) break;
      if (checkJavaIdentifier(text, startOffset, endOffset, searcher, index)) {
        if (!processor.execute(index)) return false;
      }
    }
    return true;
  }

  private static boolean checkJavaIdentifier(@NotNull CharSequence text,
                                             int startOffset,
                                             int endOffset,
                                             @NotNull StringSearcher searcher,
                                             int index) {
    if (!searcher.isJavaIdentifier()) {
      return true;
    }

    if (index > startOffset) {
      char c = text.charAt(index - 1);
      if (Character.isJavaIdentifierPart(c) && c != '$') {
        if (!searcher.isHandleEscapeSequences() || index < 2 || isEscapedBackslash(text, startOffset, index - 2)) { //escape sequence
          return false;
        }
      }
      else if (index > 0 && searcher.isHandleEscapeSequences() && !isEscapedBackslash(text, startOffset, index - 1)) {
        return false;
      }
    }

    final int patternLength = searcher.getPattern().length();
    if (index + patternLength < endOffset) {
      char c = text.charAt(index + patternLength);
      if (Character.isJavaIdentifierPart(c) && c != '$') {
        return false;
      }
    }
    return true;
  }

  private static boolean isEscapedBackslash(CharSequence text, int startOffset, int index) {
    return StringUtil.isEscapedBackslash(text, startOffset, index);
  }
}
