// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.AnnotatorStatisticsCollector;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.concurrency.JobLauncher;
import com.intellij.diagnostic.PluginException;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedFileViewProvider;
import com.intellij.util.Processor;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.*;

final class AnnotatorRunner {
  private static final Logger LOG = Logger.getInstance(AnnotatorRunner.class);
  private final Project myProject;
  private final PsiFile myPsiFile;
  private final AnnotationSession myAnnotationSession;
  private final DumbService myDumbService;
  private final boolean myBatchMode;
  private final AnnotatorStatisticsCollector myAnnotatorStatisticsCollector = new AnnotatorStatisticsCollector();
  private final List<HighlightInfo> results = Collections.synchronizedList(new ArrayList<>());

  AnnotatorRunner(@NotNull PsiFile psiFile,
                  boolean batchMode,
                  @NotNull AnnotationSession annotationSession) {
    myProject = psiFile.getProject();
    myPsiFile = psiFile;
    myAnnotationSession = annotationSession;
    myDumbService = DumbService.getInstance(myProject);
    myBatchMode = batchMode;
  }

  // run annotators on PSI elements inside/outside while running `runnable` in parallel
  boolean runAnnotatorsAsync(@NotNull List<? extends PsiElement> inside,
                             @NotNull List<? extends PsiElement> outside,
                             @NotNull Runnable runnable,
                             @NotNull ResultSink resultSink) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    DaemonProgressIndicator indicator = GlobalInspectionContextBase.assertUnderDaemonProgress();

    // TODO move inside Divider to calc only once
    List<PsiElement> insideThenOutside = ContainerUtil.concat(inside, outside);
    Map<Annotator, Set<Language>> supportedLanguages = calcSupportedLanguages(insideThenOutside);
    Processor<? super Annotator> processor = annotator ->
      ApplicationManagerEx.getApplicationEx().tryRunReadAction(() -> runAnnotator(annotator, insideThenOutside, supportedLanguages, resultSink));
    boolean result = JobLauncher.getInstance().processConcurrentlyAsync(indicator, new ArrayList<>(supportedLanguages.keySet()), processor, runnable);
    myAnnotatorStatisticsCollector.reportAnalysisFinished(myProject, myAnnotationSession, myPsiFile);
    return result;
  }

  @NotNull
  private static Map<Annotator, Set<Language>> calcSupportedLanguages(@NotNull List<? extends PsiElement> elements) {
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
                            @NotNull Map<Annotator, Set<Language>> supportedLanguages,
                            @NotNull ResultSink result) {
    Set<Language> supported = supportedLanguages.get(annotator);
    if (supported.isEmpty()) {
      return;
    }
    // create AnnotationHolderImpl for each Annotator to make it immutable thread-safe converter to the corresponding HighlightInfo
    AnnotationSessionImpl.computeWithSession(myBatchMode, annotator, myAnnotationSession, annotationHolder -> {
      for (PsiElement psiElement : insideThenOutside) {
        if (!supported.contains(psiElement.getLanguage())) {
          continue;
        }
        if (!myDumbService.isUsableInCurrentContext(annotator)) {
          continue;
        }
        ProgressManager.checkCanceled();
        int sizeBefore = annotationHolder.size();
        annotationHolder.runAnnotatorWithContext(psiElement);
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
            if (myPsiFile.getViewProvider() instanceof InjectedFileViewProvider) {
              info.markFromInjection();
            }
            addConvertedToHostInfo(info, newInfos);
            if (LOG.isDebugEnabled()) {
              LOG.debug("runAnnotator "+annotator+"; annotation="+annotation+" -> "+newInfos);
            }
            myAnnotatorStatisticsCollector.reportAnnotationProduced(annotator, annotation);
          }
          results.addAll(newInfos);
        }
        result.accept(annotator.getClass(), psiElement, newInfos);
      }
      return null;
    });
  }

  private static void addPatchedInfos(@NotNull HighlightInfo injectedInfo,
                                      @NotNull PsiFile injectedPsi,
                                      @NotNull DocumentWindow documentWindow,
                                      @NotNull Collection<? super HighlightInfo> outHostInfos) {
    TextRange infoRange = TextRange.create(injectedInfo);
    InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(injectedPsi.getProject());
    List<TextRange> editables = injectedLanguageManager.intersectWithAllEditableFragments(injectedPsi, infoRange);
    for (TextRange editable : editables) {
      TextRange hostRange = documentWindow.injectedToHost(editable);

      boolean isAfterEndOfLine = injectedInfo.isAfterEndOfLine();
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

      // create manually to avoid extra call to HighlightInfoFilter.accept() in HighlightInfo.Builder.create()
      HighlightInfo patched = new HighlightInfo(injectedInfo.forcedTextAttributes, injectedInfo.forcedTextAttributesKey, injectedInfo.type,
                          hostRange.getStartOffset(), hostRange.getEndOffset(),
                          injectedInfo.getDescription(), injectedInfo.getToolTip(), injectedInfo.getSeverity(), isAfterEndOfLine, null,
                          false, 0, injectedInfo.getProblemGroup(), injectedInfo.toolId, injectedInfo.getGutterIconRenderer(), injectedInfo.getGroup(), injectedInfo.unresolvedReference);
      patched.setHint(injectedInfo.hasHint());

      injectedInfo.findRegisteredQuickFix((descriptor, quickfixTextRange) -> {
        List<TextRange> editableQF = injectedLanguageManager.intersectWithAllEditableFragments(injectedPsi, quickfixTextRange);
        for (TextRange editableRange : editableQF) {
          TextRange hostEditableRange = documentWindow.injectedToHost(editableRange);
          patched.registerFix(descriptor.getAction(), descriptor.myOptions, descriptor.getDisplayName(), hostEditableRange, descriptor.myKey);
        }
        return null;
      });
      patched.markFromInjection();
      outHostInfos.add(patched);
    }
  }

  private void addConvertedToHostInfo(@NotNull HighlightInfo info, @NotNull List<? super HighlightInfo> newInfos) {
    if (HighlightInfoB.isAcceptedByFilters(info, myPsiFile)) {
      if (info.isFromInjection() && myPsiFile.getFileDocument() instanceof DocumentWindow window) {
        addPatchedInfos(info, myPsiFile, window, newInfos);
      }
      else {
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
}