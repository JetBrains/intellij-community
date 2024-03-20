// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.AnnotatorStatisticsCollector;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.concurrency.JobLauncher;
import com.intellij.diagnostic.PluginException;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.PairProcessor;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class AnnotatorRunner {
  private static final Logger LOG = Logger.getInstance(AnnotatorRunner.class);
  private final Project myProject;
  private final PsiFile myPsiFile;
  private final HighlightInfoHolder myHighlightInfoHolder;
  private final HighlightingSession myHighlightingSession;
  private final DumbService myDumbService;
  private final boolean myBatchMode;
  private boolean myDumb;
  private final AnnotatorStatisticsCollector myAnnotatorStatisticsCollector = new AnnotatorStatisticsCollector();
  private final List<HighlightInfo> results = Collections.synchronizedList(new ArrayList<>());

  AnnotatorRunner(@NotNull PsiFile psiFile, boolean batchMode, @NotNull HighlightInfoHolder holder, @NotNull HighlightingSession highlightingSession) {
    myProject = psiFile.getProject();
    myPsiFile = psiFile;
    myHighlightInfoHolder = holder;
    myHighlightingSession = highlightingSession;
    myDumbService = DumbService.getInstance(myProject);
    myBatchMode = batchMode;
  }

  boolean runAnnotatorsAsync(@NotNull List<? extends PsiElement> inside, @NotNull List<? extends PsiElement> outside, @NotNull BooleanSupplier runnable) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    DaemonProgressIndicator indicator = GlobalInspectionContextBase.assertUnderDaemonProgress();

    myDumb = myDumbService.isDumb();

    // TODO move inside Divider to calc only once
    List<PsiElement> insideThenOutside = ContainerUtil.concat(inside, outside);
    Map<Annotator, Set<Language>> supportedLanguages = calcSupportedLanguages(insideThenOutside);
    HighlightInfoUpdater highlightInfoUpdater = HighlightInfoUpdater.getInstance(myProject);
    HighlightersRecycler invalidElementsRecycler = highlightInfoUpdater.removeOrRecycleInvalidPsiElements(myPsiFile, "AnnotationRunner", false, true, myHighlightingSession);
    try {
      PairProcessor<Annotator, JobLauncher.QueueController<? super Annotator>> processor = (annotator, __) ->
        ApplicationManagerEx.getApplicationEx().tryRunReadAction(() -> runAnnotator(annotator, insideThenOutside, supportedLanguages, highlightInfoUpdater));
      boolean result = JobLauncher.getInstance().procInOrderAsync(indicator, supportedLanguages.size(), processor, addToQueue -> {
        for (Annotator annotator : supportedLanguages.keySet()) {
          addToQueue.enqueue(annotator);
        }
        addToQueue.finish();
        return runnable.getAsBoolean();
      });
      myAnnotatorStatisticsCollector.reportAnalysisFinished(myProject, myHighlightInfoHolder.getAnnotationSession(), myPsiFile);
      return result;
    }
    finally {
      UpdateHighlightersUtil.incinerateObsoleteHighlighters(invalidElementsRecycler, myHighlightingSession);
      invalidElementsRecycler.releaseHighlighters();
    }
  }

  @NotNull
  private Map<Annotator, Set<Language>> calcSupportedLanguages(@NotNull List<? extends PsiElement> elements) {
    Map<Annotator, Set<Language>> map = CollectionFactory.createCustomHashingStrategyMap(new HashingStrategy<>() {
      @Override
      public int hashCode(Annotator object) {
        return object.getClass().hashCode();
      }

      @Override
      public boolean equals(Annotator o1, Annotator o2) {
        return o1 == null || o2 == null ? o1==o2 : o1.getClass().equals(o2.getClass());
      }
    });
    Set<Language> languages = new HashSet<>();
    for (PsiElement element : elements) {
      Language language = element.getLanguage();
      addDialects(language, languages);
    }
    for (Language language : languages) {
      List<Annotator> templates = LanguageAnnotators.INSTANCE.allForLanguageOrAny(language);
      for (Annotator template : templates) {
        Set<Language> supportedLanguages = map.get(template);
        if (supportedLanguages == null) {
          supportedLanguages = new HashSet<>();
          map.put(cloneTemplate(template), supportedLanguages);
        }
        supportedLanguages.add(language);
      }
    }
    return map;
  }
  private static void addDialects(@NotNull Language language, @NotNull Set<? super Language> outProcessedLanguages) {
    if (outProcessedLanguages.add(language)) {
      Collection<Language> dialects = language.getTransitiveDialects();
      outProcessedLanguages.addAll(dialects);
    }
  }

  private void runAnnotator(@NotNull Annotator annotator,
                            @NotNull List<? extends PsiElement> insideThenOutside,
                            @NotNull Map<Annotator, Set<Language>> supportedLanguages, @NotNull HighlightInfoUpdater highlightInfoUpdater) {
    Set<Language> supported = supportedLanguages.get(annotator);
    if (supported.isEmpty()) {
      return;
    }
    // create AnnotationHolderImpl for each Annotator to make it immutable thread-safe converter to the corresponding HighlightInfo
    AnnotationSessionImpl.computeWithSession(myBatchMode, annotator, (AnnotationSessionImpl)myHighlightInfoHolder.getAnnotationSession(), annotationHolder -> {
      HighlightersRecycler emptyElementRecycler = new HighlightersRecycler(); // no need to call incinerate/release because it's always empty
      for (PsiElement element : insideThenOutside) {
        if (!supported.contains(element.getLanguage())) {
          continue;
        }
        if (myDumb && !DumbService.isDumbAware(annotator)) {
          continue;
        }
        ProgressManager.checkCanceled();
        int sizeBefore = annotationHolder.size();
        annotationHolder.runAnnotatorWithContext(element);
        int sizeAfter = annotationHolder.size();

        List<HighlightInfo> newInfos;
        if (sizeBefore == sizeAfter) {
          newInfos = List.of();
        }
        else {
          newInfos = new ArrayList<>(sizeAfter - sizeBefore);
          for (int i = sizeBefore; i < sizeAfter; i++) {
            Annotation annotation = annotationHolder.get(i);
            HighlightInfo info = HighlightInfo.fromAnnotation(annotator.getClass(), annotation, myBatchMode);
            info.setGroup(-1); // prevent DefaultHighlightProcessor from removing this info, we want to control it ourselves via `psiElementVisited` below
            addConvertedToHostInfo(info, newInfos);
            if (LOG.isDebugEnabled()) {
              LOG.debug("runAnnotator annotation="+annotation+" -> "+newInfos);
            }
            myAnnotatorStatisticsCollector.reportAnnotationProduced(annotator, annotation);
          }
          results.addAll(newInfos);
        }
        Document hostDocument = myPsiFile.getFileDocument();
        if (hostDocument instanceof DocumentWindow w) hostDocument = w.getDelegate();
        highlightInfoUpdater.psiElementVisited(annotator.getClass(), element, newInfos, hostDocument, myPsiFile, myProject,
                                               emptyElementRecycler, myHighlightingSession);
      }
      return null;
    });
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

      HighlightInfo patched = new HighlightInfo(info.forcedTextAttributes, info.forcedTextAttributesKey, info.type,
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

  private void addConvertedToHostInfo(@NotNull HighlightInfo info, @NotNull List<? super HighlightInfo> newInfos) {
    Document document = myPsiFile.getFileDocument();
    if (document instanceof DocumentWindow window) {
      PsiFile hostPsiFile = InjectedLanguageManager.getInstance(myProject).getTopLevelFile(myPsiFile);
      addPatchedInfos(info, myPsiFile, window, InjectedLanguageManager.getInstance(myProject), patched -> {
        if (HighlightInfoB.isAcceptedByFilters(patched, hostPsiFile)) {
          newInfos.add(patched);
        }
      });
    }
    else {
      if (HighlightInfoB.isAcceptedByFilters(info, myPsiFile)) {
        newInfos.add(info);
      }
    }
  }

  private static Annotator cloneTemplate(@NotNull Annotator template) {
    Annotator annotator;
    try {
      annotator = ReflectionUtil.newInstance(template.getClass());
    }
    catch (Exception e) {
      LOG.error(PluginException.createByClass(e, template.getClass()));
      return null;
    }
    return annotator;
  }

  @NotNull
  List<HighlightInfo> getResults() {
    return results;
  }
}