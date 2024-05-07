// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.codeInsight.daemon.RainbowVisitor;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInsight.highlighting.PassRunningAssert;
import com.intellij.concurrency.JobLauncher;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesScheme;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.serviceContainer.AlreadyDisposedException;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.TriConsumer;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

class HighlightVisitorRunner {
  private final @NotNull Supplier<? extends @NotNull HighlightVisitor @NotNull []> myHighlightVisitorProducer;

  HighlightVisitorRunner(@NotNull PsiFile psiFile, @Nullable TextAttributesScheme scheme, boolean runVisitors, boolean highlightErrorElements) {
    // "do not run visitors" here means "reduce the set of visitors down to DefaultHighlightVisitor", because it reports error elements
    myHighlightVisitorProducer = runVisitors ?
                                 () -> cloneAndFilterHighlightVisitors(psiFile, scheme) :
                                 () -> new HighlightVisitor[]{new DefaultHighlightVisitor(psiFile.getProject(), highlightErrorElements, false)};
  }

  private static final PassRunningAssert HIGHLIGHTING_PERFORMANCE_ASSERT =
    new PassRunningAssert("the expensive method should not be called inside the highlighting pass");
  static void assertHighlightingPassNotRunning() {
    HIGHLIGHTING_PERFORMANCE_ASSERT.assertPassNotRunning();
  }

  private static @NotNull HighlightVisitor @NotNull [] cloneAndFilterHighlightVisitors(@NotNull PsiFile psiFile, @Nullable TextAttributesScheme colorsScheme) {
    Project project = psiFile.getProject();
    HighlightVisitor[] visitors = HighlightVisitor.EP_HIGHLIGHT_VISITOR.getExtensions(project);
    DumbService dumbService = DumbService.getInstance(project);
    int o = 0;
    HighlightVisitor[] clones = new HighlightVisitor[visitors.length];
    for (HighlightVisitor visitor : visitors) {
      if (!dumbService.isUsableInCurrentContext(visitor)) {
        continue;
      }
      if (visitor instanceof RainbowVisitor
          && !RainbowHighlighter.isRainbowEnabledWithInheritance(colorsScheme, psiFile.getLanguage())) {
        continue;
      }
      if (visitor.suitableForFile(psiFile)) {
        HighlightVisitor cloned;
        // clone highlight visitor because
        // 1) HighlightVisitor API is non-reentrant (with its weird analyze() method which inits a bunch of instance fields inside) and
        // 2) the old visitor instance might still be running, albeit in the canceled state.
        // we could cling to the "do not clone visitor if there's only one instance is running" optimization, but it would require complex RCU readers-style machinery which would track the complete finish of the visitor before reusing it,
        // or we can just clone the visitor, which is not expensive, given that all overrides are just a single new() call.
        cloned = visitor.clone();
        assert cloned.getClass() == visitor.getClass() : visitor.getClass()+".clone() must return a copy of "+visitor.getClass()+"; but got: "+cloned+" ("+cloned.getClass()+")";
        clones[o++] = cloned;
      }
    }
    if (o == 0) {
      GeneralHighlightingPass.LOG.error("No visitors registered. all visitors:" + Arrays.toString(visitors));
    }
    return ArrayUtil.realloc(clones, o, HighlightVisitor.ARRAY_FACTORY);
  }

  void createHighlightVisitorsFor(@NotNull Consumer<? super HighlightVisitor[]> consumer) {
    // first ever queried HighlightVisitor can be used as is, but all further HighlightVisitors queried while the previous HighlightVisitor haven't finished, should be cloned to avoid reentrancy issues
    HighlightVisitor[] filtered = myHighlightVisitorProducer.get();
    consumer.consume(filtered);
  }

  private record VisitorInfo(@NotNull HighlightVisitor visitor,
                             @NotNull Set<PsiElement> skipParentsSet,
                             @NotNull HighlightInfoHolder holder) {}
  boolean runVisitors(@NotNull PsiFile psiFile,
                      @NotNull TextRange myRestrictRange,
                      @NotNull List<? extends PsiElement> elements1,
                      @NotNull LongList ranges1,
                      @NotNull List<? extends PsiElement> elements2,
                      @NotNull LongList ranges2,
                      HighlightVisitor @NotNull [] visitors,
                      boolean forceHighlightParents,
                      int chunkSize,
                      boolean myUpdateAll,
                      @NotNull Supplier<? extends HighlightInfoHolder> infoHolderProducer,
                      @NotNull TriConsumer<Object, ? super PsiElement, ? super List<? extends HighlightInfo>> resultSink) {
    List<? extends VisitorInfo> visitorInfos = ContainerUtil.map(visitors, v -> new VisitorInfo(v, new HashSet<>(), infoHolderProducer.get()));
    // first, run all visitors in parallel on all visible elements, then run all visitors in parallel on all invisible elements
    List<PsiElement> elements = ContainerUtil.concat(elements1, elements2);
    LongList ranges = new LongArrayList();
    ranges.addAll(ranges1);
    ranges.addAll(ranges2);
    return JobLauncher.getInstance().invokeConcurrentlyUnderProgress(visitorInfos, ProgressManager.getGlobalProgressIndicator(), visitorInfo -> {
      try {
        int[] sizeAfterRunVisitor = new int[1];
        boolean result = visitorInfo.visitor().analyze(psiFile, myUpdateAll, visitorInfo.holder(), () -> {
          reportOutOfRunVisitorInfos(visitorInfo, 0, ANALYZE_BEFORE_RUN_VISITOR_FAKE_PSI_ELEMENT, resultSink);
          runVisitor(psiFile, myRestrictRange, elements, ranges, chunkSize, visitorInfo.skipParentsSet(), visitorInfo.holder(), forceHighlightParents, visitorInfo.visitor(), resultSink);
          sizeAfterRunVisitor[0] = visitorInfo.holder().size();
        });
        reportOutOfRunVisitorInfos(visitorInfo, sizeAfterRunVisitor[0], ANALYZE_AFTER_RUN_VISITOR_FAKE_PSI_ELEMENT, resultSink);
        if (!result) {
          if (GeneralHighlightingPass.LOG.isDebugEnabled()) {
            GeneralHighlightingPass.LOG.debug("GHP: visitor " + visitorInfo.visitor() + "(" + visitorInfo.visitor().getClass() + ") returned false");
          }
        }
        return result;
      }
      catch (Exception e) {
        if (GeneralHighlightingPass.LOG.isDebugEnabled()) {
          GeneralHighlightingPass.LOG.debug("GHP: visitor " + visitorInfo.visitor() + "(" + visitorInfo.visitor().getClass() + ") threw " + ExceptionUtil.getThrowableText(e));
        }
        throw e;
      }
    });
  }

  private static final PsiElement ANALYZE_BEFORE_RUN_VISITOR_FAKE_PSI_ELEMENT = HighlightInfoUpdaterImpl.createFakePsiElement();
  private static final PsiElement ANALYZE_AFTER_RUN_VISITOR_FAKE_PSI_ELEMENT = HighlightInfoUpdaterImpl.createFakePsiElement();
  /**
   * report infos created outside the {@link #runVisitor} call (either before or after, inside the {@link HighlightVisitor#analyze} method), starting from the {@param fromIndex}
   */
  private static void reportOutOfRunVisitorInfos(@NotNull VisitorInfo visitorInfo, int fromIndex, @NotNull PsiElement fakePsiElement,
                                                 @NotNull TriConsumer<Object, ? super PsiElement, ? super List<? extends HighlightInfo>> resultSink) {
    HighlightInfoHolder holder = visitorInfo.holder();
    List<HighlightInfo> newInfos;
    if (holder.size() > fromIndex) {
      newInfos = new ArrayList<>(holder.size() - fromIndex);
      for (int i=fromIndex; i<holder.size(); i++) {
        HighlightInfo info = holder.get(i);
        newInfos.add(info);
      }
    }
    else {
      newInfos = List.of();
    }
    resultSink.accept(visitorInfo.visitor().getClass(), fakePsiElement, newInfos);
  }

   private static void runVisitor(@NotNull PsiFile psiFile,
                                  @NotNull TextRange myRestrictRange,
                                  @NotNull List<? extends PsiElement> elements,
                                  @NotNull LongList ranges,
                                  int chunkSize,
                                  @NotNull Set<? super PsiElement> skipParentsSet,
                                  @NotNull HighlightInfoHolder holder,
                                  boolean forceHighlightParents,
                                  @NotNull HighlightVisitor visitor,
                                  @NotNull TriConsumer<Object, ? super PsiElement, ? super List<? extends HighlightInfo>> resultSink) {
    boolean failed = false;
    int nextLimit = chunkSize;
    List<HighlightInfo> infos = new ArrayList<>();
    Class<? extends @NotNull HighlightVisitor> toolId = visitor.getClass();
    for (int i = 0; i < elements.size(); i++) {
      PsiElement psiElement = elements.get(i);
      ProgressManager.checkCanceled();

      PsiElement parent = psiElement.getParent();
      if (psiElement != psiFile && !skipParentsSet.isEmpty() && psiElement.getFirstChild() != null && skipParentsSet.contains(psiElement) && parent != null) {
        skipParentsSet.add(parent);
      }
      else {
        int oldSize = holder.size();
        try {
          visitor.visit(psiElement);
        }
        catch (ProcessCanceledException | IndexNotReadyException | AlreadyDisposedException e) {
          throw e;
        }
        catch (Exception e) {
          if (!failed) {
            GeneralHighlightingPass.LOG.error("In file: " + psiFile.getViewProvider().getVirtualFile(), e);
          }
          failed = true;
        }
        for (int j = oldSize; j < holder.size(); j++) {
          HighlightInfo info = holder.get(j);

          if (!myRestrictRange.contains(info)) continue;
          boolean isError = info.getSeverity() == HighlightSeverity.ERROR;
          if (isError) {
            if (!forceHighlightParents && parent != null) {
              skipParentsSet.add(parent);
            }
            //myErrorFound = true;
          }
          // if this highlight info range is contained inside the current element range we are visiting
          // that means we can clear this highlight as soon as visitors won't produce any highlights during visiting the same range next time.
          // We also know that we can remove a syntax error element.
          info.setVisitingTextRange(psiFile, psiFile.getFileDocument(), ranges.getLong(i));
          info.toolId = toolId;
          infos.add(info);
        }
      }
      resultSink.accept(toolId, psiElement, infos);
      infos.clear();
      if (i == nextLimit) {
        //advanceProgress(chunkSize);
        nextLimit = i + chunkSize;
      }
    }
    //advanceProgress(elements.size() - (nextLimit-chunkSize));
  }
}
