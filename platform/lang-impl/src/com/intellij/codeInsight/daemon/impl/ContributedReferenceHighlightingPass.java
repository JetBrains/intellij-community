// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.lang.ContributedReferencesAnnotators;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesScheme;
import com.intellij.openapi.paths.WebReference;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.CommonProcessors;
import com.intellij.util.containers.ConcurrentFactoryMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass.SHOULD_HIGHLIGHT_FILTER;

final class ContributedReferenceHighlightingPass extends ProgressableTextEditorHighlightingPass {
  final boolean myUpdateAll;
  final @NotNull ProperTextRange myPriorityRange;

  final List<HighlightInfo> myHighlights = new ArrayList<>();
  final EditorColorsScheme myGlobalScheme;

  ContributedReferenceHighlightingPass(@NotNull PsiFile file,
                                       @NotNull Document document,
                                       int startOffset,
                                       int endOffset,
                                       boolean updateAll,
                                       @NotNull ProperTextRange priorityRange,
                                       @Nullable Editor editor,
                                       @NotNull HighlightInfoProcessor highlightInfoProcessor) {
    super(file.getProject(), document, getPresentableNameText(), file, editor, TextRange.create(startOffset, endOffset), true,
          highlightInfoProcessor);
    myUpdateAll = updateAll;
    myPriorityRange = priorityRange;

    PsiUtilCore.ensureValid(file);

    // initial guess to show correct progress in the traffic light icon
    setProgressLimit(document.getTextLength() / 2); // approx number of PSI elements = file length/2
    myGlobalScheme = editor != null ? editor.getColorsScheme() : EditorColorsManager.getInstance().getGlobalScheme();
  }

  @Override
  public @NotNull List<HighlightInfo> getInfos() {
    return new ArrayList<>(myHighlights);
  }

  private static @Nls String getPresentableNameText() {
    return AnalysisBundle.message("pass.contributed.references");
  }

  @Override
  protected void collectInformationWithProgress(@NotNull ProgressIndicator progress) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    List<HighlightInfo> resultInside = new ArrayList<>(100);
    List<HighlightInfo> resultOutside = new ArrayList<>(100);

    List<Divider.DividedElements> allDivided = new ArrayList<>();
    Divider.divideInsideAndOutsideAllRoots(myFile, myRestrictRange, myPriorityRange, SHOULD_HIGHLIGHT_FILTER,
                                           new CommonProcessors.CollectProcessor<>(allDivided));

    List<PsiElement> contributedReferenceHostsInside = allDivided.stream()
      .flatMap(d -> d.inside.stream())
      .filter(WebReference::isWebReferenceWorthy)
      .collect(Collectors.toList());

    List<PsiElement> contributedReferenceHostsOutside = allDivided.stream()
      .flatMap(d -> d.outside.stream())
      .filter(WebReference::isWebReferenceWorthy)
      .collect(Collectors.toList());

    setProgressLimit(contributedReferenceHostsInside.size() + contributedReferenceHostsOutside.size());

    processContributedReferencesHosts(contributedReferenceHostsInside, highlightInfo -> {
      queueInfoToUpdateIncrementally(highlightInfo, getId());
      synchronized (myHighlights) {
          resultInside.add(highlightInfo);
      }
    });

    boolean priorityIntersectionHasElements = myPriorityRange.intersectsStrict(myRestrictRange);
    if ((!contributedReferenceHostsInside.isEmpty() || !resultInside.isEmpty()) && priorityIntersectionHasElements) { // do not apply when there were no elements to highlight
      myHighlightInfoProcessor.highlightsInsideVisiblePartAreProduced(myHighlightingSession, getEditor(), resultInside, myPriorityRange, myRestrictRange, getId());
    }

    processContributedReferencesHosts(contributedReferenceHostsOutside, highlightInfo -> {
      queueInfoToUpdateIncrementally(highlightInfo, getId());
      synchronized (myHighlights) {
        resultOutside.add(highlightInfo);
      }
    });

    myHighlights.addAll(resultInside);
    myHighlights.addAll(resultOutside);

    resultOutside.addAll(resultInside);

    myHighlightInfoProcessor.highlightsOutsideVisiblePartAreProduced(myHighlightingSession, getEditor(),
                                                                     resultOutside, myPriorityRange,
                                                                     myRestrictRange, getId());
  }

  @Override
  protected void applyInformationWithProgress() {
    // do nothing
  }

  private void processContributedReferencesHosts(@NotNull List<PsiElement> contributedReferenceHosts,
                                                 @NotNull Consumer<? super HighlightInfo> outInfos) {
    if (contributedReferenceHosts.isEmpty()) return;

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

  void queueInfoToUpdateIncrementally(@NotNull HighlightInfo info, int group) {
    myHighlightInfoProcessor.infoIsAvailable(myHighlightingSession, info, myPriorityRange, myRestrictRange, group);
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

          holder.queueToUpdateIncrementally();
        }
      }
    }
  }

  private static @NotNull List<Annotator> createAnnotators(@NotNull Language language) {
    return ContributedReferencesAnnotators.INSTANCE.allForLanguageOrAny(language);
  }
}