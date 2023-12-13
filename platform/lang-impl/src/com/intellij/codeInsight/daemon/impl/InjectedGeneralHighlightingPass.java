// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.concurrency.JobLauncher;
import com.intellij.ide.IdeBundle;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.TextAttributesScheme;
import com.intellij.openapi.editor.ex.util.LayeredTextAttributes;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageManagerImpl;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

import static com.intellij.openapi.editor.colors.EditorColors.createInjectedLanguageFragmentKey;

final class InjectedGeneralHighlightingPass extends GeneralHighlightingPass {

  private final @Nullable List<? extends @NotNull TextRange> myReducedRanges;

  InjectedGeneralHighlightingPass(@NotNull PsiFile file,
                                  @NotNull Document document,
                                  @Nullable List<? extends @NotNull TextRange> reducedRanges,
                                  int startOffset,
                                  int endOffset,
                                  boolean updateAll,
                                  @NotNull ProperTextRange priorityRange,
                                  @Nullable Editor editor,
                                  @NotNull HighlightInfoProcessor highlightInfoProcessor) {
    super(file, document, startOffset, endOffset, updateAll, priorityRange, editor, highlightInfoProcessor);
    myReducedRanges = reducedRanges;
  }

  @Override
  protected @NotNull String getPresentableName() {
    return IdeBundle.message("highlighting.pass.injected.presentable.name");
  }

  @Override
  protected void collectInformationWithProgress(@NotNull ProgressIndicator progress) {
    if (!Registry.is("editor.injected.highlighting.enabled")) return;

    List<Divider.DividedElements> allDivided = new ArrayList<>();
    Divider.divideInsideAndOutsideAllRoots(myFile, myRestrictRange, myPriorityRange, SHOULD_HIGHLIGHT_FILTER, new CommonProcessors.CollectProcessor<>(allDivided));

    List<PsiElement> allInsideElements = ContainerUtil.concat(ContainerUtil.map(allDivided, d -> d.inside()));
    List<PsiElement> allOutsideElements = ContainerUtil.concat(ContainerUtil.map(allDivided, d -> d.outside()));

    List<HighlightInfo> resultInside = new ArrayList<>(100);
    List<HighlightInfo> resultOutside = new ArrayList<>(100);

    InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(myProject);
    TextAttributesKey fragmentKey = createInjectedLanguageFragmentKey(myFile.getLanguage());
    processInjectedPsiFiles(allInsideElements, allOutsideElements, progress, (injectedPsi, places) ->
      addInjectedPsiHighlights(injectedLanguageManager, injectedPsi, places, fragmentKey, patchedInfo -> {
        queueInfoToUpdateIncrementally(patchedInfo, getId());
        synchronized (myHighlights) {
          if (myRestrictRange.contains(patchedInfo)) {
            resultInside.add(patchedInfo);
          }
          else {
            // non-conditionally apply injected results regardless whether they are in myRestrictRange
            resultOutside.add(patchedInfo);
          }
        }
      }));

    synchronized (myHighlights) {
      // all infos for the "injected fragment for the host which is inside" are indeed inside
      // but some infos for the "injected fragment for the host which is outside" can be still inside
      if (resultOutside.isEmpty()) {
        // apply only result (by default apply command) and only within inside
        myHighlights.addAll(resultInside);
        myHighlightInfoProcessor.highlightsInsideVisiblePartAreProduced(myHighlightingSession, getEditor(), myHighlights, myRestrictRange, myRestrictRange, getId());
      }
      else {
        boolean priorityIntersectionHasElements = myPriorityRange.intersectsStrict(myRestrictRange);

        if ((!allInsideElements.isEmpty() || !resultInside.isEmpty()) && priorityIntersectionHasElements) { // do not apply when there were no elements to highlight
          // clear infos found in visible area to avoid applying them twice
          myHighlights.addAll(resultInside);
          myHighlightInfoProcessor.highlightsInsideVisiblePartAreProduced(myHighlightingSession, getEditor(), resultInside, myPriorityRange, myRestrictRange, getId());
        }
        else {
          for (HighlightInfo info : resultInside) {
            if (myRestrictRange.contains(info) && !myPriorityRange.contains(info)) {
              resultOutside.add(info);
            }
          }
        }

        myHighlightInfoProcessor.highlightsOutsideVisiblePartAreProduced(myHighlightingSession, getEditor(), resultOutside, myRestrictRange, new ProperTextRange(0, myDocument.getTextLength()), getId());
      }
    }
  }

  private void processInjectedPsiFiles(@NotNull List<? extends PsiElement> elements1,
                                       @NotNull List<? extends PsiElement> elements2,
                                       @NotNull ProgressIndicator progress,
                                       @NotNull PsiLanguageInjectionHost.InjectedPsiVisitor visitor) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    InjectedLanguageManagerImpl injectedLanguageManager = InjectedLanguageManagerImpl.getInstanceImpl(myProject);
    List<DocumentWindow> cachedInjected = injectedLanguageManager.getCachedInjectedDocumentsInRange(myFile, myFile.getTextRange());
    Collection<PsiElement> hosts = new HashSet<>(elements1.size() + elements2.size() + cachedInjected.size());

    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(myProject);
    //rehighlight all injected PSI regardless the range,
    //since change in one place can lead to invalidation of injected PSI in (completely) other place.
    for (DocumentWindow documentRange : cachedInjected) {
      ProgressManager.checkCanceled();
      if (!documentRange.isValid()) continue;
      PsiFile file = psiDocumentManager.getPsiFile(documentRange);
      if (file == null) continue;
      PsiElement context = injectedLanguageManager.getInjectionHost(file);
      if (context != null
          && context.isValid()
          && !file.getProject().isDisposed()
          && (myUpdateAll || myRestrictRange.contains(context.getTextRange()))) { // consider strict if partial update
        if (myReducedRanges != null && !ContainerUtil.exists(myReducedRanges, reducedRange -> reducedRange.contains(context.getTextRange()))) { // skip if not in reduced
          continue;
        }
        hosts.add(context);
      }
    }

    Processor<PsiElement> collectInjectableProcessor = new CommonProcessors.CollectProcessor<>(hosts) {
      @Override
      public boolean process(PsiElement t) {
        ProgressManager.checkCanceled();
        if (InjectedLanguageUtil.isInjectable(t, false)) {
          super.process(t);
        }
        return true;
      }
    };
    injectedLanguageManager.processInjectableElements(elements1, collectInjectableProcessor);
    injectedLanguageManager.processInjectableElements(elements2, collectInjectableProcessor);

    // the most expensive process is running injectors for these hosts, comparing to highlighting the resulting injected fragments,
    // so instead of showing "highlighted 1% of injected fragments", show "ran injectors for 1% of hosts"
    setProgressLimit(hosts.size());
    Set<PsiElement> visitedInjected = ConcurrentCollectionFactory.createConcurrentSet(); // in case of concatenation, multiple hosts can return the same injected fragment. have to visit it only once

    if (!JobLauncher.getInstance().invokeConcurrentlyUnderProgress(new ArrayList<>(hosts), progress, element -> {
        ApplicationManager.getApplication().assertReadAccessAllowed();
        injectedLanguageManager.enumerateEx(element, myFile, false, (injectedPsi, places) -> {
          if (visitedInjected.add(injectedPsi)) {
            visitor.visit(injectedPsi, places);
          }
        });
        advanceProgress(1);
        return true;
      })) {
      throw new ProcessCanceledException();
    }
  }

  private @NotNull HighlightInfoHolder createInfoHolder(@NotNull PsiFile injectedPsi, @NotNull DocumentWindow documentWindow,
                                                        @NotNull InjectedLanguageManager injectedLanguageManager,
                                                        @NotNull Consumer<? super HighlightInfo> outInfos) {
    HighlightInfoFilter[] filters = HighlightInfoFilter.EXTENSION_POINT_NAME.getExtensionList().toArray(HighlightInfoFilter.EMPTY_ARRAY);
    EditorColorsScheme actualScheme = getColorsScheme() == null ? EditorColorsManager.getInstance().getGlobalScheme() : getColorsScheme();
    return new HighlightInfoHolder(injectedPsi, filters) {
      @Override
      public @NotNull TextAttributesScheme getColorsScheme() {
        return actualScheme;
      }
      @Override
      public boolean add(@Nullable HighlightInfo info) {
        boolean added = super.add(info);
        if (info != null && added) {
          addPatchedInfos(info, injectedPsi, documentWindow, injectedLanguageManager, outInfos);
        }
        return added;
      }
    };
  }

  private void addInjectedPsiHighlights(@NotNull InjectedLanguageManager injectedLanguageManager,
                                        @NotNull PsiFile injectedPsi,
                                        @NotNull List<? extends PsiLanguageInjectionHost.Shred> places,
                                        @Nullable TextAttributesKey attributesKey,
                                        @NotNull Consumer<? super HighlightInfo> outInfos) {
    DocumentWindow documentWindow = (DocumentWindow)PsiDocumentManager.getInstance(myProject).getCachedDocument(injectedPsi);
    if (documentWindow == null) return;
    boolean addTooltips = places.size() < 100;
    for (PsiLanguageInjectionHost.Shred place : places) {
      PsiLanguageInjectionHost host = place.getHost();
      if (host == null) continue;
      TextRange textRange = place.getRangeInsideHost().shiftRight(host.getTextRange().getStartOffset());
      if (textRange.isEmpty()) continue;
      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.INJECTED_LANGUAGE_BACKGROUND).range(textRange);
      if (attributesKey != null && InjectedLanguageUtil.isHighlightInjectionBackground(host)) {
        builder.textAttributes(attributesKey);
      }
      if (addTooltips) {
        String desc = injectedPsi.getLanguage().getDisplayName() + ": " + injectedPsi.getText();
        builder.unescapedToolTip(desc);
      }
      HighlightInfo info = builder.createUnconditionally();
      info.markFromInjection();
      outInfos.accept(info);
    }

    HighlightInfoHolder holder = createInfoHolder(injectedPsi, documentWindow, injectedLanguageManager, outInfos);
    runHighlightVisitorsForInjected(injectedPsi, holder);
    highlightInjectedSyntax(injectedPsi, places, outInfos);

    if (!isDumbMode()) {
      List<HighlightInfo> todos = new ArrayList<>();
      highlightTodos(injectedPsi, injectedPsi.getText(), 0, injectedPsi.getTextLength(), myPriorityRange, todos, todos);
      for (HighlightInfo info : todos) {
        addPatchedInfos(info, injectedPsi, documentWindow, injectedLanguageManager, outInfos);
      }
    }
  }

  private static void addPatchedInfos(@NotNull HighlightInfo info,
                                      @NotNull PsiFile injectedPsi,
                                      @NotNull DocumentWindow documentWindow,
                                      @NotNull InjectedLanguageManager injectedLanguageManager,
                                      @NotNull Consumer<? super HighlightInfo> outInfos) {
    ProperTextRange infoRange = new ProperTextRange(info.startOffset, info.endOffset);
    List<TextRange> editables = injectedLanguageManager.intersectWithAllEditableFragments(injectedPsi, infoRange);
    for (TextRange editable : editables) {
      TextRange hostRange = documentWindow.injectedToHost(editable);

      boolean isAfterEndOfLine = info.isAfterEndOfLine();
      if (isAfterEndOfLine) {
        // convert injected afterEndOfLine to either host's afterEndOfLine or not-afterEndOfLine highlight of the injected fragment boundary
        int hostEndOffset = hostRange.getEndOffset();
        int lineNumber = documentWindow.getDelegate().getLineNumber(hostEndOffset);
        int hostLineEndOffset = documentWindow.getDelegate().getLineEndOffset(lineNumber);
        if (hostEndOffset < hostLineEndOffset) {
          // convert to non-afterEndOfLine
          isAfterEndOfLine = false;
          hostRange = new ProperTextRange(hostRange.getStartOffset(), hostEndOffset+1);
        }
      }

      HighlightInfo patched =
        new HighlightInfo(info.forcedTextAttributes, info.forcedTextAttributesKey, info.type,
                          hostRange.getStartOffset(), hostRange.getEndOffset(),
                          info.getDescription(), info.getToolTip(), info.getSeverity(), isAfterEndOfLine, null,
                          false, 0, info.getProblemGroup(), info.toolId, info.getGutterIconRenderer(), info.getGroup(), info.unresolvedReference);
      patched.setHint(info.hasHint());

      info.findRegisteredQuickFix((descriptor, quickfixTextRange) -> {
        List<TextRange> editableQF = injectedLanguageManager.intersectWithAllEditableFragments(injectedPsi, quickfixTextRange);
        for (TextRange editableRange : editableQF) {
          TextRange hostEditableRange = documentWindow.injectedToHost(editableRange);
          patched.registerFix(descriptor.getAction(), descriptor.myOptions, descriptor.getDisplayName(), hostEditableRange, descriptor.myKey);
        }
        return null;
      });
      patched.markFromInjection();
      outInfos.accept(patched);
    }
  }

  private void runHighlightVisitorsForInjected(@NotNull PsiFile injectedPsi, @NotNull HighlightInfoHolder holder) {
    HighlightVisitor[] filtered = getHighlightVisitors(injectedPsi);
    try {
      List<PsiElement> elements = CollectHighlightsUtil.getElementsInRange(injectedPsi, 0, injectedPsi.getTextLength());
      for (HighlightVisitor visitor : filtered) {
        visitor.analyze(injectedPsi, true, holder, () -> {
          for (PsiElement element : elements) {
            ProgressManager.checkCanceled();
            visitor.visit(element);
          }
        });
      }
    }
    finally {
      incVisitorUsageCount(-1);
    }
  }

  private void highlightInjectedSyntax(@NotNull PsiFile injectedPsi, @NotNull List<? extends PsiLanguageInjectionHost.Shred> places, @NotNull Consumer<? super HighlightInfo> outInfos) {
    List<InjectedLanguageUtil.TokenInfo> tokens = InjectedLanguageUtil.getHighlightTokens(injectedPsi);
    if (tokens == null) return;

    int shredIndex = -1;
    int injectionHostTextRangeStart = -1;
    for (InjectedLanguageUtil.TokenInfo token : tokens) {
      ProgressManager.checkCanceled();
      TextRange range = token.rangeInsideInjectionHost();
      if (range.getLength() == 0) continue;
      if (shredIndex != token.shredIndex()) {
        shredIndex = token.shredIndex();
        PsiLanguageInjectionHost.Shred shred = places.get(shredIndex);
        PsiLanguageInjectionHost host = shred.getHost();
        if (host == null) return;
        injectionHostTextRangeStart = host.getTextRange().getStartOffset();
      }
      TextRange hostRange = range.shiftRight(injectionHostTextRangeStart);

      addSyntaxInjectedFragmentInfo(myGlobalScheme, hostRange, token.textAttributesKeys(), outInfos);
    }
  }

  @Override
  protected void applyInformationWithProgress() {
  }

  static void addSyntaxInjectedFragmentInfo(@NotNull EditorColorsScheme scheme,
                                            @NotNull TextRange hostRange,
                                            TextAttributesKey @NotNull [] keys,
                                            @NotNull Consumer<? super HighlightInfo> outInfos) {
    if (hostRange.isEmpty()) {
      return;
    }
    // erase marker to override hosts colors
    HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.INJECTED_LANGUAGE_FRAGMENT)
      .range(hostRange)
      .textAttributes(TextAttributes.ERASE_MARKER)
      .createUnconditionally();
    outInfos.accept(info);

    LayeredTextAttributes injectedAttributes = LayeredTextAttributes.create(scheme, keys);
    if (injectedAttributes.isEmpty() || keys.length == 1 && keys[0] == HighlighterColors.TEXT) {
      // nothing to add
      return;
    }

    HighlightInfo injectedInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.INJECTED_LANGUAGE_FRAGMENT)
      .range(hostRange)
      .textAttributes(injectedAttributes)
      .createUnconditionally();
    outInfos.accept(injectedInfo);
  }
}
