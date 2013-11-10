/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.analysis.CustomHighlightInfoHolder;
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
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.Problem;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.search.TodoItem;
import com.intellij.util.NotNullProducer;
import com.intellij.util.SmartList;
import com.intellij.util.containers.Stack;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class GeneralHighlightingPass extends ProgressableTextEditorHighlightingPass implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass");
  static final String PRESENTABLE_NAME = DaemonBundle.message("pass.syntax");
  private static final Key<Boolean> HAS_ERROR_ELEMENT = Key.create("HAS_ERROR_ELEMENT");
  protected static final Condition<PsiFile> FILE_FILTER = new Condition<PsiFile>() {
    @Override
    public boolean value(PsiFile file) {
      return HighlightingLevelManager.getInstance(file.getProject()).shouldHighlight(file);
    }
  };

  protected final int myStartOffset;
  protected final int myEndOffset;
  protected final boolean myUpdateAll;
  protected final ProperTextRange myPriorityRange;
  protected final Editor myEditor;

  protected final List<HighlightInfo> myHighlights = new ArrayList<HighlightInfo>();

  protected volatile boolean myHasErrorElement;
  private volatile boolean myErrorFound;
  private static final Comparator<HighlightVisitor> VISITOR_ORDER_COMPARATOR = new Comparator<HighlightVisitor>() {
    @Override
    public int compare(final HighlightVisitor o1, final HighlightVisitor o2) {
      return o1.order() - o2.order();
    }
  };
  protected final EditorColorsScheme myGlobalScheme;
  private NotNullProducer<HighlightVisitor[]> myHighlightVisitorProducer = new NotNullProducer<HighlightVisitor[]>() {
    @NotNull
    @Override
    public HighlightVisitor[] produce() {
      return cloneHighlightVisitors();
    }
  };

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
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myUpdateAll = updateAll;
    myPriorityRange = priorityRange;
    myEditor = editor;

    LOG.assertTrue(file.isValid());
    boolean wholeFileHighlighting = isWholeFileHighlighting();
    myHasErrorElement = !wholeFileHighlighting && Boolean.TRUE.equals(myFile.getUserData(HAS_ERROR_ELEMENT));
    final DaemonCodeAnalyzerEx daemonCodeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(myProject);
    FileStatusMap fileStatusMap = daemonCodeAnalyzer.getFileStatusMap();
    myErrorFound = !wholeFileHighlighting && fileStatusMap.wasErrorFound(myDocument);

    // initial guess to show correct progress in the traffic light icon
    setProgressLimit(document.getTextLength()/2); // approx number of PSI elements = file length/2
    myGlobalScheme = myEditor != null ? myEditor.getColorsScheme() : EditorColorsManager.getInstance().getGlobalScheme();
  }

  private static final Key<AtomicInteger> HIGHLIGHT_VISITOR_INSTANCE_COUNT = new Key<AtomicInteger>("HIGHLIGHT_VISITOR_INSTANCE_COUNT");
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
    final List<HighlightVisitor> visitors = new ArrayList<HighlightVisitor>(highlightVisitors.length);
    List<HighlightVisitor> list = Arrays.asList(highlightVisitors);
    for (HighlightVisitor visitor : DumbService.getInstance(myProject).filterByDumbAwareness(list)) {
      if (visitor.suitableForFile(psiFile)) {
        visitors.add(visitor);
      }
    }
    LOG.assertTrue(!visitors.isEmpty(), list);

    HighlightVisitor[] visitorArray = visitors.toArray(new HighlightVisitor[visitors.size()]);
    Arrays.sort(visitorArray, VISITOR_ORDER_COMPARATOR);
    return visitorArray;
  }

  public void setHighlightVisitorProducer(@NotNull NotNullProducer<HighlightVisitor[]> highlightVisitorProducer) {
    myHighlightVisitorProducer = highlightVisitorProducer;
  }

  @NotNull
  protected HighlightVisitor[] getHighlightVisitors(@NotNull PsiFile psiFile) {
    return filterVisitors(myHighlightVisitorProducer.produce(), psiFile);
  }

  // returns old value
  public int incVisitorUsageCount(int delta) {
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
    final List<HighlightInfo> outsideResult = new ArrayList<HighlightInfo>(100);
    final List<HighlightInfo> insideResult = new ArrayList<HighlightInfo>(100);

    final DaemonCodeAnalyzerEx daemonCodeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(myProject);
    final HighlightVisitor[] filteredVisitors = getHighlightVisitors(myFile);
    final List<PsiElement> insideElements = new ArrayList<PsiElement>();
    final List<PsiElement> outsideElements = new ArrayList<PsiElement>();
    try {
      List<ProperTextRange> insideRanges = new ArrayList<ProperTextRange>();
      List<ProperTextRange> outsideRanges = new ArrayList<ProperTextRange>();
      Divider.divideInsideAndOutside(myFile, myStartOffset, myEndOffset, myPriorityRange, insideElements, insideRanges, outsideElements,
                                     outsideRanges, false, FILE_FILTER);

      setProgressLimit((long)(insideElements.size()+outsideElements.size()));

      final boolean forceHighlightParents = forceHighlightParents();

      if (!isDumbMode()) {
        highlightTodos(myFile, myDocument.getCharsSequence(), myStartOffset, myEndOffset, progress, myPriorityRange, insideResult,
                       outsideResult);
      }

      Runnable after1 = new Runnable() {
        @Override
        public void run() {
          final TextRange priorityIntersection = myPriorityRange.intersection(myRestrictRange);
          if ((!insideElements.isEmpty() || !insideResult.isEmpty()) &&
              priorityIntersection != null) { // do not apply when there were no elements to highlight

            myHighlightInfoProcessor.highlightsInsideVisiblePartAreProduced(myHighlightingSession, insideResult, myPriorityRange, myRestrictRange);
          }
        }
      };
      collectHighlights(insideElements, insideRanges, after1, outsideElements, outsideRanges, progress, filteredVisitors, insideResult, outsideResult, forceHighlightParents);

      myHighlightInfoProcessor.highlightsOutsideVisiblePartAreProduced(myHighlightingSession, outsideResult, myPriorityRange, myRestrictRange);

      if (myUpdateAll) {
        daemonCodeAnalyzer.getFileStatusMap().setErrorFoundFlag(myDocument, myErrorFound);
      }
    }
    finally {
      incVisitorUsageCount(-1);
    }
    myHighlights.addAll(insideResult);
    myHighlights.addAll(outsideResult);
  }

  protected boolean isFailFastOnAcquireReadAction() {
    return true;
  }

  private boolean isWholeFileHighlighting() {
    return myUpdateAll && myStartOffset == 0 && myEndOffset == myDocument.getTextLength();
  }

  @Override
  protected void applyInformationWithProgress() {
    myFile.putUserData(HAS_ERROR_ELEMENT, myHasErrorElement);

    if (myUpdateAll) {
      reportErrorsToWolf();
    }
  }

  @Override
  @NotNull
  public List<HighlightInfo> getInfos() {
    return new ArrayList<HighlightInfo>(myHighlights);
  }

  private void collectHighlights(@NotNull final List<PsiElement> elements1,
                                 @NotNull final List<ProperTextRange> ranges1,
                                 @NotNull final Runnable after1,
                                 @NotNull final List<PsiElement> elements2,
                                 @NotNull final List<ProperTextRange> ranges2,
                                 @NotNull final ProgressIndicator progress,
                                 @NotNull final HighlightVisitor[] visitors,
                                 @NotNull final List<HighlightInfo> insideResult,
                                 @NotNull final List<HighlightInfo> outsideResult,
                                 final boolean forceHighlightParents) {
    final Set<PsiElement> skipParentsSet = new THashSet<PsiElement>();

    // TODO - add color scheme to holder
    final HighlightInfoHolder holder = createInfoHolder(myFile);

    final int chunkSize = Math.max(1, (elements1.size()+elements2.size()) / 100); // one percent precision is enough

    final Runnable action = new Runnable() {
      @Override
      public void run() {
        Stack<Pair<TextRange, List<HighlightInfo>>> nested = new Stack<Pair<TextRange, List<HighlightInfo>>>();
        boolean failed = false;
        List<ProperTextRange> ranges = ranges1;
        //noinspection unchecked
        for (List<PsiElement> elements : new List[]{elements1, elements2}) {
          nested.clear();
          int nextLimit = chunkSize;
          for (int i = 0; i < elements.size(); i++) {
            PsiElement element = elements.get(i);
            progress.checkCanceled();

            PsiElement parent = element.getParent();
            if (element != myFile && !skipParentsSet.isEmpty() && element.getFirstChild() != null && skipParentsSet.contains(element)) {
              skipParentsSet.add(parent);
              continue;
            }

            if (element instanceof PsiErrorElement) {
              myHasErrorElement = true;
            }
            holder.clear();

            for (final HighlightVisitor visitor : visitors) {
              try {
                visitor.visit(element);
              }
              catch (ProcessCanceledException e) {
                throw e;
              }
              catch (IndexNotReadyException e) {
                throw e;
              }
              catch (Exception e) {
                if (!failed) {
                  LOG.error(e);
                }
                failed = true;
              }
            }

            if (i == nextLimit) {
              advanceProgress(chunkSize);
              nextLimit = i + chunkSize;
            }

            TextRange elementRange = ranges.get(i);
            List<HighlightInfo> infosForThisRange = holder.size() == 0 ? null : new ArrayList<HighlightInfo>(holder.size());
            for (int j = 0; j < holder.size(); j++) {
              final HighlightInfo info = holder.get(j);
              assert info != null;

              if (!myRestrictRange.containsRange(info.getStartOffset(), info.getEndOffset())) continue;
              List<HighlightInfo> result = myPriorityRange.containsRange(info.getStartOffset(), info.getEndOffset()) ? insideResult : outsideResult;
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
              info.setBijective(elementRange.equalsToRange(info.startOffset, info.endOffset));

              myHighlightInfoProcessor.infoIsAvailable(myHighlightingSession, info);
              //myTransferToEDTQueue.offer(info);
              infosForThisRange.add(info);
            }
            // include infos which we got while visiting nested elements with the same range
            while (true) {
              if (!nested.isEmpty() && elementRange.contains(nested.peek().first)) {
                Pair<TextRange, List<HighlightInfo>> old = nested.pop();
                if (elementRange.equals(old.first)) {
                  if (infosForThisRange == null) {
                    infosForThisRange = old.second;
                  }
                  else if (old.second != null){
                    infosForThisRange.addAll(old.second);
                  }
                }
              }
              else {
                break;
              }
            }
            nested.push(Pair.create(elementRange, infosForThisRange));
            if (parent == null || !Comparing.equal(elementRange, parent.getTextRange())) {
              myHighlightInfoProcessor.allHighlightsForRangeAreProduced(myHighlightingSession, elementRange, infosForThisRange);
              //killAbandonedHighlightsUnder(elementRange, infosForThisRange, progress);
            }
          }
          advanceProgress(elements.size() - (nextLimit-chunkSize));
          if (elements == elements1) {
            after1.run();
            ranges = ranges2;
          }
        }
      }
    };

    analyzeByVisitors(progress, visitors, holder, 0, action);
  }

  private void analyzeByVisitors(@NotNull final ProgressIndicator progress,
                                 @NotNull final HighlightVisitor[] visitors,
                                 @NotNull final HighlightInfoHolder holder,
                                 final int i,
                                 @NotNull final Runnable action) {
    if (i == visitors.length) {
      action.run();
    }
    else {
      if (!visitors[i].analyze(myFile, myUpdateAll, holder, new Runnable() {
        @Override
        public void run() {
          analyzeByVisitors(progress, visitors, holder, i+1, action);
        }
      })) {
        cancelAndRestartDaemonLater(progress, myProject, this);
      }
    }
  }

  static void cancelAndRestartDaemonLater(@NotNull ProgressIndicator progress,
                                          @NotNull final Project project,
                                          @NotNull TextEditorHighlightingPass passCalledFrom) throws ProcessCanceledException {
    progress.cancel();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        try {
          Thread.sleep(new Random().nextInt(100));
        }
        catch (InterruptedException e) {
          LOG.error(e);
        }
        DaemonCodeAnalyzer.getInstance(project).restart();
      }
    }, project.getDisposed());
    throw new ProcessCanceledException();
  }

  private boolean forceHighlightParents() {
    boolean forceHighlightParents = false;
    for(HighlightRangeExtension extension: Extensions.getExtensions(HighlightRangeExtension.EP_NAME)) {
      if (extension.isForceHighlightParents(myFile)) {
        forceHighlightParents = true;
        break;
      }
    }
    return forceHighlightParents;
  }

  protected HighlightInfoHolder createInfoHolder(final PsiFile file) {
    final HighlightInfoFilter[] filters = HighlightInfoFilter.EXTENSION_POINT_NAME.getExtensions();
    return new CustomHighlightInfoHolder(file, getColorsScheme(), filters);
  }

  protected static void highlightTodos(@NotNull PsiFile file,
                                       @NotNull CharSequence text,
                                       int startOffset,
                                       int endOffset,
                                       @NotNull ProgressIndicator progress,
                                       @NotNull ProperTextRange priorityRange,
                                       @NotNull Collection<HighlightInfo> insideResult,
                                       @NotNull Collection<HighlightInfo> outsideResult) {
    PsiTodoSearchHelper helper = PsiTodoSearchHelper.SERVICE.getInstance(file.getProject());
    if (helper == null) return;
    TodoItem[] todoItems = helper.findTodoItems(file, startOffset, endOffset);
    if (todoItems.length == 0) return;

    for (TodoItem todoItem : todoItems) {
      progress.checkCanceled();
      TextRange range = todoItem.getTextRange();
      String description = text.subSequence(range.getStartOffset(), range.getEndOffset()).toString();
      TextAttributes attributes = todoItem.getPattern().getAttributes().getTextAttributes();
      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.TODO).range(range);
      builder.textAttributes(attributes);
      builder.descriptionAndTooltip(description);
      HighlightInfo info = builder.createUnconditionally();
      (priorityRange.containsRange(info.getStartOffset(), info.getEndOffset()) ? insideResult : outsideResult).add(info);
    }
  }

  private void reportErrorsToWolf() {
    if (!myFile.getViewProvider().isPhysical()) return; // e.g. errors in evaluate expression
    Project project = myFile.getProject();
    if (!PsiManager.getInstance(project).isInProject(myFile)) return; // do not report problems in libraries
    VirtualFile file = myFile.getVirtualFile();
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
    List<Problem> problems = new SmartList<Problem>();
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
    return super.toString() + " updateAll="+myUpdateAll+" range=("+myStartOffset+","+myEndOffset+")";
  }
}
