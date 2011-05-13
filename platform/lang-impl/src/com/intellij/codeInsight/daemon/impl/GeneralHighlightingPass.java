/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightLevelUtil;
import com.intellij.codeInsight.problems.ProblemImpl;
import com.intellij.concurrency.JobUtil;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.Problem;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.impl.source.tree.injected.Place;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.TodoItem;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class GeneralHighlightingPass extends ProgressableTextEditorHighlightingPass implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass");
  static final String PRESENTABLE_NAME = DaemonBundle.message("pass.syntax");
  private static final Key<Boolean> HAS_ERROR_ELEMENT = Key.create("HAS_ERROR_ELEMENT");

  private final int myStartOffset;
  private final int myEndOffset;
  private final boolean myUpdateAll;
  private final ProperTextRange myPriorityRange;
  private final Editor myEditor;

  private final Collection<HighlightInfo> myHighlights = new ArrayList<HighlightInfo>();

  protected volatile boolean myHasErrorElement;
  private volatile boolean myErrorFound;
  private static final Comparator<HighlightVisitor> VISITOR_ORDER_COMPARATOR = new Comparator<HighlightVisitor>() {
    public int compare(final HighlightVisitor o1, final HighlightVisitor o2) {
      return o1.order() - o2.order();
    }
  };
  private Runnable myApplyCommand;
  private final EditorColorsScheme myGlobalScheme;
  private boolean myFailFastOnAcquireReadAction = true;

  public GeneralHighlightingPass(@NotNull Project project,
                                 @NotNull PsiFile file,
                                 @NotNull Document document,
                                 int startOffset,
                                 int endOffset,
                                 boolean updateAll) {
    this(project, file, document, startOffset, endOffset, updateAll, new ProperTextRange(0,document.getTextLength()), null);
  }
  public GeneralHighlightingPass(@NotNull Project project,
                                 @NotNull PsiFile file,
                                 @NotNull Document document,
                                 int startOffset,
                                 int endOffset,
                                 boolean updateAll,
                                 @NotNull ProperTextRange priorityRange,
                                 @Nullable Editor editor) {
    super(project, document, PRESENTABLE_NAME, file, true);
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myUpdateAll = updateAll;
    myPriorityRange = priorityRange;
    myEditor = editor;

    LOG.assertTrue(file.isValid());
    setId(Pass.UPDATE_ALL);
    myHasErrorElement = !isWholeFileHighlighting() && Boolean.TRUE.equals(myFile.getUserData(HAS_ERROR_ELEMENT));
    FileStatusMap fileStatusMap = ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject)).getFileStatusMap();
    myErrorFound = !isWholeFileHighlighting() && fileStatusMap.wasErrorFound(myDocument);

    myApplyCommand = new Runnable() {
      public void run() {
        ProperTextRange range = new ProperTextRange(myStartOffset, myEndOffset);
        MarkupModel model = myDocument.getMarkupModel(myProject);
        UpdateHighlightersUtil.cleanFileLevelHighlights(myProject, Pass.UPDATE_ALL,myFile);
        final EditorColorsScheme colorsScheme = getColorsScheme();
        UpdateHighlightersUtil.setHighlightersInRange(myProject, myDocument, range, colorsScheme, myHighlights, (MarkupModelEx)model, Pass.UPDATE_ALL);
      }
    };

    // initial guess to show correct progress in the traffic light icon
    setProgressLimit(document.getTextLength()/2); // approx number of PSI elements = file length/2
    myGlobalScheme = EditorColorsManager.getInstance().getGlobalScheme();
  }

  private static final Key<AtomicInteger> HIGHLIGHT_VISITOR_INSTANCE_COUNT = new Key<AtomicInteger>("HIGHLIGHT_VISITOR_INSTANCE_COUNT");
  @NotNull
  private HighlightVisitor[] getHighlightVisitors() {
    int oldCount = incVisitorUsageCount(1);
    HighlightVisitor[] highlightVisitors = createHighlightVisitors();
    if (oldCount != 0) {
      HighlightVisitor[] clones = new HighlightVisitor[highlightVisitors.length];
      for (int i = 0; i < highlightVisitors.length; i++) {
        HighlightVisitor highlightVisitor = highlightVisitors[i];
        clones[i] = highlightVisitor.clone();
      }
      highlightVisitors = clones;
    }
    return highlightVisitors;
  }

  protected HighlightVisitor[] createHighlightVisitors() {
    return Extensions.getExtensions(HighlightVisitor.EP_HIGHLIGHT_VISITOR, myProject);
  }

  // returns old value
  private int incVisitorUsageCount(int delta) {
    AtomicInteger count = myProject.getUserData(HIGHLIGHT_VISITOR_INSTANCE_COUNT);
    if (count == null) {
      count = ((UserDataHolderEx)myProject).putUserDataIfAbsent(HIGHLIGHT_VISITOR_INSTANCE_COUNT, new AtomicInteger(0));
    }
    int old = count.getAndAdd(delta);
    assert old + delta >= 0 : old +";" + delta;
    return old;
  }

  protected void collectInformationWithProgress(final ProgressIndicator progress) {
    final Set<HighlightInfo> gotHighlights = new THashSet<HighlightInfo>(100);
    final Set<HighlightInfo> outsideResult = new THashSet<HighlightInfo>(100);

    DaemonCodeAnalyzer daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(myProject);
    final FileStatusMap fileStatusMap = ((DaemonCodeAnalyzerImpl)daemonCodeAnalyzer).getFileStatusMap();
    HighlightVisitor[] highlightVisitors = getHighlightVisitors();
    final HighlightVisitor[] filteredVisitors = filterVisitors(highlightVisitors, myFile);
    final List<PsiElement> inside = new ArrayList<PsiElement>();
    final List<PsiElement> outside = new ArrayList<PsiElement>();
    try {
      Divider.divideInsideAndOutside(myFile, myStartOffset, myEndOffset, myPriorityRange, inside, outside,
                                     HighlightLevelUtil.AnalysisLevel.HIGHLIGHT,false);
      final Set<PsiFile> injected = new THashSet<PsiFile>();
      getInjectedPsiFiles(inside, outside, progress, injected);

      setProgressLimit((long)(inside.size()+outside.size()));

      final boolean forceHighlightParents = forceHighlightParents();

      if (!isDumbMode()) {
        highlightTodos(myFile, myDocument.getCharsSequence(), myStartOffset, myEndOffset, progress, myPriorityRange, gotHighlights, outsideResult);
      }

      collectHighlights(inside, new Runnable() {
        @Override
        public void run() {
          // all infos for the "injected fragment for the host which is inside" are indeed inside
          // but some of the infos for the "injected fragment for the host which is outside" can be still inside
          THashSet<HighlightInfo> injectedResult = new THashSet<HighlightInfo>();
          if (!addInjectedPsiHighlights(injected, progress, Collections.synchronizedSet(injectedResult))) throw new ProcessCanceledException();

          //if (!addInjectedPsiHighlights(injectedOutside, progress, result)) throw new ProcessCanceledException();
          final List<HighlightInfo> toApplyOutside = new ArrayList<HighlightInfo>(gotHighlights.size());

          for (HighlightInfo info : injectedResult) {
            if (myPriorityRange.containsRange(info.getStartOffset(), info.getEndOffset())) {
              gotHighlights.add(info);
            }
            else {
              // nonconditionally apply injected results regardless whether they are in myStartOffset,myEndOffset
              toApplyOutside.add(info);
            }
          }

          if (outsideResult.isEmpty() && toApplyOutside.isEmpty()) {
            return;  // apply only result (by default apply command) and only within inside
          }

          final ProperTextRange priorityIntersection = myPriorityRange.intersection(new TextRange(myStartOffset, myEndOffset));
          if ((!inside.isEmpty() || !gotHighlights.isEmpty()) && priorityIntersection != null) { // do not apply when there were no elements to highlight
            // clear infos found in visible area to avoid applying them twice
            final List<HighlightInfo> toApplyInside = new ArrayList<HighlightInfo>(gotHighlights);
            myHighlights.addAll(toApplyInside);
            gotHighlights.clear();
            gotHighlights.addAll(outsideResult);
            final long modificationStamp = myDocument.getModificationStamp();
            UIUtil.invokeLaterIfNeeded(new Runnable() {
              @Override
              public void run() {
                if (myProject.isDisposed() || modificationStamp != myDocument.getModificationStamp()) return;
                MarkupModel markupModel = myDocument.getMarkupModel(myProject);

                UpdateHighlightersUtil.setHighlightersInRange(myProject, myDocument, priorityIntersection, getColorsScheme(), toApplyInside,
                                                              (MarkupModelEx)markupModel, Pass.UPDATE_ALL);
                if (myEditor != null) {
                  new ShowAutoImportPass(myProject, myFile, myEditor).applyInformationToEditor();
                }
              }
            });
          }

          myApplyCommand = new Runnable() {
            @Override
            public void run() {
              final List<HighlightInfo> insideInfos = new ArrayList<HighlightInfo>(gotHighlights.size());

              ProperTextRange range = new ProperTextRange(myStartOffset, myEndOffset);

              for (HighlightInfo info : gotHighlights) {
                if (!range.containsRange(info.getStartOffset(), info.getEndOffset())) continue;
                if (myPriorityRange.containsRange(info.getStartOffset(), info.getEndOffset())) {
                  insideInfos.add(info);
                }
                else {
                  toApplyOutside.add(info);
                }
              }

              //toApply.addAll(injectedOutsideInfos);

              /*
              if (!insideInfos.isEmpty()) {
                // some one has reported highlights inside range while running annotators for outside range - bad, bad annotator!
                for (HighlightInfo info : insideInfos) {
                  toApply.add(info);
                }
              }
              */

              UpdateHighlightersUtil.setHighlightersOutsideRange(myProject, myDocument, toApplyOutside, getColorsScheme(),
                                                                         myStartOffset, myEndOffset, myPriorityRange, Pass.UPDATE_ALL);
            }
          };
        }
      }, outside, progress, filteredVisitors, gotHighlights, forceHighlightParents);

      if (myUpdateAll) {
        fileStatusMap.setErrorFoundFlag(myDocument, myErrorFound);
      }
    }
    finally {
      incVisitorUsageCount(-1);
    }
    myHighlights.addAll(gotHighlights);
  }

  private void getInjectedPsiFiles(@NotNull final List<PsiElement> elements1,
                                   @NotNull final List<PsiElement> elements2,
                                   @NotNull final ProgressIndicator progress,
                                   @NotNull final Set<PsiFile> outInjected) {
    List<DocumentWindow> injected = InjectedLanguageUtil.getCachedInjectedDocuments(myFile);
    Collection<PsiElement> hosts = new THashSet<PsiElement>(elements1.size() + elements2.size() + injected.size());

    // rehighlight all injected PSI regardless the range,
    // since change in one place can lead to invalidation of injected PSI in (completely) other place.
    for (DocumentWindow documentRange : injected) {
      progress.checkCanceled();
      if (!documentRange.isValid()) continue;
      PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(documentRange);
      if (file == null) continue;
      PsiElement context = file.getContext();
      if (context != null
          && context.isValid()
          && !file.getProject().isDisposed()
          && (myUpdateAll || new ProperTextRange(myStartOffset, myEndOffset).intersects(context.getTextRange()))) {
        hosts.add(context);
      }
    }
    hosts.addAll(elements1);
    hosts.addAll(elements2);

    final PsiLanguageInjectionHost.InjectedPsiVisitor visitor = new PsiLanguageInjectionHost.InjectedPsiVisitor() {
      public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
        synchronized (outInjected) {
          outInjected.add(injectedPsi);
        }
      }
    };
    if (!JobUtil.invokeConcurrentlyUnderProgress(new ArrayList<PsiElement>(hosts), new Processor<PsiElement>() {
        @Override
        public boolean process(PsiElement element) {
          progress.checkCanceled();
          InjectedLanguageUtil.enumerate(element, myFile, visitor, false);
          return true;
        }
      }, false, progress)) throw new ProcessCanceledException();
  }

  // returns false if canceled
  private boolean addInjectedPsiHighlights(@NotNull final Set<PsiFile> injectedFiles,
                                           @NotNull final ProgressIndicator progress,
                                           @NotNull final Collection<HighlightInfo> outInfos) {
    if (injectedFiles.isEmpty()) return true;
    final InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(myProject);
    final TextAttributes injectedAttributes = myGlobalScheme.getAttributes(EditorColors.INJECTED_LANGUAGE_FRAGMENT);

    return JobUtil.invokeConcurrentlyUnderProgress(new ArrayList<PsiFile>(injectedFiles), new Processor<PsiFile>() {
      public boolean process(final PsiFile injectedPsi) {
        DocumentWindow documentWindow = (DocumentWindow)PsiDocumentManager.getInstance(myProject).getCachedDocument(injectedPsi);
        if (documentWindow == null) return true;
        Place places = InjectedLanguageUtil.getShreds(injectedPsi);
        for (PsiLanguageInjectionHost.Shred place : places) {
          TextRange textRange = place.getRangeInsideHost().shiftRight(place.host.getTextRange().getStartOffset());
          if (textRange.isEmpty()) continue;
          String desc = injectedPsi.getLanguage().getDisplayName() + ": " + injectedPsi.getText();
          HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.INJECTED_LANGUAGE_FRAGMENT, textRange, null, desc, injectedAttributes);
          outInfos.add(info);
        }

        HighlightInfoHolder holder = createInfoHolder(injectedPsi);
        runHighlightVisitosForInjected(injectedPsi, holder, progress);
        for (int i = 0; i < holder.size(); i++) {
          HighlightInfo info = holder.get(i);
          final int startOffset = documentWindow.injectedToHost(info.startOffset);
          final TextRange fixedTextRange = getFixedTextRange(documentWindow, startOffset);
          addPatchedInfos(info, injectedPsi, documentWindow, injectedLanguageManager, fixedTextRange, outInfos);
        }
        holder.clear();
        highlightInjectedSyntax(injectedPsi, holder);
        for (int i = 0; i < holder.size(); i++) {
          HighlightInfo info = holder.get(i);
          final int startOffset = info.startOffset;
          final TextRange fixedTextRange = getFixedTextRange(documentWindow, startOffset);
          if (fixedTextRange == null) {
            outInfos.add(info);
          }
          else {
            HighlightInfo patched =
              new HighlightInfo(info.forcedTextAttributes, info.forcedTextAttributesKey, info.type,
                                fixedTextRange.getStartOffset(), fixedTextRange.getEndOffset(),
                                info.description, info.toolTip, info.type.getSeverity(null), info.isAfterEndOfLine, null, false);
            outInfos.add(patched);
          }
        }

        if (!isDumbMode()) {
          List<HighlightInfo> todos = new ArrayList<HighlightInfo>();
          highlightTodos(injectedPsi, injectedPsi.getText(), 0, injectedPsi.getTextLength(), progress, myPriorityRange, todos, todos);
          for (HighlightInfo info : todos) {
            addPatchedInfos(info, injectedPsi, documentWindow, injectedLanguageManager, null, outInfos);
          }
        }
        return true;
      }
    }, myFailFastOnAcquireReadAction, progress);
  }

  private static TextRange getFixedTextRange(@NotNull DocumentWindow documentWindow, int startOffset) {
    final TextRange fixedTextRange;
    TextRange textRange = documentWindow.getHostRange(startOffset);
    if (textRange == null) {
      // todo[cdr] check this fix. prefix/suffix code annotation case
      textRange = findNearestTextRange(documentWindow, startOffset);
      final boolean isBefore = startOffset < textRange.getStartOffset();
      fixedTextRange = new ProperTextRange(isBefore ? textRange.getStartOffset() - 1 : textRange.getEndOffset(),
                                     isBefore ? textRange.getStartOffset() : textRange.getEndOffset() + 1);
    }
    else {
      fixedTextRange = null;
    }
    return fixedTextRange;
  }

  private static void addPatchedInfos(@NotNull HighlightInfo info,
                                      @NotNull PsiFile injectedPsi,
                                      @NotNull DocumentWindow documentWindow,
                                      @NotNull InjectedLanguageManager injectedLanguageManager,
                                      @Nullable TextRange fixedTextRange,
                                      @NotNull Collection<HighlightInfo> out) {
    ProperTextRange textRange = new ProperTextRange(info.startOffset, info.endOffset);
    List<TextRange> editables = injectedLanguageManager.intersectWithAllEditableFragments(injectedPsi, textRange);
    for (TextRange editable : editables) {
      TextRange hostRange = fixedTextRange == null ? documentWindow.injectedToHost(editable) : fixedTextRange;

      boolean isAfterEndOfLine = info.isAfterEndOfLine;
      if (isAfterEndOfLine) {
        // convert injected afterEndOfLine to either host' afterEndOfLine or not-afterEndOfLine highlight of the injected fragment boundary
        int hostEndOffset = hostRange.getEndOffset();
        int lineNumber = documentWindow.getDelegate().getLineNumber(hostEndOffset);
        int hostLineEndOffset = documentWindow.getDelegate().getLineEndOffset(lineNumber);
        if (hostEndOffset < hostLineEndOffset) {
          // convert to non-afterEndOfLine
          isAfterEndOfLine = false;
          hostRange = new ProperTextRange(hostRange.getStartOffset(), hostEndOffset+1);
        }
      }

      HighlightInfo patched =
        new HighlightInfo(info.forcedTextAttributes, info.forcedTextAttributesKey, info.type,
                          hostRange.getStartOffset(), hostRange.getEndOffset(),
                          info.description, info.toolTip, info.type.getSeverity(null), isAfterEndOfLine, null, false);
      patched.setHint(info.hasHint());
      patched.setGutterIconRenderer(info.getGutterIconRenderer());

      if (info.quickFixActionRanges != null) {
        for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> pair : info.quickFixActionRanges) {
          TextRange quickfixTextRange = pair.getSecond();
          List<TextRange> editableQF = injectedLanguageManager.intersectWithAllEditableFragments(injectedPsi, quickfixTextRange);
          for (TextRange editableRange : editableQF) {
            HighlightInfo.IntentionActionDescriptor descriptor = pair.getFirst();
            if (patched.quickFixActionRanges == null) patched.quickFixActionRanges = new ArrayList<Pair<HighlightInfo.IntentionActionDescriptor, TextRange>>();
            TextRange hostEditableRange = documentWindow.injectedToHost(editableRange);
            patched.quickFixActionRanges.add(Pair.create(descriptor, hostEditableRange));
          }
        }
      }
      out.add(patched);
    }
  }

  // finds the first nearest text range
  private static TextRange findNearestTextRange(final DocumentWindow documentWindow, final int startOffset) {
    TextRange textRange = null;
    for (RangeMarker marker : documentWindow.getHostRanges()) {
      TextRange curRange = ProperTextRange.create(marker);
      if (curRange.getStartOffset() > startOffset && textRange != null) break;
      textRange = curRange;
    }
    assert textRange != null;
    return textRange;
  }

  private void runHighlightVisitosForInjected(@NotNull PsiFile injectedPsi, @NotNull final HighlightInfoHolder holder, @NotNull final ProgressIndicator progress) {
    HighlightVisitor[] visitors = getHighlightVisitors();
    try {
      HighlightVisitor[] filtered = filterVisitors(visitors, injectedPsi);
      final List<PsiElement> elements = CollectHighlightsUtil.getElementsInRange(injectedPsi, 0, injectedPsi.getTextLength());
      for (final HighlightVisitor hvisitor : filtered) {
        hvisitor.analyze(new Runnable() {
          public void run() {
            for (PsiElement element : elements) {
              progress.checkCanceled();
              hvisitor.visit(element, holder);
            }
          }
        }, true, injectedPsi);
      }
    }
    finally {
      incVisitorUsageCount(-1);
    }
  }

  private void highlightInjectedSyntax(final PsiFile injectedPsi, HighlightInfoHolder holder) {
    List<Trinity<IElementType, PsiLanguageInjectionHost, TextRange>> tokens = InjectedLanguageUtil.getHighlightTokens(injectedPsi);
    if (tokens == null) return;

    final Language injectedLanguage = injectedPsi.getLanguage();
    Project project = injectedPsi.getProject();
    SyntaxHighlighter syntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(injectedLanguage, project, injectedPsi.getVirtualFile());
    final TextAttributes defaultAttrs = myGlobalScheme.getAttributes(HighlighterColors.TEXT);

    for (Trinity<IElementType, PsiLanguageInjectionHost, TextRange> token : tokens) {
      ProgressManager.checkCanceled();
      IElementType tokenType = token.getFirst();
      PsiLanguageInjectionHost injectionHost = token.getSecond();
      TextRange textRange = token.getThird();
      TextAttributesKey[] keys = syntaxHighlighter.getTokenHighlights(tokenType);
      if (textRange.getLength() == 0) continue;

      TextRange annRange = textRange.shiftRight(injectionHost.getTextRange().getStartOffset());
      // force attribute colors to override host' ones
      TextAttributes attributes = null;
      for(TextAttributesKey key:keys) {
        TextAttributes attrs2 = myGlobalScheme.getAttributes(key);
        if (attrs2 != null) {
          attributes = attributes != null ? TextAttributes.merge(attributes, attrs2):attrs2;
        }
      }
      TextAttributes forcedAttributes;
      if (attributes == null || attributes.isEmpty() || attributes.equals(defaultAttrs)) {
        forcedAttributes = TextAttributes.ERASE_MARKER;
      }
      else {
        Color back = attributes.getBackgroundColor() == null ? myGlobalScheme.getDefaultBackground() : attributes.getBackgroundColor();
        Color fore = attributes.getForegroundColor() == null ? myGlobalScheme.getDefaultForeground() : attributes.getForegroundColor();
        forcedAttributes = new TextAttributes(fore, back, attributes.getEffectColor(), attributes.getEffectType(), attributes.getFontType());
      }

      HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.INJECTED_LANGUAGE_FRAGMENT, annRange, null,null,forcedAttributes);
      holder.add(info);
    }
  }

  private boolean isWholeFileHighlighting() {
    return myUpdateAll && myStartOffset == 0 && myEndOffset == myDocument.getTextLength();
  }

  protected void applyInformationWithProgress() {
    myFile.putUserData(HAS_ERROR_ELEMENT, myHasErrorElement);

    myApplyCommand.run();

    if (myUpdateAll) {
      reportErrorsToWolf();
    }
  }

  @NotNull
  Collection<HighlightInfo> getHighlights() {
    return new ArrayList<HighlightInfo>(myHighlights);
  }

  private void collectHighlights(@NotNull final List<PsiElement> elements1,
                                 @NotNull final Runnable after1,
                                 @NotNull final List<PsiElement> elements2,
                                 @NotNull final ProgressIndicator progress,
                                 @NotNull final HighlightVisitor[] visitors,
                                 @NotNull final Set<HighlightInfo> gotHighlights,
                                 final boolean forceHighlightParents) {
    final Set<PsiElement> skipParentsSet = new THashSet<PsiElement>();

    // TODO - add color scheme to holder
    final HighlightInfoHolder holder = createInfoHolder(myFile);

    final int chunkSize = Math.max(1, (elements1.size()+elements2.size()) / 100); // one percent precision is enough

    final Runnable action = new Runnable() {
      public void run() {
        for (List<PsiElement> elements : new List[]{elements1, elements2}) {
          int nextLimit = chunkSize;
          for (int i = 0; i < elements.size(); i++) {
            PsiElement element = elements.get(i);
            progress.checkCanceled();

            if (element != myFile && !skipParentsSet.isEmpty() && element.getFirstChild() != null && skipParentsSet.contains(element)) {
              skipParentsSet.add(element.getParent());
              continue;
            }

            if (element instanceof PsiErrorElement) {
              myHasErrorElement = true;
            }
            holder.clear();

            for (final HighlightVisitor visitor : visitors) {
              visitor.visit(element, holder);
            }

            if (i == nextLimit) {
              advanceProgress(chunkSize);
              nextLimit = i + chunkSize;
            }

            //noinspection ForLoopReplaceableByForEach
            for (int j = 0; j < holder.size(); j++) {
              final HighlightInfo info = holder.get(j);
              assert info != null;
              // have to filter out already obtained highlights
              if (!gotHighlights.add(info)) continue;
              boolean isError = info.getSeverity() == HighlightSeverity.ERROR;
              if (isError) {
                if (!forceHighlightParents) {
                  skipParentsSet.add(element.getParent());
                }
                myErrorFound = true;
              }
              UIUtil.invokeLaterIfNeeded(new Runnable() {
                public void run() {
                  if (progress.isCanceled()) return;

                  final EditorColorsScheme colorsScheme = getColorsScheme();
                  UpdateHighlightersUtil.addHighlighterToEditorIncrementally(myProject, myDocument, myFile, 0,
                                                                             myDocument.getTextLength(),
                                                                             info, colorsScheme, Pass.UPDATE_ALL);
                }
              });
            }
          }
          advanceProgress(elements.size() - (nextLimit-chunkSize));
          if (elements == elements1) after1.run();
        }
      }
    };

    analyzeByVisitors(progress, visitors, action, 0);
  }

  private void analyzeByVisitors(final ProgressIndicator progress, final HighlightVisitor[] visitors, final Runnable action, final int i) {
    if (i == visitors.length) {
      action.run();
    }
    else {
      if (!visitors[i].analyze(new Runnable() {
        @Override
        public void run() {
          analyzeByVisitors(progress, visitors, action, i+1);
        }
      }, myUpdateAll, myFile)) {
        cancelAndRestartDaemonLater(progress, myProject, this);
      }
    }
  }

  private static HighlightVisitor[] filterVisitors(HighlightVisitor[] highlightVisitors, final PsiFile file) {
    final List<HighlightVisitor> visitors = new ArrayList<HighlightVisitor>(highlightVisitors.length);
    List<HighlightVisitor> list = Arrays.asList(highlightVisitors);
    for (HighlightVisitor visitor : DumbService.getInstance(file.getProject()).filterByDumbAwareness(list)) {
      if (visitor.suitableForFile(file)) visitors.add(visitor);
    }
    LOG.assertTrue(!visitors.isEmpty(), list);

    HighlightVisitor[] visitorArray = visitors.toArray(new HighlightVisitor[visitors.size()]);
    Arrays.sort(visitorArray, VISITOR_ORDER_COMPARATOR);
    return visitorArray;
  }

  static Void cancelAndRestartDaemonLater(ProgressIndicator progress, final Project project, TextEditorHighlightingPass pass) {
    PassExecutorService.log(progress, pass, "Cancel and restart");
    progress.cancel();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
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
    final HighlightInfoFilter[] filters = ApplicationManager.getApplication().getExtensions(HighlightInfoFilter.EXTENSION_POINT_NAME);
    return new HighlightInfoHolder(file, getColorsScheme(), filters);
  }

  private static void highlightTodos(@NotNull PsiFile file,
                                     @NotNull CharSequence text,
                                     int startOffset,
                                     int endOffset,
                                     @NotNull ProgressIndicator progress,
                                     @NotNull ProperTextRange priorityRange,
                                     @NotNull Collection<HighlightInfo> result,
                                     @NotNull Collection<HighlightInfo> outsideResult) {
    PsiManager psiManager = file.getManager();
    PsiSearchHelper helper = psiManager.getSearchHelper();
    TodoItem[] todoItems = helper.findTodoItems(file, startOffset, endOffset);
    if (todoItems.length == 0) return;

    for (TodoItem todoItem : todoItems) {
      progress.checkCanceled();
      TextRange range = todoItem.getTextRange();
      String description = text.subSequence(range.getStartOffset(), range.getEndOffset()).toString();
      TextAttributes attributes = todoItem.getPattern().getAttributes().getTextAttributes();
      HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.TODO, range, description, description, attributes);
      assert info != null;
      if (priorityRange.containsRange(info.getStartOffset(), info.getEndOffset())) {
        result.add(info);
      }
      else {
        outsideResult.add(info);
      }
    }
  }

  private void reportErrorsToWolf() {
    if (!myFile.getViewProvider().isPhysical()) return; // e.g. errors in evaluate expression
    Project project = myFile.getProject();
    if (!PsiManager.getInstance(project).isInProject(myFile)) return; // do not report problems in libraries
    VirtualFile file = myFile.getVirtualFile();
    if (file == null) return;

    List<Problem> problems = convertToProblems(getHighlights(), file, myHasErrorElement);
    WolfTheProblemSolver wolf = WolfTheProblemSolver.getInstance(project);

    boolean hasErrors = DaemonCodeAnalyzerImpl.hasErrors(project, getDocument());
    if (!hasErrors || isWholeFileHighlighting()) {
      wolf.reportProblems(file, problems);
    }
    else {
      wolf.weHaveGotProblems(file, problems);
    }
  }

  public double getProgress() {
    // do not show progress of visible highlighters update
    return myUpdateAll ? super.getProgress() : -1;
  }

  private static List<Problem> convertToProblems(final Collection<HighlightInfo> infos,
                                                 final VirtualFile file,
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

  public void setFailFastOnAcquireReadAction(boolean failFastOnAcquireReadAction) {
    myFailFastOnAcquireReadAction = failFastOnAcquireReadAction;
  }
}
