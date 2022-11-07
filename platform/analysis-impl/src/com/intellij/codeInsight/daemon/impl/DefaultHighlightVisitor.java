// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.AnnotatorStatisticsCollector;
import com.intellij.codeInsight.daemon.impl.analysis.ErrorQuickFixProvider;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.diagnostic.PluginException;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.LanguageUtil;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;


final class DefaultHighlightVisitor implements HighlightVisitor, DumbAware {
  private static final Logger LOG = Logger.getInstance(DefaultHighlightVisitor.class);
  private AnnotationHolderImpl myAnnotationHolder;
  private final Map<Language, List<Annotator>> myAnnotators = ConcurrentFactoryMap.createMap(language -> createAnnotators(language));
  private final Project myProject;
  private final boolean myHighlightErrorElements;
  private final boolean myRunAnnotators;
  private final DumbService myDumbService;
  private HighlightInfoHolder myHolder;
  private final boolean myBatchMode;
  private boolean myDumb;
  private final AnnotatorStatisticsCollector myAnnotatorStatisticsCollector = new AnnotatorStatisticsCollector();

  @SuppressWarnings("UnusedDeclaration")
  DefaultHighlightVisitor(@NotNull Project project) {
    this(project, true, true, false);
  }

  DefaultHighlightVisitor(@NotNull Project project,
                          boolean highlightErrorElements,
                          boolean runAnnotators,
                          boolean batchMode) {
    myProject = project;
    myHighlightErrorElements = highlightErrorElements;
    myRunAnnotators = runAnnotators;
    myDumbService = DumbService.getInstance(project);
    myBatchMode = batchMode;
  }

  @Override
  public boolean suitableForFile(@NotNull PsiFile file) {
    return true;
  }

  @Override
  public boolean analyze(@NotNull PsiFile file,
                         boolean updateWholeFile,
                         @NotNull HighlightInfoHolder holder,
                         @NotNull Runnable action) {
    myDumb = myDumbService.isDumb();
    myHolder = holder;

    myAnnotationHolder = new AnnotationHolderImpl(holder.getAnnotationSession(), myBatchMode) {
      @Override
      void queueToUpdateIncrementally() {
        if (!isEmpty()) {
          myAnnotatorStatisticsCollector.reportAnnotationProduced(myCurrentAnnotator, get(0));
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
      myHolder = null;
      myAnnotationHolder = null;
      myAnnotatorStatisticsCollector.reportAnalysisFinished(myProject, holder.getAnnotationSession(), file);
    }
    return true;
  }

  @Override
  public void visit(@NotNull PsiElement element) {
    if (myRunAnnotators) {
      runAnnotators(element);
    }
    if (element instanceof PsiErrorElement && myHighlightErrorElements) {
      visitErrorElement((PsiErrorElement)element);
    }
  }

  @SuppressWarnings("CloneDoesntCallSuperClone")
  @Override
  @NotNull
  public HighlightVisitor clone() {
    return new DefaultHighlightVisitor(myProject, myHighlightErrorElements, myRunAnnotators, myBatchMode);
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

  private void visitErrorElement(@NotNull PsiErrorElement element) {
    if (HighlightErrorFilter.EP_NAME.findFirstSafe(myProject, filter -> !filter.shouldHighlightErrorElement(element)) != null) {
      return;
    }

    myHolder.add(createErrorElementInfo(element));
  }

  private static HighlightInfo createErrorElementInfo(@NotNull PsiErrorElement element) {
    HighlightInfo.Builder builder = createInfoWithoutFixes(element);
    List<ErrorQuickFixProvider> providers = ErrorQuickFixProvider.EP_NAME.getExtensionList();
    for (ErrorQuickFixProvider provider : providers) {
      provider.registerErrorQuickFix(element, builder);
    }
    HighlightInfo info = builder.create();
    if (info != null) {
      for (ErrorQuickFixProvider provider : providers) {
        provider.registerErrorQuickFix(element, info);
      }
    }
    return info;
  }

  @NotNull
  private static HighlightInfo.Builder createInfoWithoutFixes(@NotNull PsiErrorElement element) {
    TextRange range = element.getTextRange();
    String errorDescription = element.getErrorDescription();
    if (!range.isEmpty()) {
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(errorDescription);
    }
    int offset = range.getStartOffset();
    PsiFile containingFile = element.getContainingFile();
    int fileLength = containingFile.getTextLength();
    FileViewProvider viewProvider = containingFile.getViewProvider();
    PsiElement elementAtOffset = viewProvider.findElementAt(offset, LanguageUtil.getRootLanguage(element));
    String text = elementAtOffset == null ? null : elementAtOffset.getText();
    if (offset < fileLength && text != null && !StringUtil.startsWithChar(text, '\n') && !StringUtil.startsWithChar(text, '\r')) {
      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(offset, offset + 1);
      builder.descriptionAndTooltip(errorDescription);
      return builder;
    }
    int start;
    int end;
    if (offset > 0) {
      start = offset/* - 1*/;
      end = offset;
    }
    else {
      start = offset;
      end = offset < fileLength ? offset + 1 : offset;
    }
    HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element, start, end);
    builder.descriptionAndTooltip(errorDescription);
    builder.endOfLine();
    return builder;
  }

  @NotNull
  private List<Annotator> cloneTemplates(@NotNull Collection<? extends Annotator> templates) {
    List<Annotator> result = new ArrayList<>(templates.size());
    for (Annotator template : templates) {
      Annotator annotator;
      try {
        annotator = ReflectionUtil.newInstance(template.getClass());
      }
      catch (Exception e) {
        LOG.error(PluginException.createByClass(e, template.getClass()));
        continue;
      }
      result.add(annotator);
      myAnnotatorStatisticsCollector.reportNewAnnotatorCreated(annotator);
    }
    return result;
  }

  @NotNull
  private List<Annotator> createAnnotators(@NotNull Language language) {
    return cloneTemplates(LanguageAnnotators.INSTANCE.allForLanguageOrAny(language));
  }
}