// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.codeInsight.daemon.RainbowVisitor;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInsight.highlighting.PassRunningAssert;
import com.intellij.concurrency.JobLauncher;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.TextAttributesScheme;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Consumer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;

class HighlightVisitorRunner {
  @NotNull private final PsiFile myPsiFile;
  @Nullable private final TextAttributesScheme myScheme;
  private final boolean myRunVisitors;
  private final boolean myHighlightErrorElements;

  HighlightVisitorRunner(@NotNull PsiFile psiFile, @Nullable TextAttributesScheme scheme, boolean runVisitors, boolean highlightErrorElements) {
    myPsiFile = psiFile;
    myScheme = scheme;
    myRunVisitors = runVisitors;
    myHighlightErrorElements = highlightErrorElements;
  }

  private static final PassRunningAssert HIGHLIGHTING_PERFORMANCE_ASSERT =
    new PassRunningAssert("the expensive method should not be called inside the highlighting pass");
  static void assertHighlightingPassNotRunning() {
    HIGHLIGHTING_PERFORMANCE_ASSERT.assertPassNotRunning();
  }

  private @NotNull HighlightVisitor @NotNull [] cloneAndFilterHighlightVisitors(@NotNull PsiFile psiFile, @Nullable TextAttributesScheme colorsScheme) {
    Project project = psiFile.getProject();
    // "!runVisitors" here means "reduce the set of visitors down to DefaultHighlightVisitor", because we should report error elements
    List<HighlightVisitor> visitors = myRunVisitors ? HighlightVisitor.EP_HIGHLIGHT_VISITOR.getExtensionList(project) : List.of();
    List<HighlightVisitor> clones = new ArrayList<>(visitors.size()+1);
    DumbService dumbService = DumbService.getInstance(project);
    boolean defaultHighlightVisitorAdded = false; // or a visitor superseding DefaultHighlightVisitor
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
        assert !(cloned instanceof DefaultHighlightVisitor) : "DefaultHighlightVisitor must not be registered as a regular <highlightVisitor>; instead, this method ensures it (or some other visitor superseding DefaultHighlightVisitor) is always in the list";
        defaultHighlightVisitorAdded |= cloned.supersedesDefaultHighlighter();
        clones.add(cloned);
      }
    }
    if (!defaultHighlightVisitorAdded && myHighlightErrorElements) {
      clones.add(new DefaultHighlightVisitor(project));
    }
    return clones.toArray(HighlightVisitor.EMPTY_ARRAY);
  }

  void createHighlightVisitorsFor(@NotNull Consumer<? super HighlightVisitor[]> consumer) {
    HighlightVisitor[] filtered = cloneAndFilterHighlightVisitors(myPsiFile, myScheme);
    consumer.consume(filtered);
  }

  private record VisitorInfo(@NotNull HighlightVisitor visitor,
                             @NotNull Set<PsiElement> skipParentsSet,
                             @NotNull HighlightInfoHolder holder) {}
  boolean runVisitors(@NotNull PsiFile psiFile,
                      @NotNull List<? extends PsiElement> elements1,
                      @NotNull List<? extends PsiElement> elements2,
                      HighlightVisitor @NotNull [] visitors,
                      boolean forceHighlightParents,
                      int chunkSize,
                      boolean myUpdateAll,
                      @NotNull Supplier<? extends HighlightInfoHolder> infoHolderProducer,
                      @NotNull ResultSink resultSink) {
    List<VisitorInfo> visitorInfos = ContainerUtil.map(visitors, v -> new VisitorInfo(v, new HashSet<>(), infoHolderProducer.get()));
    ProgressIndicator progress = ProgressIndicatorProvider.getGlobalProgressIndicator();
    if (GeneralHighlightingPass.LOG.isTraceEnabled()) {
      GeneralHighlightingPass.LOG.trace("HighlightVisitorRunner: visitors: " + Arrays.toString(visitors)+"; psiFile="+psiFile+"; progress="+progress);
    }
    boolean res = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(visitorInfos, progress, visitorInfo -> {
        HighlightVisitor visitor = visitorInfo.visitor();
        if (GeneralHighlightingPass.LOG.isTraceEnabled()) {
          GeneralHighlightingPass.LOG.trace("HighlightVisitorRunner: running visitor: " + visitor+"("+visitor.getClass()+"); psiFile="+psiFile+"; "+Thread.currentThread()+"; progress="+progress);
        }
        try {
          int[] sizeAfterRunVisitor = new int[1];
          HighlightInfoHolder holder = visitorInfo.holder();
          boolean result = visitor.analyze(psiFile, myUpdateAll, holder, () -> {
            reportOutOfRunVisitorInfos(0, ANALYZE_BEFORE_RUN_VISITOR_FAKE_PSI_ELEMENT, holder, visitor, resultSink);
            runVisitor(psiFile, elements1, chunkSize, visitorInfo.skipParentsSet(), holder, forceHighlightParents, visitor, resultSink);
            runVisitor(psiFile, elements2, chunkSize, visitorInfo.skipParentsSet(), holder, forceHighlightParents, visitor, resultSink);
            sizeAfterRunVisitor[0] = holder.size();
          });
          reportOutOfRunVisitorInfos(sizeAfterRunVisitor[0], ANALYZE_AFTER_RUN_VISITOR_FAKE_PSI_ELEMENT, holder, visitor, resultSink);
          if (GeneralHighlightingPass.LOG.isTraceEnabled()) {
            GeneralHighlightingPass.LOG.trace("HighlightVisitorRunner: visitor finished " + visitor + "(" + visitor.getClass() + ") progress=" + progress+
                                              (result ? "" : " returned false") + "; holder: "+holder.size()+" results"+"; "+Thread.currentThread());
          }
          return result;
        }
        catch (CancellationException e) {
          throw e;
        }
        catch (Exception e) {
          if (GeneralHighlightingPass.LOG.isTraceEnabled()) {
            GeneralHighlightingPass.LOG.trace("GHP: visitor " + visitor + "(" + visitor.getClass() + ") threw " + ExceptionUtil.getThrowableText(e)+"; "+Thread.currentThread()+"; progress="+progress);
          }
          throw e;
        }
      });
    if (GeneralHighlightingPass.LOG.isTraceEnabled()) {
      GeneralHighlightingPass.LOG.trace("HighlightVisitorRunner: all visitors ran; result="+res+" visitorInfos="+visitorInfos+"; "+Thread.currentThread()+"; progress="+progress);
    }
    return res;
  }

  private static final PsiElement ANALYZE_BEFORE_RUN_VISITOR_FAKE_PSI_ELEMENT = HighlightInfoUpdaterImpl.createFakePsiElement("ANALYZE_BEFORE_RUN_VISITOR");
  private static final PsiElement ANALYZE_AFTER_RUN_VISITOR_FAKE_PSI_ELEMENT = HighlightInfoUpdaterImpl.createFakePsiElement("ANALYZE_AFTER_RUN_VISITOR");
  /**
   * report infos created outside the {@link #runVisitor} call (either before or after, inside the {@link HighlightVisitor#analyze} method), starting from the {@param fromIndex}
   */
  private static void reportOutOfRunVisitorInfos(int fromIndex,
                                                 @NotNull PsiElement fakePsiElement,
                                                 @NotNull HighlightInfoHolder holder,
                                                 @NotNull HighlightVisitor visitor,
                                                 @NotNull ResultSink resultSink) {
    List<HighlightInfo> newInfos;
    if (holder.size() > fromIndex) {
      newInfos = new ArrayList<>(holder.size() - fromIndex);
      Class<? extends @NotNull HighlightVisitor> toolId = visitor.getClass();
      for (int i = fromIndex; i < holder.size(); i++) {
        HighlightInfo info = holder.get(i);
        newInfos.add(info);
        info.toolId = toolId;
        info.setGroup(HighlightInfoUpdaterImpl.MANAGED_HIGHLIGHT_INFO_GROUP);
      }
    }
    else {
      newInfos = List.of();
    }
    resultSink.accept(visitor.getClass(), fakePsiElement, newInfos);
  }

  private static void runVisitor(@NotNull PsiFile psiFile,
                                 @NotNull List<? extends PsiElement> elements,
                                 int chunkSize,
                                 @NotNull Set<? super PsiElement> skipParentsSet,
                                 @NotNull HighlightInfoHolder holder,
                                 boolean forceHighlightParents,
                                 @NotNull HighlightVisitor visitor,
                                 @NotNull ResultSink resultSink) {
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
        catch (IndexNotReadyException e) {
          break;
        }
        catch (Exception e) {
          if (Logger.shouldRethrow(e)) {
            throw e;
          }
          if (!failed) {
            GeneralHighlightingPass.LOG.error("In file: " + psiFile.getViewProvider().getVirtualFile(), e);
          }
          failed = true;
        }
        for (int j = oldSize; j < holder.size(); j++) {
          HighlightInfo info = holder.get(j);

          boolean isError = info.getSeverity() == HighlightSeverity.ERROR;
          if (isError) {
            if (!forceHighlightParents && parent != null) {
              skipParentsSet.add(parent);
            }
          }
          info.toolId = toolId;
          info.setGroup(HighlightInfoUpdaterImpl.MANAGED_HIGHLIGHT_INFO_GROUP);
          infos.add(info);
        }
      }
      resultSink.accept(toolId, psiElement, infos);
      if (!infos.isEmpty()) {
        infos.clear();
      }
      if (i == nextLimit) {
        //advanceProgress(chunkSize);
        nextLimit = i + chunkSize;
      }
    }
    //advanceProgress(elements.size() - (nextLimit-chunkSize));
  }
}
