// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.completion.impl.CompletionSorterImpl;
import com.intellij.codeInsight.lookup.Classifier;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.WeighingContext;
import com.intellij.modcompletion.ModCompletionItemProvider;
import com.intellij.modcompletion.ModCompletionResult;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.DumbModeAccessType;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A lightweight implementation of completion using ModCompletion providers 
 * @see ModCompletionItemProvider 
 */
@NotNullByDefault
@ApiStatus.Internal
public final class LightModCompletionServiceImpl {
  public static void getItems(PsiFile file, int caretOffset, int invocationCount, CompletionType type,
                              ModCompletionResult sink) {
    CharSequence sequence = file.getFileDocument().getCharsSequence();
    int start = findStart(caretOffset, sequence);
    DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
      getItems(file, start, caretOffset, invocationCount, type, sink);
    });
  }

  private static int findStart(int caretOffset, CharSequence sequence) {
    int start = caretOffset;
    while (start > 0 && StringUtil.isJavaIdentifierPart(sequence.charAt(start - 1))) {
      start--;
    }
    return start;
  }

  public static void getItems(PsiFile file, int startOffset, int caretOffset, int invocationCount, CompletionType type,  
                              ModCompletionResult sink) {
    PsiElement element;
    PsiElement original = file.findElementAt(startOffset);
    if (startOffset == caretOffset) {
      PsiFile copy = (PsiFile)file.copy();
      Document document = copy.getFileDocument();
      document.insertString(caretOffset, CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED);
      PsiDocumentManager manager = PsiDocumentManager.getInstance(file.getProject());
      manager.commitDocument(document);
      element = Objects.requireNonNull(copy.findElementAt(caretOffset));
    } else {
      element = Objects.requireNonNullElse(original, file);
    }
    List<ModCompletionItemProvider> providers = ModCompletionItemProvider.EP_NAME.allForLanguage(file.getLanguage());
    String prefix = file.getFileDocument().getText(TextRange.create(startOffset, caretOffset));
    var matcher = new CamelHumpMatcher(prefix);
    ModCompletionItemProvider.CompletionContext context = new ModCompletionItemProvider.CompletionContext(
      file, caretOffset, original, element, matcher, invocationCount, type);
    ProcessingContext processingContext = createContext(matcher);
    Map<CompletionSorterImpl, Classifier<LookupElement>> sortMap = new LinkedHashMap<>();
    Map<CompletionSorterImpl, List<LookupElement>> allItems = new LinkedHashMap<>();
    for (ModCompletionItemProvider provider : providers) {
      CompletionSorterImpl sorter = (CompletionSorterImpl)provider.getSorter(context);
      Classifier<LookupElement> classifier = sortMap.computeIfAbsent(sorter, s -> s.buildClassifier(Classifier.empty()));
      provider.provideItems(context, item -> {
        if (matcher.prefixMatches(item.mainLookupString()) ||
            ContainerUtil.exists(item.additionalLookupStrings(), matcher::prefixMatches)) {
          CompletionItemLookupElement le = new CompletionItemLookupElement(item);
          classifier.addElement(le, processingContext);
          allItems.computeIfAbsent(sorter, k -> new ArrayList<>()).add(le);
        }
      });
    }
    EntryStream.of(sortMap)
      .mapKeyValue((sorter, classifier) -> classifier.classify(allItems.getOrDefault(sorter, List.of()), processingContext))
      .flatMap(items -> StreamEx.of(items.spliterator()))
      .map(item -> ((CompletionItemLookupElement)item).item())
      .forEach(sink);
  }

  private static ProcessingContext createContext(CamelHumpMatcher matcher) {
    ProcessingContext processingContext = new ProcessingContext();
    processingContext.put(CompletionLookupArranger.WEIGHING_CONTEXT, new SimpleWeighingContext(matcher));
    processingContext.put(CompletionLookupArranger.PREFIX_CHANGES, 0);
    return processingContext;
  }

  private record SimpleWeighingContext(PrefixMatcher myMatcher) implements WeighingContext {
    @Override
    public String itemPattern(LookupElement element) {
      return myMatcher.getPrefix();
    }

    @Override
    public PrefixMatcher itemMatcher(LookupElement item) {
      return myMatcher;
    }
  }
}
