// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@ApiStatus.Internal
public final class CodeFormattingData {

  private static final Key<CodeFormattingData> CODE_FORMATTING_DATA_KEY = Key.create("code.formatting.data");

  private final @NotNull Map<Integer, Set<TextRange>> injectedFileRangesMap = new HashMap<>();

  private final @NotNull PsiFile myPsiFile;

  private CodeFormattingData(@NotNull PsiFile file) { myPsiFile = file; }

  public static @NotNull CodeFormattingData getOrCreate(@NotNull PsiFile psiFile) {
    CodeFormattingData formattingData = psiFile.getUserData(CODE_FORMATTING_DATA_KEY);
    if (formattingData == null) {
      formattingData = new CodeFormattingData(psiFile);
      psiFile.putUserData(CODE_FORMATTING_DATA_KEY, formattingData);
    }
    return formattingData;
  }
  
  public static void copy(@NotNull PsiFile source, @NotNull PsiFile target) {
    target.putUserData(CODE_FORMATTING_DATA_KEY, source.getUserData(CODE_FORMATTING_DATA_KEY));
  }

  public static @NotNull CodeFormattingData prepare(@NotNull PsiFile file, @NotNull List<TextRange> ranges) {
    CodeFormattingData formattingData = getOrCreate(file);
    for (TextRange range : ranges) {
      formattingData.getInjectedRanges(range);
    }

    for (CodeFormattingDataPreparer preparer : CodeFormattingDataPreparer.EP_NAME.getExtensionList()) {
      preparer.prepareFormattingData(file, ranges, formattingData);
    }

    return formattingData;
  }

  public void dispose() {
    myPsiFile.putUserData(CODE_FORMATTING_DATA_KEY, null);
  }

  public @NotNull Set<TextRange> getInjectedRanges(@NotNull TextRange range) {
    if (myPsiFile.getProject().isDefault() || range.isEmpty()) return Collections.emptySet();
    if ("Rust".equals(myPsiFile.getLanguage().getID())) return Collections.emptySet();
    Set<TextRange> injectedRanges = injectedFileRangesMap.get(range.getStartOffset());
    if (injectedRanges == null) {
      injectedRanges = collectInjectedRanges(range);
      injectedFileRangesMap.put(range.getStartOffset(), injectedRanges);
    }
    return injectedRanges;
  }


  private LinkedHashSet<TextRange> collectInjectedRanges(@NotNull TextRange range) {
    // We use a set here because we encountered a situation when more than one PSI leaf points to the same injected fragment
    // (at least for sql injected into sql).
    final LinkedHashSet<TextRange> injectedFileRangesSet = new LinkedHashSet<>();
    if (myPsiFile.getProject().isDefault()) return injectedFileRangesSet;

    List<DocumentWindow> injectedDocuments =
      InjectedLanguageManager.getInstance(myPsiFile.getProject()).getCachedInjectedDocumentsInRange(myPsiFile, myPsiFile.getTextRange());
    if (!injectedDocuments.isEmpty()) {
      for (DocumentWindow injectedDocument : injectedDocuments) {
        injectedFileRangesSet.add(TextRange.from(injectedDocument.injectedToHost(0), injectedDocument.getTextLength()));
      }
    }
    else {
      Collection<PsiLanguageInjectionHost> injectionHosts = collectInjectionHosts(myPsiFile, range);
      PsiLanguageInjectionHost.InjectedPsiVisitor visitor = (injectedPsi, places) -> {
        for (PsiLanguageInjectionHost.Shred place : places) {
          Segment rangeMarker = place.getHostRangeMarker();
          if (rangeMarker != null) {
            injectedFileRangesSet.add(TextRange.create(rangeMarker.getStartOffset(), rangeMarker.getEndOffset()));
          }
        }
      };
      for (PsiLanguageInjectionHost host : injectionHosts) {
        ProgressManager.checkCanceled();
        InjectedLanguageManager.getInstance(myPsiFile.getProject()).enumerate(host, visitor);
      }
    }
    return injectedFileRangesSet;
  }

  private static @NotNull Collection<PsiLanguageInjectionHost> collectInjectionHosts(@NotNull PsiFile file, @NotNull TextRange range) {
    Stack<PsiElement> toProcess = new Stack<>();
    for (PsiElement e = file.findElementAt(range.getStartOffset()); e != null; e = e.getNextSibling()) {
      if (e.getTextRange().getStartOffset() >= range.getEndOffset()) {
        break;
      }
      toProcess.push(e);
    }
    if (toProcess.isEmpty()) {
      return Collections.emptySet();
    }
    Set<PsiLanguageInjectionHost> result = null;
    while (!toProcess.isEmpty()) {
      PsiElement e = toProcess.pop();
      if (e instanceof PsiLanguageInjectionHost) {
        if (result == null) {
          result = new HashSet<>();
        }
        result.add((PsiLanguageInjectionHost)e);
      }
      else {
        for (PsiElement child = e.getFirstChild(); child != null; child = child.getNextSibling()) {
          if (e.getTextRange().getStartOffset() >= range.getEndOffset()) {
            break;
          }
          toProcess.push(child);
        }
      }
    }
    return result == null ? Collections.emptySet() : result;
  }
}
