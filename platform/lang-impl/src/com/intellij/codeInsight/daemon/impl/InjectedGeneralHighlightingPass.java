// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.concurrency.JobLauncher;
import com.intellij.ide.IdeBundle;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.ContributedReferencesAnnotators;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.Annotator;
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
import com.intellij.openapi.paths.WebReference;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageManagerImpl;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

import static com.intellij.openapi.editor.colors.EditorColors.createInjectedLanguageFragmentKey;

final class InjectedGeneralHighlightingPass extends GeneralHighlightingPass {

  private final @Nullable Collection<? extends @NotNull TextRange> myReducedRanges;

  InjectedGeneralHighlightingPass(@NotNull PsiFile file,
                                  @NotNull Document document,
                                  @Nullable Collection<? extends @NotNull TextRange> reducedRanges,
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

    List<PsiElement> allInsideElements = ContainerUtil.concat((List<List<PsiElement>>)ContainerUtil.map(allDivided, d -> d.inside));
    List<PsiElement> allOutsideElements = ContainerUtil.concat((List<List<PsiElement>>)ContainerUtil.map(allDivided, d -> d.outside));

    boolean enableContributedReferencePass = !Registry.is("annotate.hyperlinks.in.general.pass");
    List<PsiElement> contributedReferenceHosts = new ArrayList<>();
    List<PsiElement> contributedReferenceHostsInside = new ArrayList<>();
    if (enableContributedReferencePass) {
      findContributedReferenceHosts(myFile, contributedReferenceHosts);

      for (int i = 0; i < allInsideElements.size(); i++) {
        PsiElement element = allInsideElements.get(i);
        if (WebReference.isWebReferenceWorthy(element)) {
          contributedReferenceHostsInside.add(element);
        }
      }
    }

    int contributedReferenceHostsCount = contributedReferenceHosts.size();

    List<HighlightInfo> resultInside = new ArrayList<>(100);
    List<HighlightInfo> resultOutside = new ArrayList<>(100);

    InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(myProject);
    TextAttributesKey fragmentKey = createInjectedLanguageFragmentKey(myFile.getLanguage());
    processInjectedPsiFiles(allInsideElements, allOutsideElements, contributedReferenceHostsCount, progress, (injectedPsi, places) ->
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

    if (enableContributedReferencePass) {
      List<PsiElement> toVisit;
      if (resultOutside.isEmpty() && !myUpdateAll) { // we can update incrementally, no results for injected fragments outside of range
        toVisit = contributedReferenceHostsInside;
      }
      else { // we must update everything, since there can be updates outside of ranges
        Set<PsiElement> allOrdered = new LinkedHashSet<>(contributedReferenceHostsInside.size() + contributedReferenceHosts.size());
        allOrdered.addAll(contributedReferenceHostsInside);
        allOrdered.addAll(contributedReferenceHosts);
        toVisit = new ArrayList<>(allOrdered);
      }

      processContributedReferencesHosts(toVisit, highlightInfo -> {
        queueInfoToUpdateIncrementally(highlightInfo, getId());
        synchronized (myHighlights) {
          if (myRestrictRange.contains(highlightInfo)) {
            resultInside.add(highlightInfo);
          }
          else {
            resultOutside.add(highlightInfo);
          }
        }
      });
    }

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
                                       int contributedReferenceHostsCount,
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
    setProgressLimit(hosts.size() + contributedReferenceHostsCount);
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
    HighlightInfoFilter[] filters = HighlightInfoFilter.EXTENSION_POINT_NAME.getExtensions();
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
                          false, 0, info.getProblemGroup(), info.getInspectionToolId(), info.getGutterIconRenderer(), info.getGroup(), info.unresolvedReference, injectedPsi);
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

  /**
   * We highlight contributed references only after injected fragments, because InjectedReferencesContributor supplies references from
   * fragments and thus PsiLanguageInjectionHost.getReferences() depends on parsing of injected fragments.
   * <p>
   * This helps to not block {@link GeneralHighlightingPass} with a long parsing of nested code in a single threaded manner.
   */
  private void processContributedReferencesHosts(@NotNull List<PsiElement> contributedReferenceHosts,
                                                 @NotNull Consumer<? super HighlightInfo> outInfos) {
    ContributedReferencesHighlightVisitor visitor = new ContributedReferencesHighlightVisitor(myProject);

    HighlightInfoFilter[] filters = HighlightInfoFilter.EXTENSION_POINT_NAME.getExtensions();
    EditorColorsScheme actualScheme = getColorsScheme() == null ? EditorColorsManager.getInstance().getGlobalScheme() : getColorsScheme();
    HighlightInfoHolder holder = new HighlightInfoHolder(myFile, filters) {
      @Override
      public @NotNull TextAttributesScheme getColorsScheme() {
        return actualScheme;
      }

      @Override
      public boolean add(@Nullable HighlightInfo info) {
        boolean added = super.add(info);
        if (info != null && added) {
          outInfos.accept(info);
        }
        return added;
      }
    };

    visitor.analyze(holder, () -> {
      for (int i = 0; i < contributedReferenceHosts.size(); i++) {
        PsiElement contributedReferenceHost = contributedReferenceHosts.get(i);

        visitor.visit(contributedReferenceHost);

        advanceProgress(1);
      }
    });
  }

  private static void findContributedReferenceHosts(PsiFile file, List<PsiElement> result) {
    FileViewProvider viewProvider = file.getViewProvider();
    for (Language language : viewProvider.getLanguages()) {
      PsiFile root = viewProvider.getPsi(language);

      if (SHOULD_HIGHLIGHT_FILTER.test(root)) {
        // due to non-incremental nature of the injected pass we must always find all of them and add highlights
        // otherwise highlights outside restricted or priority range will be removed
        root.acceptChildren(new PsiRecursiveElementVisitor() {
          @Override
          public void visitElement(@NotNull PsiElement element) {
            super.visitElement(element);

            if (WebReference.isWebReferenceWorthy(element)) {
              result.add(element);
            }
          }
        });
      }
    }
  }
}

final class ContributedReferencesHighlightVisitor {
  private AnnotationHolderImpl myAnnotationHolder;

  private final Map<Language, List<Annotator>> myAnnotators;

  private final DumbService myDumbService;
  private final boolean myBatchMode;
  private boolean myDumb;

  ContributedReferencesHighlightVisitor(@NotNull Project project) {
    this(project, false);
  }

  private ContributedReferencesHighlightVisitor(@NotNull Project project,
                                                boolean batchMode) {
    myDumbService = DumbService.getInstance(project);
    myBatchMode = batchMode;
    myAnnotators = ConcurrentFactoryMap.createMap(language -> createAnnotators(language));
  }

  public void analyze(@NotNull HighlightInfoHolder holder, @NotNull Runnable action) {
    myDumb = myDumbService.isDumb();

    myAnnotationHolder = new AnnotationHolderImpl(holder.getAnnotationSession(), myBatchMode) {
      @Override
      void queueToUpdateIncrementally() {
        if (!isEmpty()) {
          //noinspection ForLoopReplaceableByForEach
          for (int i = 0; i < size(); i++) {
            Annotation annotation = get(i);
            holder.add(HighlightInfo.fromAnnotation(annotation, myBatchMode));
          }
          clear();
        }
      }
    };
    try {
      action.run();
      myAnnotationHolder.assertAllAnnotationsCreated();
    }
    finally {
      myAnnotators.clear();
      myAnnotationHolder = null;
    }
  }

  public void visit(@NotNull PsiElement element) {
    runAnnotators(element);
  }

  private void runAnnotators(@NotNull PsiElement element) {
    List<Annotator> annotators = myAnnotators.get(element.getLanguage());
    if (!annotators.isEmpty()) {
      AnnotationHolderImpl holder = myAnnotationHolder;
      holder.myCurrentElement = element;
      for (Annotator annotator : annotators) {
        if (!myDumb || DumbService.isDumbAware(annotator)) {
          ProgressManager.checkCanceled();
          holder.myCurrentAnnotator = annotator;
          annotator.annotate(element, holder);
          // assume that annotator is done messing with just created annotations after its annotate() method completed,
          // so we can start applying them incrementally at last
          // (but not sooner, thanks to awfully racey Annotation.setXXX() API)
          holder.queueToUpdateIncrementally();
        }
      }
    }
  }

  private static @NotNull List<Annotator> createAnnotators(@NotNull Language language) {
    return ContributedReferencesAnnotators.INSTANCE.allForLanguageOrAny(language);
  }
}