/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.RainbowVisitor;
import com.intellij.codeInsight.daemon.impl.analysis.CustomHighlightInfoHolder;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager;
import com.intellij.codeInsight.problems.ProblemImpl;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.Problem;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.*;
import com.intellij.psi.impl.search.PsiTodoSearchHelperImpl;
import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.search.TodoItem;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.CommonProcessors;
import com.intellij.util.NotNullProducer;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class GeneralHighlightingPass extends ProgressableTextEditorHighlightingPass implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass");
  private static final String PRESENTABLE_NAME = DaemonBundle.message("pass.syntax");
  private static final Key<Boolean> HAS_ERROR_ELEMENT = Key.create("HAS_ERROR_ELEMENT");
  static final Condition<PsiFile> SHOULD_HIGHLIGHT_FILTER = file -> HighlightingLevelManager.getInstance(file.getProject()).shouldHighlight(file);
  private static final Random RESTART_DAEMON_RANDOM = new Random();

  final boolean myUpdateAll;
  final ProperTextRange myPriorityRange;

  final List<HighlightInfo> myHighlights = new ArrayList<>();

  protected volatile boolean myHasErrorElement;
  private volatile boolean myErrorFound;
  final EditorColorsScheme myGlobalScheme;
  private volatile NotNullProducer<HighlightVisitor[]> myHighlightVisitorProducer = this::cloneHighlightVisitors;

  public GeneralHighlightingPass(@NotNull Project project,
                                 @NotNull PsiFile file,
                                 @NotNull Document document,
                                 int startOffset,
                                 int endOffset,
                                 boolean updateAll,
                                 @NotNull ProperTextRange priorityRange,
                                 @Nullable Editor editor,
                                 @NotNull HighlightInfoProcessor highlightInfoProcessor) {
    super(project, document, PRESENTABLE_NAME, file, editor, TextRange.create(startOffset, endOffset), true, highlightInfoProcessor);
    myUpdateAll = updateAll;
    myPriorityRange = priorityRange;

    PsiUtilCore.ensureValid(file);
    boolean wholeFileHighlighting = isWholeFileHighlighting();
    myHasErrorElement = !wholeFileHighlighting && Boolean.TRUE.equals(getFile().getUserData(HAS_ERROR_ELEMENT));
    final DaemonCodeAnalyzerEx daemonCodeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(myProject);
    FileStatusMap fileStatusMap = daemonCodeAnalyzer.getFileStatusMap();
    myErrorFound = !wholeFileHighlighting && fileStatusMap.wasErrorFound(getDocument());

    // initial guess to show correct progress in the traffic light icon
    setProgressLimit(document.getTextLength()/2); // approx number of PSI elements = file length/2
    myGlobalScheme = editor != null ? editor.getColorsScheme() : EditorColorsManager.getInstance().getGlobalScheme();
  }

  @NotNull
  private PsiFile getFile() {
    //noinspection ConstantConditions
    return myFile;
  }

  @NotNull
  @Override
  public Document getDocument() {
    //noinspection ConstantConditions
    return super.getDocument();
  }

  private static final Key<AtomicInteger> HIGHLIGHT_VISITOR_INSTANCE_COUNT = new Key<>("HIGHLIGHT_VISITOR_INSTANCE_COUNT");
  @NotNull
  private HighlightVisitor[] cloneHighlightVisitors() {
    int oldCount = incVisitorUsageCount(1);
    HighlightVisitor[] highlightVisitors = Extensions.getExtensions(HighlightVisitor.EP_HIGHLIGHT_VISITOR, myProject);
    if (oldCount != 0) {
      HighlightVisitor[] clones = new HighlightVisitor[highlightVisitors.length];
      for (int i = 0; i < highlightVisitors.length; i++) {
        HighlightVisitor highlightVisitor = highlightVisitors[i];
        HighlightVisitor cloned = highlightVisitor.clone();
        assert cloned.getClass() == highlightVisitor.getClass() : highlightVisitor.getClass()+".clone() must return a copy of "+highlightVisitor.getClass()+"; but got: "+cloned+" of "+cloned.getClass();
        clones[i] = cloned;
      }
      highlightVisitors = clones;
    }
    return highlightVisitors;
  }

  @NotNull
  private HighlightVisitor[] filterVisitors(@NotNull HighlightVisitor[] highlightVisitors, @NotNull PsiFile psiFile) {
    final List<HighlightVisitor> visitors = new ArrayList<>(highlightVisitors.length);
    List<HighlightVisitor> list = Arrays.asList(highlightVisitors);
    for (HighlightVisitor visitor : DumbService.getInstance(myProject).filterByDumbAwareness(list)) {
      if (visitor instanceof RainbowVisitor
          && !RainbowHighlighter.isRainbowEnabledWithInheritance(getColorsScheme(), psiFile.getLanguage())) {
        continue;
      }
      if (visitor.suitableForFile(psiFile)) {
        visitors.add(visitor);
      }
    }
    if (visitors.isEmpty()) {
      LOG.error("No visitors registered. list=" +
                list +
                "; all visitors are:" +
                Arrays.asList(Extensions.getExtensions(HighlightVisitor.EP_HIGHLIGHT_VISITOR, myProject)));
    }

    return visitors.toArray(new HighlightVisitor[visitors.size()]);
  }

  void setHighlightVisitorProducer(@NotNull NotNullProducer<HighlightVisitor[]> highlightVisitorProducer) {
    myHighlightVisitorProducer = highlightVisitorProducer;
  }

  @NotNull
  HighlightVisitor[] getHighlightVisitors(@NotNull PsiFile psiFile) {
    return filterVisitors(myHighlightVisitorProducer.produce(), psiFile);
  }

  // returns old value
  int incVisitorUsageCount(int delta) {
    AtomicInteger count = myProject.getUserData(HIGHLIGHT_VISITOR_INSTANCE_COUNT);
    if (count == null) {
      count = ((UserDataHolderEx)myProject).putUserDataIfAbsent(HIGHLIGHT_VISITOR_INSTANCE_COUNT, new AtomicInteger(0));
    }
    int old = count.getAndAdd(delta);
    assert old + delta >= 0 : old +";" + delta;
    return old;
  }

  @Override
  protected void collectInformationWithProgress(@NotNull final ProgressIndicator progress) {
    final List<HighlightInfo> outsideResult = new ArrayList<>(100);
    final List<HighlightInfo> insideResult = new ArrayList<>(100);

    final DaemonCodeAnalyzerEx daemonCodeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(myProject);
    final HighlightVisitor[] filteredVisitors = getHighlightVisitors(getFile());
    try {
      List<Divider.DividedElements> dividedElements = new ArrayList<>();
      Divider.divideInsideAndOutsideAllRoots(getFile(), myRestrictRange, myPriorityRange, SHOULD_HIGHLIGHT_FILTER,
                                             new CommonProcessors.CollectProcessor<>(dividedElements));
      List<PsiElement> allInsideElements = ContainerUtil.concat((List<List<PsiElement>>)ContainerUtil.map(dividedElements,
                                            dividedForRoot -> {
                                              List<PsiElement> inside = dividedForRoot.inside;
                                              PsiElement lastInside = ContainerUtil.getLastItem(inside);
                                              return lastInside instanceof PsiFile && !(lastInside instanceof PsiCodeFragment) ? inside
                                                .subList(0, inside.size() - 1) : inside;
                                            }));


      List<ProperTextRange> allInsideRanges = ContainerUtil.concat((List<List<ProperTextRange>>)ContainerUtil.map(dividedElements,
                                                  dividedForRoot -> {
                                                    List<ProperTextRange> insideRanges = dividedForRoot.insideRanges;
                                                    PsiElement lastInside = ContainerUtil.getLastItem(dividedForRoot.inside);
                                                    return lastInside instanceof PsiFile && !(lastInside instanceof PsiCodeFragment) ? insideRanges
                                                      .subList(0, insideRanges.size() - 1) : insideRanges;
                                                  }));

      List<PsiElement> allOutsideElements = ContainerUtil.concat((List<List<PsiElement>>)ContainerUtil.map(dividedElements,
                                                  dividedForRoot -> {
                                                    List<PsiElement> outside = dividedForRoot.outside;
                                                    PsiElement lastInside = ContainerUtil.getLastItem(dividedForRoot.inside);
                                                    return lastInside instanceof PsiFile && !(lastInside instanceof PsiCodeFragment) ? ContainerUtil.append(outside,
                                                      lastInside) : outside;
                                                  }));
      List<ProperTextRange> allOutsideRanges = ContainerUtil.concat((List<List<ProperTextRange>>)ContainerUtil.map(dividedElements,
                                                        dividedForRoot -> {
                                                          List<ProperTextRange> outsideRanges = dividedForRoot.outsideRanges;
                                                          PsiElement lastInside = ContainerUtil.getLastItem(dividedForRoot.inside);
                                                          ProperTextRange lastInsideRange = ContainerUtil.getLastItem(dividedForRoot.insideRanges);
                                                          return lastInside instanceof PsiFile && !(lastInside instanceof PsiCodeFragment) ? ContainerUtil.append(outsideRanges,
                                                                                                                lastInsideRange) : outsideRanges;
                                                        }));


      setProgressLimit((long)(allInsideElements.size()+allOutsideElements.size()));

      final boolean forceHighlightParents = forceHighlightParents();

      if (!isDumbMode()) {
        highlightTodos(getFile(), getDocument().getCharsSequence(), myRestrictRange.getStartOffset(), myRestrictRange.getEndOffset(), progress, myPriorityRange, insideResult,
                       outsideResult);
      }

      boolean success = collectHighlights(allInsideElements, allInsideRanges, allOutsideElements, allOutsideRanges, progress, filteredVisitors, insideResult, outsideResult, forceHighlightParents);

      if (success) {
        myHighlightInfoProcessor.highlightsOutsideVisiblePartAreProduced(myHighlightingSession, getEditor(),
                                                                         outsideResult, myPriorityRange,
                                                                         myRestrictRange, getId());

        if (myUpdateAll) {
          daemonCodeAnalyzer.getFileStatusMap().setErrorFoundFlag(myProject, getDocument(), myErrorFound);
        }
      }
      else {
        cancelAndRestartDaemonLater(progress, myProject);
      }
    }
    finally {
      incVisitorUsageCount(-1);
      myHighlights.addAll(insideResult);
      myHighlights.addAll(outsideResult);
    }
  }

  boolean isFailFastOnAcquireReadAction() {
    return true;
  }

  private boolean isWholeFileHighlighting() {
    return myUpdateAll && myRestrictRange.equalsToRange(0, getDocument().getTextLength());
  }

  @Override
  protected void applyInformationWithProgress() {
    getFile().putUserData(HAS_ERROR_ELEMENT, myHasErrorElement);

    if (myUpdateAll) {
      ((HighlightingSessionImpl)myHighlightingSession).applyInEDT(this::reportErrorsToWolf);
    }
  }

  @Override
  @NotNull
  public List<HighlightInfo> getInfos() {
    return new ArrayList<>(myHighlights);
  }

  private boolean collectHighlights(@NotNull final List<PsiElement> elements1,
                                    @NotNull final List<ProperTextRange> ranges1,
                                    @NotNull final List<PsiElement> elements2,
                                    @NotNull final List<ProperTextRange> ranges2,
                                    @NotNull final ProgressIndicator progress,
                                    @NotNull final HighlightVisitor[] visitors,
                                    @NotNull final List<HighlightInfo> insideResult,
                                    @NotNull final List<HighlightInfo> outsideResult,
                                    final boolean forceHighlightParents) {
    final Set<PsiElement> skipParentsSet = new THashSet<>();

    // TODO - add color scheme to holder
    final HighlightInfoHolder holder = createInfoHolder(getFile());

    final int chunkSize = Math.max(1, (elements1.size()+elements2.size()) / 100); // one percent precision is enough

    boolean success = analyzeByVisitors(visitors, holder, 0, () -> {
      Stack<TextRange> nestedRange = new Stack<>();
      Stack<List<HighlightInfo>> nestedInfos = new Stack<>();
      runVisitors(elements1, ranges1, chunkSize, progress, skipParentsSet, holder, insideResult, outsideResult, forceHighlightParents, visitors,
                  nestedRange, nestedInfos);
      final TextRange priorityIntersection = myPriorityRange.intersection(myRestrictRange);
      if ((!elements1.isEmpty() || !insideResult.isEmpty()) && priorityIntersection != null) { // do not apply when there were no elements to highlight
        myHighlightInfoProcessor.highlightsInsideVisiblePartAreProduced(myHighlightingSession, getEditor(), insideResult, myPriorityRange, myRestrictRange, getId());
      }
      runVisitors(elements2, ranges2, chunkSize, progress, skipParentsSet, holder, insideResult, outsideResult, forceHighlightParents, visitors,
                  nestedRange, nestedInfos);
    });
    List<HighlightInfo> postInfos = new ArrayList<>(holder.size());
    // there can be extra highlights generated in PostHighlightVisitor
    for (int j = 0; j < holder.size(); j++) {
      final HighlightInfo info = holder.get(j);
      assert info != null;
      postInfos.add(info);
    }
    myHighlightInfoProcessor.highlightsInsideVisiblePartAreProduced(myHighlightingSession, getEditor(),
                                                                    postInfos, getFile().getTextRange(), getFile().getTextRange(), POST_UPDATE_ALL);
    return success;
  }

  private boolean analyzeByVisitors(@NotNull final HighlightVisitor[] visitors,
                                    @NotNull final HighlightInfoHolder holder,
                                    final int i,
                                    @NotNull final Runnable action) {
    final boolean[] success = {true};
    if (i == visitors.length) {
      action.run();
    }
    else {
      if (!visitors[i].analyze(getFile(), myUpdateAll, holder, () -> success[0] = analyzeByVisitors(visitors, holder, i + 1, action))) {
        success[0] = false;
      }
    }
    return success[0];
  }

  private void runVisitors(@NotNull List<PsiElement> elements,
                           @NotNull List<ProperTextRange> ranges,
                           int chunkSize,
                           @NotNull ProgressIndicator progress,
                           @NotNull Set<PsiElement> skipParentsSet,
                           @NotNull HighlightInfoHolder holder,
                           @NotNull List<HighlightInfo> insideResult,
                           @NotNull List<HighlightInfo> outsideResult,
                           boolean forceHighlightParents,
                           @NotNull HighlightVisitor[] visitors,
                           @NotNull Stack<TextRange> nestedRange,
                           @NotNull Stack<List<HighlightInfo>> nestedInfos) {
    boolean failed = false;
    int nextLimit = chunkSize;
    for (int i = 0; i < elements.size(); i++) {
      PsiElement element = elements.get(i);
      ProgressManager.checkCanceled();

      PsiElement parent = element.getParent();
      if (element != getFile() && !skipParentsSet.isEmpty() && element.getFirstChild() != null && skipParentsSet.contains(element)) {
        skipParentsSet.add(parent);
        continue;
      }

      boolean isErrorElement = element instanceof PsiErrorElement;
      if (isErrorElement) {
        myHasErrorElement = true;
      }

      for (HighlightVisitor visitor : visitors) {
        try {
          visitor.visit(element);
        }
        catch (ProcessCanceledException | IndexNotReadyException e) {
          throw e;
        }
        catch (Exception e) {
          if (!failed) {
            LOG.error("In file: " + myFile.getViewProvider().getVirtualFile(), e);
          }
          failed = true;
        }
      }

      if (i == nextLimit) {
        advanceProgress(chunkSize);
        nextLimit = i + chunkSize;
      }

      TextRange elementRange = ranges.get(i);
      List<HighlightInfo> infosForThisRange = holder.size() == 0 ? null : new ArrayList<>(holder.size());
      for (int j = 0; j < holder.size(); j++) {
        final HighlightInfo info = holder.get(j);

        if (!myRestrictRange.containsRange(info.getStartOffset(), info.getEndOffset())) continue;
        List<HighlightInfo> result = myPriorityRange.containsRange(info.getStartOffset(), info.getEndOffset()) && !(element instanceof PsiFile) ? insideResult : outsideResult;
        // have to filter out already obtained highlights
        if (!result.add(info)) continue;
        boolean isError = info.getSeverity() == HighlightSeverity.ERROR;
        if (isError) {
          if (!forceHighlightParents) {
            skipParentsSet.add(parent);
          }
          myErrorFound = true;
        }
        // if this highlight info range is exactly the same as the element range we are visiting
        // that means we can clear this highlight as soon as visitors won't produce any highlights during visiting the same range next time.
        // We also know that we can remove syntax error element.
        info.setBijective(elementRange.equalsToRange(info.startOffset, info.endOffset) || isErrorElement);

        myHighlightInfoProcessor.infoIsAvailable(myHighlightingSession, info, myPriorityRange, myRestrictRange, Pass.UPDATE_ALL);
        infosForThisRange.add(info);
      }
      holder.clear();

      // include infos which we got while visiting nested elements with the same range
      while (true) {
        if (!nestedRange.isEmpty() && elementRange.contains(nestedRange.peek())) {
          TextRange oldRange = nestedRange.pop();
          List<HighlightInfo> oldInfos = nestedInfos.pop();
          if (elementRange.equals(oldRange)) {
            if (infosForThisRange == null) {
              infosForThisRange = oldInfos;
            }
            else if (oldInfos != null) {
              infosForThisRange.addAll(oldInfos);
            }
          }
        }
        else {
          break;
        }
      }
      nestedRange.push(elementRange);
      nestedInfos.push(infosForThisRange);
      if (parent == null || !Comparing.equal(elementRange, parent.getTextRange())) {
        myHighlightInfoProcessor.allHighlightsForRangeAreProduced(myHighlightingSession, elementRange, infosForThisRange);
      }
    }
    advanceProgress(elements.size() - (nextLimit-chunkSize));
  }

  private static final int POST_UPDATE_ALL = 5;

  private static void cancelAndRestartDaemonLater(@NotNull ProgressIndicator progress,
                                                  @NotNull final Project project) throws ProcessCanceledException {
    progress.cancel();
    Application application = ApplicationManager.getApplication();
    int delay = application.isUnitTestMode() ? 0 : RESTART_DAEMON_RANDOM.nextInt(100);
    EdtExecutorService.getScheduledExecutorInstance().schedule(() -> {
      if (!project.isDisposed()) {
        DaemonCodeAnalyzer.getInstance(project).restart();
      }
    }, delay, TimeUnit.MILLISECONDS);
    throw new ProcessCanceledException();
  }

  private boolean forceHighlightParents() {
    boolean forceHighlightParents = false;
    for(HighlightRangeExtension extension: Extensions.getExtensions(HighlightRangeExtension.EP_NAME)) {
      if (extension.isForceHighlightParents(getFile())) {
        forceHighlightParents = true;
        break;
      }
    }
    return forceHighlightParents;
  }

  protected HighlightInfoHolder createInfoHolder(@NotNull PsiFile file) {
    final HighlightInfoFilter[] filters = HighlightInfoFilter.EXTENSION_POINT_NAME.getExtensions();
    return new CustomHighlightInfoHolder(file, getColorsScheme(), filters);
  }

  static void highlightTodos(@NotNull PsiFile file,
                             @NotNull CharSequence text,
                             int startOffset,
                             int endOffset,
                             @NotNull ProgressIndicator progress,
                             @NotNull ProperTextRange priorityRange,
                             @NotNull Collection<HighlightInfo> insideResult,
                             @NotNull Collection<HighlightInfo> outsideResult) {
    PsiTodoSearchHelper helper = PsiTodoSearchHelper.SERVICE.getInstance(file.getProject());
    if (helper == null || !shouldHighlightTodos(helper, file)) return;
    TodoItem[] todoItems = helper.findTodoItems(file, startOffset, endOffset);
    if (todoItems.length == 0) return;

    for (TodoItem todoItem : todoItems) {
      ProgressManager.checkCanceled();
      TextRange range = todoItem.getTextRange();
      TextAttributes attributes = todoItem.getPattern().getAttributes().getTextAttributes();
      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.TODO).range(range);
      builder.textAttributes(attributes);
      String description = text.subSequence(range.getStartOffset(), range.getEndOffset()).toString();
      builder.description(description);
      builder.unescapedToolTip(StringUtil.shortenPathWithEllipsis(description, 1024));
      HighlightInfo info = builder.createUnconditionally();
      (priorityRange.containsRange(info.getStartOffset(), info.getEndOffset()) ? insideResult : outsideResult).add(info);
    }
  }

  private static boolean shouldHighlightTodos(PsiTodoSearchHelper helper, PsiFile file) {
    if (helper instanceof PsiTodoSearchHelperImpl) {
      PsiTodoSearchHelperImpl helperImpl = (PsiTodoSearchHelperImpl) helper;
      return helperImpl.shouldHighlightInEditor(file);
    } else {
      return false;
    }
  }

  private void reportErrorsToWolf() {
    if (!getFile().getViewProvider().isPhysical()) return; // e.g. errors in evaluate expression
    Project project = getFile().getProject();
    if (!PsiManager.getInstance(project).isInProject(getFile())) return; // do not report problems in libraries
    VirtualFile file = getFile().getVirtualFile();
    if (file == null) return;

    List<Problem> problems = convertToProblems(getInfos(), file, myHasErrorElement);
    WolfTheProblemSolver wolf = WolfTheProblemSolver.getInstance(project);

    boolean hasErrors = DaemonCodeAnalyzerEx.hasErrors(project, getDocument());
    if (!hasErrors || isWholeFileHighlighting()) {
      wolf.reportProblems(file, problems);
    }
    else {
      wolf.weHaveGotProblems(file, problems);
    }
  }

  @Override
  public double getProgress() {
    // do not show progress of visible highlighters update
    return myUpdateAll ? super.getProgress() : -1;
  }

  private static List<Problem> convertToProblems(@NotNull Collection<HighlightInfo> infos,
                                                 @NotNull VirtualFile file,
                                                 final boolean hasErrorElement) {
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
    return super.toString() + " updateAll="+myUpdateAll+" range= "+myRestrictRange;
  }
}
