/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.progress.ProgressManager;
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
import com.intellij.util.ArrayUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.StringSearcher;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public class LowLevelSearchUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.search.LowLevelSearchUtil");

  // TRUE/FALSE -> injected psi has been discovered and processor returned true/false;
  // null -> there were nothing injected found
  private static Boolean processInjectedFile(PsiElement element,
                                             final TextOccurenceProcessor processor,
                                             final StringSearcher searcher,
                                             @NotNull ProgressIndicator progress,
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
                                           @NotNull ProgressIndicator progress,
                                           TreeElement lastElement) {
    if (scope instanceof PsiCompiledElement) {
      throw new IllegalArgumentException("Scope is compiled, can't scan: "+scope+"; containingFile: "+scope.getContainingFile());
    }
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
      throw new AssertionError("offset=" + offset + "; scopeStartOffset=" + scopeStartOffset + "; leafElement=" + leafElement + ";  scope=" + scope+"; leafElement.isValid(): "+ (leafElement == null ? null : leafElement.isValid()));
    }
    InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(project);
    lastElement = leafNode;
    boolean contains = false;
    PsiElement prev = null;
    TreeElement prevNode = null;
    PsiElement run = null;
    while (run != scope) {
      ProgressManager.checkCanceled();
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
    assert run == scope: "Malbuilt PSI; scopeNode: "+scope+"; containingFile:" + PsiTreeUtil.getParentOfType(scope, PsiFile.class, false) +
                         "; leafNode: "+run+"; isAncestor="+ PsiTreeUtil.isAncestor(scope, run, false)+"; in same file: "+(PsiTreeUtil.getParentOfType(scope, PsiFile.class, false) == PsiTreeUtil.getParentOfType(run, PsiFile.class, false));

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

  public static boolean processElementsContainingWordInElement(@NotNull final TextOccurenceProcessor processor,
                                                               @NotNull final PsiElement scope,
                                                               @NotNull final StringSearcher searcher,
                                                               boolean processInjectedPsi,
                                                               @NotNull  ProgressIndicator progress) {
    int[] occurrences = getTextOccurrencesInScope(scope, searcher, progress);
    return processElementsAtOffsets(scope, searcher, processInjectedPsi, progress, occurrences, processor);
  }

  @NotNull
  static int[] getTextOccurrencesInScope(@NotNull PsiElement scope, @NotNull StringSearcher searcher, ProgressIndicator progress) {
    ProgressManager.checkCanceled();

    PsiFile file = scope.getContainingFile();
    FileViewProvider viewProvider = file.getViewProvider();
    final CharSequence buffer = viewProvider.getContents();

    TextRange range = scope.getTextRange();
    if (range == null) {
      LOG.error("Element " + scope + " of class " + scope.getClass() + " has null range");
      return ArrayUtil.EMPTY_INT_ARRAY;
    }

    int startOffset = range.getStartOffset();
    int endOffset = range.getEndOffset();
    if (endOffset > buffer.length()) {
      diagnoseInvalidRange(scope, file, viewProvider, buffer, range);
      return ArrayUtil.EMPTY_INT_ARRAY;
    }

    int[] offsets = getTextOccurrences(buffer, startOffset, endOffset, searcher, progress);
    for (int i = 0; i < offsets.length; i++) {
      offsets[i] -= startOffset;
    }
    return offsets;
  }

  static boolean processElementsAtOffsets(@NotNull PsiElement scope,
                                          @NotNull StringSearcher searcher,
                                          boolean processInjectedPsi,
                                          @NotNull ProgressIndicator progress,
                                          int[] offsetsInScope, @NotNull TextOccurenceProcessor processor) {
    if (offsetsInScope.length == 0) return true;

    Project project = scope.getProject();
    TreeElement lastElement = null;
    for (int offset : offsetsInScope) {
      progress.checkCanceled();
      lastElement = processTreeUp(project, processor, scope, searcher, offset, processInjectedPsi, progress, lastElement);
      if (lastElement == null) return false;
    }
    return true;
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

  // map (text to be scanned -> list of cached pairs of (searcher used to scan text, occurrences found))
  // occurrences found is an int array of (startOffset used, endOffset used, occurrence 1 offset, occurrence 2 offset,...)
  private static final ConcurrentMap<CharSequence, Map<StringSearcher, int[]>> cache = ContainerUtil.createConcurrentWeakMap(ContainerUtil.identityStrategy());
  public static boolean processTextOccurrences(@NotNull CharSequence text,
                                               int startOffset,
                                               int endOffset,
                                               @NotNull StringSearcher searcher,
                                               @Nullable ProgressIndicator progress,
                                               @NotNull TIntProcedure processor) {
    for (int offset : getTextOccurrences(text, startOffset, endOffset, searcher, progress)) {
      if (!processor.execute(offset)) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  private static int[] getTextOccurrences(@NotNull CharSequence text,
                                          int startOffset,
                                          int endOffset,
                                          @NotNull StringSearcher searcher,
                                          @Nullable ProgressIndicator progress) {
    if (endOffset > text.length()) {
      throw new IllegalArgumentException("end: " + endOffset + " > length: "+text.length());
    }
    Map<StringSearcher, int[]> cachedMap = cache.get(text);
    int[] cachedOccurrences = cachedMap == null ? null : cachedMap.get(searcher);
    boolean hasCachedOccurrences = cachedOccurrences != null && cachedOccurrences[0] <= startOffset && cachedOccurrences[1] >= endOffset;
    if (!hasCachedOccurrences) {
      TIntArrayList occurrences = new TIntArrayList();
      int newStart = Math.min(startOffset, cachedOccurrences == null ? startOffset : cachedOccurrences[0]);
      int newEnd = Math.max(endOffset, cachedOccurrences == null ? endOffset : cachedOccurrences[1]);
      occurrences.add(newStart);
      occurrences.add(newEnd);
      for (int index = newStart; index < newEnd; index++) {
        ProgressManager.checkCanceled();
        //noinspection AssignmentToForLoopParameter
        index = searcher.scan(text, index, newEnd);
        if (index < 0) break;
        if (checkJavaIdentifier(text, 0, text.length(), searcher, index)) {
          occurrences.add(index);
        }
      }
      cachedOccurrences = occurrences.toNativeArray();
      if (cachedMap == null) {
        cachedMap = ConcurrencyUtil.cacheOrGet(cache, text, ContainerUtil.createConcurrentSoftMap());
      }
      cachedMap.put(searcher, cachedOccurrences);
    }
    TIntArrayList offsets = new TIntArrayList(cachedOccurrences.length - 2);
    for (int i = 2; i < cachedOccurrences.length; i++) {
      int occurrence = cachedOccurrences[i];
      if (occurrence > endOffset - searcher.getPatternLength()) break;
      if (occurrence >= startOffset) {
        offsets.add(occurrence);
      }
    }
    return offsets.toNativeArray();
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
