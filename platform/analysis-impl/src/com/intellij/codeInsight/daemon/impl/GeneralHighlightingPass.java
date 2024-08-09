// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager;
import com.intellij.codeInsight.problems.ProblemImpl;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesScheme;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.Problem;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

sealed class GeneralHighlightingPass extends ProgressableTextEditorHighlightingPass implements DumbAware
  permits ChameleonSyntaxHighlightingPass, NasueousGeneralHighlightingPass {
  static final Logger LOG = Logger.getInstance(GeneralHighlightingPass.class);
  private static final Key<Boolean> HAS_ERROR_ELEMENT = Key.create("HAS_ERROR_ELEMENT");
  static final Predicate<? super PsiFile> SHOULD_HIGHLIGHT_FILTER = file -> {
    HighlightingLevelManager manager = HighlightingLevelManager.getInstance(file.getProject());
    return manager != null && manager.shouldHighlight(file);
  };
  private static final Random RESTART_DAEMON_RANDOM = new Random();

  private final boolean myUpdateAll;
  final @NotNull ProperTextRange myPriorityRange;

  final List<HighlightInfo> myHighlights = Collections.synchronizedList(new ArrayList<>());

  protected volatile boolean myHasErrorElement;
  private volatile boolean myHasErrorSeverity;
  private volatile boolean myOldErrorFound;
  private final boolean myRunAnnotators;
  private final HighlightInfoUpdater myHighlightInfoUpdater;
  private final HighlightVisitorRunner myHighlightVisitorRunner;

  GeneralHighlightingPass(@NotNull PsiFile psiFile,
                          @NotNull Document document,
                          int startOffset,
                          int endOffset,
                          boolean updateAll,
                          @NotNull ProperTextRange priorityRange,
                          @Nullable Editor editor,
                          boolean runAnnotators,
                          boolean runVisitors,
                          boolean highlightErrorElements,
                          @NotNull HighlightInfoUpdater highlightInfoUpdater) {
    super(psiFile.getProject(), document, AnalysisBundle.message("pass.syntax"), psiFile, editor, TextRange.create(startOffset, endOffset), true, HighlightInfoProcessor.getEmpty());
    myUpdateAll = updateAll;
    myPriorityRange = priorityRange;
    myRunAnnotators = runAnnotators;
    myHighlightInfoUpdater = highlightInfoUpdater;

    PsiUtilCore.ensureValid(psiFile);
    boolean wholeFileHighlighting = isWholeFileHighlighting();
    myHasErrorElement = !wholeFileHighlighting && Boolean.TRUE.equals(getFile().getUserData(HAS_ERROR_ELEMENT));
    DaemonCodeAnalyzerEx daemonCodeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(myProject);
    FileStatusMap fileStatusMap = daemonCodeAnalyzer.getFileStatusMap();
    myOldErrorFound = !wholeFileHighlighting && fileStatusMap.wasErrorFound(getDocument());

    // initial guess to show correct progress in the traffic light icon
    setProgressLimit(document.getTextLength()/2); // approx number of PSI elements = file length/2
    EditorColorsScheme globalScheme = editor != null ? editor.getColorsScheme() : EditorColorsManager.getInstance().getGlobalScheme();
    myHighlightVisitorRunner = new HighlightVisitorRunner(psiFile, globalScheme, runVisitors, highlightErrorElements);
  }

  private @NotNull PsiFile getFile() {
    return myFile;
  }
  static void assertHighlightingPassNotRunning() {
    HighlightVisitorRunner.assertHighlightingPassNotRunning();
  }

  @Override
  protected void collectInformationWithProgress(@NotNull ProgressIndicator progress) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();

    DaemonCodeAnalyzerEx daemonCodeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(myProject);
    myHighlightVisitorRunner.createHighlightVisitorsFor(filteredVisitors->{
      List<Divider.DividedElements> dividedElements = new ArrayList<>();
      List<Divider.DividedElements> notVisitableElements = new ArrayList<>();
      Divider.divideInsideAndOutsideAllRoots(getFile(), myRestrictRange, myPriorityRange, psiFile -> true,
                                             elements -> {
                                               if (SHOULD_HIGHLIGHT_FILTER.test(elements.psiRoot())) {
                                                 dividedElements.add(elements);
                                               }
                                               else {
                                                 notVisitableElements.add(elements);
                                               }
                                               return false;
                                             });
      List<PsiElement> allInsideElements = ContainerUtil.concat(ContainerUtil.map(dividedElements,
                                            dividedForRoot -> {
                                              List<? extends PsiElement> inside = dividedForRoot.inside();
                                              PsiElement lastInside = ContainerUtil.getLastItem(inside);
                                              return lastInside instanceof PsiFile && !(lastInside instanceof PsiCodeFragment) ? inside
                                                .subList(0, inside.size() - 1) : inside;
                                            }));


      List<LongList> map = ContainerUtil.map(dividedElements,
                                             dividedForRoot -> {
                                               LongList insideRanges = dividedForRoot.insideRanges();
                                               PsiElement lastInside = ContainerUtil.getLastItem(dividedForRoot.inside());
                                               return lastInside instanceof PsiFile && !(lastInside instanceof PsiCodeFragment)
                                                      ? insideRanges.subList(0, insideRanges.size() - 1) : insideRanges;
                                             });

      LongList allInsideRanges = ContainerUtil.reduce(map, new LongArrayList(map.isEmpty() ? 1 : map.get(0).size()), (l1, l2)->{ l1.addAll(l2); return l1;});

      List<PsiElement> allOutsideElements = ContainerUtil.concat(ContainerUtil.map(dividedElements,
                                                  dividedForRoot -> {
                                                    List<? extends PsiElement> outside = dividedForRoot.outside();
                                                    PsiElement lastInside = ContainerUtil.getLastItem(dividedForRoot.inside());
                                                    return lastInside instanceof PsiFile && !(lastInside instanceof PsiCodeFragment) ? ContainerUtil.append(outside,
                                                      lastInside) : outside;
                                                  }));
      List<LongList> map1 = ContainerUtil.map(dividedElements,
                                              dividedForRoot -> {
                                                LongList outsideRanges = dividedForRoot.outsideRanges();
                                                PsiElement lastInside = ContainerUtil.getLastItem(dividedForRoot.inside());
                                                long lastInsideRange = dividedForRoot.insideRanges().isEmpty() ? -1 : dividedForRoot.insideRanges().getLong(dividedForRoot.insideRanges().size()-1);
                                                if (lastInside instanceof PsiFile && !(lastInside instanceof PsiCodeFragment) && lastInsideRange != -1) {
                                                  LongArrayList r = new LongArrayList(outsideRanges);
                                                  r.add(lastInsideRange);
                                                  return r;
                                                }
                                                return outsideRanges;
                                              });
      LongList allOutsideRanges = ContainerUtil.reduce(map1, new LongArrayList(map1.isEmpty() ? 1 : map1.get(0).size()), (l1, l2)->{ l1.addAll(l2); return l1;});


      setProgressLimit(allInsideElements.size() + allOutsideElements.size());

      boolean forceHighlightParents = forceHighlightParents();

      // Remove obsolete infos for invalid psi elements.
      // Unfortunately, the majority of PSI implementations are very bad at increment reparsing, meaning the smallest document change
      // leads (non-optimally) to many unrelated PSI elements being invalidated, and then the new PSI recreated in their place with identical structure.
      // If we removed these invalid elements eagerly, it would cause flicker because the newly created elements preempt the just removed invalid ones.
      // Hence, we defer the removal of invalid elements till the very end, in hope that these highlighters be reused by these new elements.
      // this optimization, however, could lead to an increased latency
      Consumer<? super ManagedHighlighterRecycler> recyclerConsumer = invalidPsiRecycler -> {
        // clear highlights generated by visitors called on psi elements no longer highlightable under the current highlighting level (e.g., when the file level was changed from "All Problems" to "None")
        for (Divider.DividedElements notVisitable : notVisitableElements) {
          for (PsiElement element : ContainerUtil.concat(notVisitable.inside(), notVisitable.outside())) {
            for (HighlightVisitor visitor : filteredVisitors) {
              myHighlightInfoUpdater.psiElementVisited(visitor.getClass(), element, List.of(), getDocument(), getFile(), myProject, getHighlightingSession(), invalidPsiRecycler);
            }
          }
        }
        boolean success = collectHighlights(allInsideElements, allInsideRanges, allOutsideElements, allOutsideRanges, filteredVisitors,
                                            forceHighlightParents, (toolId, psiElement, newInfos) -> {
            myHighlightInfoUpdater.psiElementVisited(toolId, psiElement, newInfos, getDocument(), getFile(), myProject, getHighlightingSession(), invalidPsiRecycler);
            myHighlights.addAll(newInfos);
            if (psiElement instanceof PsiErrorElement) {
              myHasErrorElement = true;
            }
            for (HighlightInfo info : newInfos) {
              if (info.getSeverity() == HighlightSeverity.ERROR) {
                myHasErrorSeverity = true;
                break;
              }
            }
        });
        if (success) {
          if (myUpdateAll) {
            daemonCodeAnalyzer.getFileStatusMap().setErrorFoundFlag(myProject, getDocument(), myHasErrorSeverity);
            reportErrorsToWolf(myHasErrorSeverity);
          }
        }
        else {
          cancelAndRestartDaemonLater(progress, myProject);
        }
      };
      if (myHighlightInfoUpdater instanceof HighlightInfoUpdaterImpl impl) {
        impl.runWithInvalidPsiRecycler(getHighlightingSession(), HighlightInfoUpdaterImpl.WhatTool.ANNOTATOR_OR_VISITOR, recyclerConsumer);
      }
      else {
        ManagedHighlighterRecycler.runWithRecycler(getHighlightingSession(), recyclerConsumer);
      }
    });
  }

  private boolean isWholeFileHighlighting() {
    return myUpdateAll && myRestrictRange.equalsToRange(0, getDocument().getTextLength());
  }

  @Override
  protected void applyInformationWithProgress() {
    ((HighlightingSessionImpl)getHighlightingSession()).applyFileLevelHighlightsRequests();
    getFile().putUserData(HAS_ERROR_ELEMENT, myHasErrorElement);
  }

  @Override
  public final @NotNull List<HighlightInfo> getInfos() {
    return myHighlights;
  }

  private boolean collectHighlights(@NotNull List<? extends PsiElement> elements1,
                                    @NotNull LongList ranges1,
                                    @NotNull List<? extends PsiElement> elements2,
                                    @NotNull LongList ranges2,
                                    HighlightVisitor @NotNull [] visitors,
                                    boolean forceHighlightParents,
                                    @NotNull ResultSink resultSink) {
    int chunkSize = Math.max(1, (elements1.size()+elements2.size()) / 100); // one percent precision is enough
    Runnable runnable = () -> myHighlightVisitorRunner.runVisitors(getFile(), myRestrictRange, elements1, ranges1, elements2, ranges2, visitors, forceHighlightParents, chunkSize,
                                                            myUpdateAll, () -> createInfoHolder(getFile()), resultSink);
    AnnotationSession session = AnnotationSessionImpl.create(getFile());
    setupAnnotationSession(session, myPriorityRange, myRestrictRange,
                           ((HighlightingSessionImpl)getHighlightingSession()).getMinimumSeverity());
    AnnotatorRunner annotatorRunner = myRunAnnotators ? new AnnotatorRunner(getFile(), false, session) : null;
    if (annotatorRunner == null) {
      runnable.run();
      return true;
    }
    return annotatorRunner.runAnnotatorsAsync(elements1, elements2, runnable, resultSink);
  }

  static final int POST_UPDATE_ALL = 5;
  private static final AtomicInteger RESTART_REQUESTS = new AtomicInteger();

  @TestOnly
  static boolean isRestartPending() {
    return RESTART_REQUESTS.get() > 0;
  }

  private static void cancelAndRestartDaemonLater(@NotNull ProgressIndicator progress, @NotNull Project project) throws ProcessCanceledException {
    RESTART_REQUESTS.incrementAndGet();
    progress.cancel();
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      RESTART_REQUESTS.decrementAndGet();
      if (!project.isDisposed()) {
        DaemonCodeAnalyzer.getInstance(project).restart();
      }
    }
    else {
      int delay = RESTART_DAEMON_RANDOM.nextInt(100);
      EdtExecutorService.getScheduledExecutorInstance().schedule(() -> {
        RESTART_REQUESTS.decrementAndGet();
        if (!project.isDisposed()) {
          DaemonCodeAnalyzer.getInstance(project).restart();
        }
      }, delay, TimeUnit.MILLISECONDS);
    }
    throw new ProcessCanceledException();
  }

  private boolean forceHighlightParents() {
    boolean forceHighlightParents = false;
    for(HighlightRangeExtension extension: HighlightRangeExtension.EP_NAME.getExtensionList()) {
      if (extension.isForceHighlightParents(getFile())) {
        forceHighlightParents = true;
        break;
      }
    }
    return forceHighlightParents;
  }


  protected @NotNull HighlightInfoHolder createInfoHolder(@NotNull PsiFile file) {
    HighlightInfoFilter[] filters = HighlightInfoFilter.EXTENSION_POINT_NAME.getExtensionList().toArray(HighlightInfoFilter.EMPTY_ARRAY);
    EditorColorsScheme actualScheme = getColorsScheme() == null ? EditorColorsManager.getInstance().getGlobalScheme() : getColorsScheme();
    HighlightInfoHolder holder = new HighlightInfoHolder(file, filters) {
      @Override
      public @NotNull TextAttributesScheme getColorsScheme() {
        return actualScheme;
      }

      @Override
      public boolean add(@Nullable HighlightInfo info) {
        boolean added;
        synchronized (this) {
          added = super.add(info);
        }
        if (info != null && added && info.getSeverity() == HighlightSeverity.ERROR) {
          myHasErrorSeverity = true;
        }
        return added;
      }
    };
    setupAnnotationSession(holder.getAnnotationSession(), myPriorityRange, myRestrictRange,
                           ((HighlightingSessionImpl)getHighlightingSession()).getMinimumSeverity());
    return holder;
  }

  static void setupAnnotationSession(@NotNull AnnotationSession annotationSession,
                                     @NotNull TextRange priorityRange,
                                     @NotNull TextRange highlightRange,
                                     @Nullable HighlightSeverity minimumSeverity) {
    ((AnnotationSessionImpl)annotationSession).setMinimumSeverity(minimumSeverity);
    ((AnnotationSessionImpl)annotationSession).setVR(priorityRange, highlightRange);
  }

  private void reportErrorsToWolf(boolean hasErrors) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    if (!getFile().getViewProvider().isPhysical()) return; // e.g. errors in evaluate expression
    Project project = getFile().getProject();
    if (!PsiManager.getInstance(project).isInProject(getFile())) return; // do not report problems in libraries
    VirtualFile file = getFile().getVirtualFile();
    if (file == null) return;

    List<Problem> problems = convertToProblems(getInfos(), file, myHasErrorElement);
    WolfTheProblemSolver wolf = WolfTheProblemSolver.getInstance(project);

    if (!hasErrors || isWholeFileHighlighting()) {
      wolf.reportProblems(file, problems);
    }
    else {
      wolf.weHaveGotProblems(file, problems);
    }
  }

  private static @NotNull List<Problem> convertToProblems(@NotNull Collection<? extends HighlightInfo> infos,
                                                          @NotNull VirtualFile file,
                                                          boolean hasErrorElement) {
    List<Problem> problems = new SmartList<>();
    for (HighlightInfo info : infos) {
      if (info.getSeverity() == HighlightSeverity.ERROR) {
        Problem problem = new ProblemImpl(file, info, hasErrorElement);
        problems.add(problem);
      }
    }
    return problems;
  }

  @Override
  public String toString() {
    return super.toString() + " updateAll="+myUpdateAll+" range="+myRestrictRange;
  }
}
