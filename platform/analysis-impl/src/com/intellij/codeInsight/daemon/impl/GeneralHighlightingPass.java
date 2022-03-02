// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.RainbowVisitor;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager;
import com.intellij.codeInsight.problems.ProblemImpl;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
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
import com.intellij.util.containers.LongStack;
import com.intellij.util.containers.Stack;
import com.intellij.xml.util.XmlStringUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public class GeneralHighlightingPass extends ProgressableTextEditorHighlightingPass {
  private static final Logger LOG = Logger.getInstance(GeneralHighlightingPass.class);
  private static final Key<Boolean> HAS_ERROR_ELEMENT = Key.create("HAS_ERROR_ELEMENT");
  static final Predicate<PsiFile> SHOULD_HIGHLIGHT_FILTER = file -> HighlightingLevelManager.getInstance(file.getProject()).shouldHighlight(file);
  private static final Random RESTART_DAEMON_RANDOM = new Random();

  final boolean myUpdateAll;
  final ProperTextRange myPriorityRange;

  final List<HighlightInfo> myHighlights = new ArrayList<>();

  protected volatile boolean myHasErrorElement;
  private volatile boolean myErrorFound;
  final EditorColorsScheme myGlobalScheme;
  private volatile NotNullProducer<HighlightVisitor[]> myHighlightVisitorProducer = this::cloneHighlightVisitors;

  GeneralHighlightingPass(@NotNull PsiFile file,
                          @NotNull Document document,
                          int startOffset,
                          int endOffset,
                          boolean updateAll,
                          @NotNull ProperTextRange priorityRange,
                          @Nullable Editor editor,
                          @NotNull HighlightInfoProcessor highlightInfoProcessor) {
    super(file.getProject(), document, getPresentableNameText(), file, editor, TextRange.create(startOffset, endOffset), true, highlightInfoProcessor);
    myUpdateAll = updateAll;
    myPriorityRange = priorityRange;

    PsiUtilCore.ensureValid(file);
    boolean wholeFileHighlighting = isWholeFileHighlighting();
    myHasErrorElement = !wholeFileHighlighting && Boolean.TRUE.equals(getFile().getUserData(HAS_ERROR_ELEMENT));
    DaemonCodeAnalyzerEx daemonCodeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(myProject);
    FileStatusMap fileStatusMap = daemonCodeAnalyzer.getFileStatusMap();
    myErrorFound = !wholeFileHighlighting && fileStatusMap.wasErrorFound(getDocument());

    // initial guess to show correct progress in the traffic light icon
    setProgressLimit(document.getTextLength()/2); // approx number of PSI elements = file length/2
    myGlobalScheme = editor != null ? editor.getColorsScheme() : EditorColorsManager.getInstance().getGlobalScheme();
  }

  private @NotNull PsiFile getFile() {
    return myFile;
  }

  private static final Key<AtomicInteger> HIGHLIGHT_VISITOR_INSTANCE_COUNT = new Key<>("HIGHLIGHT_VISITOR_INSTANCE_COUNT");
  private HighlightVisitor @NotNull [] cloneHighlightVisitors() {
    int oldCount = incVisitorUsageCount(1);
    HighlightVisitor[] highlightVisitors = HighlightVisitor.EP_HIGHLIGHT_VISITOR.getExtensions(myProject);
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

  private HighlightVisitor @NotNull [] filterVisitors(HighlightVisitor @NotNull [] highlightVisitors, @NotNull PsiFile psiFile) {
    List<HighlightVisitor> visitors = new ArrayList<>(highlightVisitors.length);
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
      LOG.error("No visitors registered. list=" + list + "; all visitors are:" + HighlightVisitor.EP_HIGHLIGHT_VISITOR.getExtensionList(myProject));
    }

    return visitors.toArray(new HighlightVisitor[0]);
  }

  void setHighlightVisitorProducer(@NotNull NotNullProducer<HighlightVisitor[]> highlightVisitorProducer) {
    myHighlightVisitorProducer = highlightVisitorProducer;
  }

  HighlightVisitor @NotNull [] getHighlightVisitors(@NotNull PsiFile psiFile) {
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
  protected void collectInformationWithProgress(@NotNull ProgressIndicator progress) {
    List<HighlightInfo> outsideResult = new ArrayList<>(100);
    List<HighlightInfo> insideResult = new ArrayList<>(100);

    DaemonCodeAnalyzerEx daemonCodeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(myProject);
    HighlightVisitor[] filteredVisitors = getHighlightVisitors(getFile());
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


      List<LongList> map = ContainerUtil.map(dividedElements,
                                             dividedForRoot -> {
                                               LongList insideRanges = dividedForRoot.insideRanges;
                                               PsiElement lastInside = ContainerUtil.getLastItem(dividedForRoot.inside);
                                               return lastInside instanceof PsiFile && !(lastInside instanceof PsiCodeFragment)
                                                      ? insideRanges.subList(0, insideRanges.size() - 1) : insideRanges;
                                             });

      LongList allInsideRanges = ContainerUtil.reduce(map, new LongArrayList(map.isEmpty() ? 1 : map.get(0).size()), (l1, l2)->{ l1.addAll(l2); return l1;});

      List<PsiElement> allOutsideElements = ContainerUtil.concat((List<List<PsiElement>>)ContainerUtil.map(dividedElements,
                                                  dividedForRoot -> {
                                                    List<PsiElement> outside = dividedForRoot.outside;
                                                    PsiElement lastInside = ContainerUtil.getLastItem(dividedForRoot.inside);
                                                    return lastInside instanceof PsiFile && !(lastInside instanceof PsiCodeFragment) ? ContainerUtil.append(outside,
                                                      lastInside) : outside;
                                                  }));
      List<LongList> map1 = ContainerUtil.map(dividedElements,
                                                           dividedForRoot -> {
                                                             LongList outsideRanges = dividedForRoot.outsideRanges;
                                                             PsiElement lastInside = ContainerUtil.getLastItem(dividedForRoot.inside);
                                                             long lastInsideRange = dividedForRoot.insideRanges.isEmpty() ? -1 : dividedForRoot.insideRanges.getLong(dividedForRoot.insideRanges.size()-1);
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

      if (!isDumbMode() && getEditor() != null) {
        highlightTodos(getFile(), getDocument().getCharsSequence(), myRestrictRange.getStartOffset(), myRestrictRange.getEndOffset(),
                       myPriorityRange, insideResult,
                       outsideResult);
      }

      boolean success = collectHighlights(allInsideElements, allInsideRanges, allOutsideElements, allOutsideRanges, filteredVisitors, insideResult, outsideResult, forceHighlightParents);

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
  public @NotNull List<HighlightInfo> getInfos() {
    return new ArrayList<>(myHighlights);
  }

  private boolean collectHighlights(@NotNull List<? extends PsiElement> elements1,
                                    @NotNull LongList ranges1,
                                    @NotNull List<? extends PsiElement> elements2,
                                    @NotNull LongList ranges2,
                                    HighlightVisitor @NotNull [] visitors,
                                    @NotNull List<HighlightInfo> insideResult,
                                    @NotNull List<? super HighlightInfo> outsideResult,
                                    boolean forceHighlightParents) {
    Set<PsiElement> skipParentsSet = new HashSet<>();

    HighlightInfoHolder holder = createInfoHolder(getFile());
    holder.getAnnotationSession().setVR(myPriorityRange);

    int chunkSize = Math.max(1, (elements1.size()+elements2.size()) / 100); // one percent precision is enough

    boolean success = analyzeByVisitors(visitors, holder, 0, () -> {
      LongStack nestedRange = new LongStack();
      Stack<List<HighlightInfo>> nestedInfos = new Stack<>();
      runVisitors(elements1, ranges1, chunkSize, skipParentsSet, holder, insideResult, outsideResult, forceHighlightParents, visitors,
                  nestedRange, nestedInfos);
      boolean priorityIntersectionHasElements = myPriorityRange.intersectsStrict(myRestrictRange);
      if ((!elements1.isEmpty() || !insideResult.isEmpty()) && priorityIntersectionHasElements) { // do not apply when there were no elements to highlight
        myHighlightInfoProcessor.highlightsInsideVisiblePartAreProduced(myHighlightingSession, getEditor(), insideResult, myPriorityRange, myRestrictRange, getId());
      }
      runVisitors(elements2, ranges2, chunkSize, skipParentsSet, holder, insideResult, outsideResult, forceHighlightParents, visitors,
                  nestedRange, nestedInfos);
    });
    // there can be extra highlights generated in PostHighlightVisitor
    List<HighlightInfo> postInfos = new ArrayList<>(holder.size());
    for (int j = 0; j < holder.size(); j++) {
      HighlightInfo info = holder.get(j);
      postInfos.add(info);
    }
    myHighlightInfoProcessor.highlightsInsideVisiblePartAreProduced(myHighlightingSession, getEditor(),
                                                                    postInfos, getFile().getTextRange(), getFile().getTextRange(), POST_UPDATE_ALL);
    return success;
  }

  private boolean analyzeByVisitors(HighlightVisitor @NotNull [] visitors,
                                    @NotNull HighlightInfoHolder holder,
                                    int i,
                                    @NotNull Runnable action) {
    boolean[] success = {true};
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

  private void runVisitors(@NotNull List<? extends PsiElement> elements,
                           @NotNull LongList ranges,
                           int chunkSize,
                           @NotNull Set<? super PsiElement> skipParentsSet,
                           @NotNull HighlightInfoHolder holder,
                           @NotNull List<? super HighlightInfo> insideResult,
                           @NotNull List<? super HighlightInfo> outsideResult,
                           boolean forceHighlightParents,
                           HighlightVisitor @NotNull [] visitors,
                           @NotNull LongStack nestedRange,
                           @NotNull Stack<List<HighlightInfo>> nestedInfos) {
    boolean failed = false;
    int nextLimit = chunkSize;
    for (int i = 0; i < elements.size(); i++) {
      PsiElement element = elements.get(i);
      ProgressManager.checkCanceled();

      PsiElement parent = element.getParent();
      if (element != getFile() && !skipParentsSet.isEmpty() && element.getFirstChild() != null && skipParentsSet.contains(element)
          && parent != null) {
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

          // assume that the visitor is done messing with just created HighlightInfo after its visit() method completed
          // and we can start applying them incrementally at last.
          // (but not sooner, thanks to awfully racey HighlightInfo.setXXX() and .registerFix() API)
          holder.queueToUpdateIncrementally();
        }
        catch (ProcessCanceledException | IndexNotReadyException e) {
          throw e;
        }
        catch (Exception e) {
          if (!failed) {
            LOG.error("In file: " + getFile().getViewProvider().getVirtualFile(), e);
          }
          failed = true;
        }
      }

      if (i == nextLimit) {
        advanceProgress(chunkSize);
        nextLimit = i + chunkSize;
      }

      long elementRange = ranges.getLong(i);
      List<HighlightInfo> infosForThisRange = holder.size() == 0 ? null : new ArrayList<>(holder.size());
      for (int j = 0; j < holder.size(); j++) {
        HighlightInfo info = holder.get(j);

        if (!myRestrictRange.contains(info)) continue;
        List<? super HighlightInfo> result = myPriorityRange.contains(info) && !(element instanceof PsiFile) ? insideResult : outsideResult;
        result.add(info);
        boolean isError = info.getSeverity() == HighlightSeverity.ERROR;
        if (isError) {
          if (!forceHighlightParents && parent != null) {
            skipParentsSet.add(parent);
          }
          myErrorFound = true;
        }
        // if this highlight info range is contained inside the current element range we are visiting
        // that means we can clear this highlight as soon as visitors won't produce any highlights during visiting the same range next time.
        // We also know that we can remove syntax error element.
        info.setVisitingTextRange(elementRange);
        infosForThisRange.add(info);
      }
      holder.clear();

      // include infos which we got while visiting nested elements with the same range
      while (true) {
        if (!nestedRange.empty() && TextRange.contains(elementRange, nestedRange.peek())) {
          long oldRange = nestedRange.pop();
          List<HighlightInfo> oldInfos = nestedInfos.pop();
          if (elementRange == oldRange) {
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
      if (parent == null || !hasSameRangeAsParent(parent, element)) {
        myHighlightInfoProcessor.allHighlightsForRangeAreProduced(myHighlightingSession, elementRange, infosForThisRange);
      }
    }
    advanceProgress(elements.size() - (nextLimit-chunkSize));
  }

  private static boolean hasSameRangeAsParent(@NotNull PsiElement parent, @NotNull PsiElement element) {
    return element.getStartOffsetInParent() == 0 && element.getTextLength() == parent.getTextLength();
  }

  public static final int POST_UPDATE_ALL = 5;

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
    HighlightInfoFilter[] filters = HighlightInfoFilter.EXTENSION_POINT_NAME.getExtensions();
    EditorColorsScheme actualScheme = getColorsScheme() == null ? EditorColorsManager.getInstance().getGlobalScheme() : getColorsScheme();
    return new HighlightInfoHolder(file, filters) {
      int queued; // all infos at [0..queued) indices are scheduled to EDT via queueInfoToUpdateIncrementally()
      @Override
      public @NotNull TextAttributesScheme getColorsScheme() {
        return actualScheme;
      }

      @Override
      public void queueToUpdateIncrementally() {
        for (int i = queued; i < size(); i++) {
          HighlightInfo info = get(i);
          queueInfoToUpdateIncrementally(info);
        }
        queued = size();
      }

      @Override
      public void clear() {
        super.clear();
        queued = 0;
      }
    };
  }

  protected void queueInfoToUpdateIncrementally(@NotNull HighlightInfo info) {
    int group = info.getGroup() == 0 ? Pass.UPDATE_ALL : info.getGroup();
    myHighlightInfoProcessor.infoIsAvailable(myHighlightingSession, info, myPriorityRange, myRestrictRange, group);
  }

  static void highlightTodos(@NotNull PsiFile file,
                             @NotNull CharSequence text,
                             int startOffset,
                             int endOffset,
                             @NotNull ProperTextRange priorityRange,
                             @NotNull Collection<? super HighlightInfo> insideResult,
                             @NotNull Collection<? super HighlightInfo> outsideResult) {
    PsiTodoSearchHelper helper = PsiTodoSearchHelper.SERVICE.getInstance(file.getProject());
    if (helper == null || !shouldHighlightTodos(helper, file)) return;
    TodoItem[] todoItems = helper.findTodoItems(file, startOffset, endOffset);
    if (todoItems.length == 0) return;

    for (TodoItem todoItem : todoItems) {
      ProgressManager.checkCanceled();

      TextRange textRange = todoItem.getTextRange();
      List<TextRange> additionalRanges = todoItem.getAdditionalTextRanges();

      String description = formatDescription(text, textRange, additionalRanges);
      String tooltip = XmlStringUtil.escapeString(StringUtil.shortenPathWithEllipsis(description, 1024)).replace("\n", "<br>");

      TextAttributes attributes = todoItem.getPattern().getAttributes().getTextAttributes();
      addTodoItem(startOffset, endOffset, priorityRange, insideResult, outsideResult, attributes, description, tooltip, textRange);
      if (!additionalRanges.isEmpty()) {
        TextAttributes attributesForAdditionalLines = attributes.clone();
        attributesForAdditionalLines.setErrorStripeColor(null);
        for (TextRange range: additionalRanges) {
          addTodoItem(startOffset, endOffset, priorityRange, insideResult, outsideResult, attributesForAdditionalLines, description,
                      tooltip, range);
        }
      }
    }
  }

  private static @NlsSafe String formatDescription(@NotNull CharSequence text, @NotNull TextRange textRange, @NotNull List<? extends TextRange> additionalRanges) {
    StringJoiner joiner = new StringJoiner("\n");
    joiner.add(textRange.subSequence(text));
    for (TextRange additionalRange : additionalRanges) {
      joiner.add(additionalRange.subSequence(text));
    }
    return joiner.toString();
  }

  private static void addTodoItem(int restrictStartOffset,
                                  int restrictEndOffset,
                                  @NotNull ProperTextRange priorityRange,
                                  @NotNull Collection<? super HighlightInfo> insideResult,
                                  @NotNull Collection<? super HighlightInfo> outsideResult,
                                  @NotNull TextAttributes attributes,
                                  @NotNull @NlsContexts.DetailedDescription String description,
                                  @NotNull @NlsContexts.Tooltip String tooltip,
                                  @NotNull TextRange range) {
    if (range.getStartOffset() >= restrictEndOffset || range.getEndOffset() <= restrictStartOffset) return;
    HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.TODO)
      .range(range)
      .textAttributes(attributes)
      .description(description)
      .escapedToolTip(tooltip)
      .createUnconditionally();
    Collection<? super HighlightInfo> result = priorityRange.containsRange(info.getStartOffset(), info.getEndOffset()) ? insideResult : outsideResult;
    result.add(info);
  }

  private static boolean shouldHighlightTodos(@NotNull PsiTodoSearchHelper helper, @NotNull PsiFile file) {
    return helper instanceof PsiTodoSearchHelperImpl && ((PsiTodoSearchHelperImpl)helper).shouldHighlightInEditor(file);
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
    return super.toString() + " updateAll="+myUpdateAll+" range= "+myRestrictRange;
  }

  private static @Nls String getPresentableNameText() {
    return AnalysisBundle.message("pass.syntax");
  }
}
