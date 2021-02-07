// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.preview;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestPanel;
import com.intellij.diff.comparison.ComparisonManagerImpl;
import com.intellij.diff.comparison.InnerFragmentsPolicy;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.tools.fragmented.UnifiedDiffTool;
import com.intellij.diff.tools.util.text.LineOffsets;
import com.intellij.diff.tools.util.text.LineOffsetsUtil;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Range;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.extractMethod.ExtractMethodSnapshot;
import com.intellij.refactoring.extractMethod.JavaDuplicatesExtractMethodProcessor;
import com.intellij.refactoring.introduceParameter.IntroduceParameterHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.duplicates.Match;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Pavel.Dolgov
 */
final class PreviewDiffPanel extends BorderLayoutPanel implements Disposable, PreviewTreeListener {
  private final Project myProject;
  private final PreviewTree myTree;
  private final List<SmartPsiElementPointer<PsiElement>> myPattern;
  private final ExtractMethodSnapshot mySnapshot;
  private final SmartPsiElementPointer<PsiElement> myAnchor;
  private final DiffRequestPanel myDiffPanel;
  private PreviewDiffRequest myDiffRequest; // accessed in EDT
  private Document myPatternDocument; // accessed in EDT
  private long myInitialDocumentStamp; // accessed in EDT

  private static final @NonNls String DIFF_PLACE = "ExtractMethod";

  PreviewDiffPanel(@NotNull ExtractMethodProcessor processor, PreviewTree tree) {
    myProject = processor.getProject();
    myTree = tree;
    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(myProject);

    myPattern = ContainerUtil.map2List(processor.getElements(), smartPointerManager::createSmartPsiElementPointer);
    mySnapshot = new ExtractMethodSnapshot(processor);
    myAnchor = smartPointerManager.createSmartPsiElementPointer(processor.getAnchor());

    myDiffPanel = DiffManager.getInstance().createRequestPanel(myProject, this, null);
    myDiffPanel.putContextHints(DiffUserDataKeys.PLACE, DIFF_PLACE);
    myDiffPanel.putContextHints(DiffUserDataKeys.FORCE_READ_ONLY, true);
    myDiffPanel.putContextHints(DiffUserDataKeysEx.FORCE_DIFF_TOOL, UnifiedDiffTool.INSTANCE);
    addToCenter(myDiffPanel.getComponent());
    Disposer.register(this, myDiffPanel);
  }

  @Override
  public void dispose() {
  }

  public void doExtract() {
    List<DuplicateNode> enabledNodes = myTree.getModel().getEnabledDuplicates();
    PsiElement[] pattern = getPatternElements();
    if (pattern.length == 0) {
      // todo suggest re-running the refactoring
      CommonRefactoringUtil.showErrorHint(myProject, null, JavaRefactoringBundle.message("refactoring.extract.method.preview.failed"),
                                          ExtractMethodHandler.getRefactoringName(), HelpID.EXTRACT_METHOD);
      return;
    }
    JavaDuplicatesExtractMethodProcessor processor = new JavaDuplicatesExtractMethodProcessor(pattern,
                                                                                              ExtractMethodHandler.getRefactoringName());
    if (!processor.prepareFromSnapshot(mySnapshot, true)) {
      return;
    }

    WriteCommandAction.runWriteCommandAction(myProject, ExtractMethodHandler.getRefactoringName(), null,
                                             () -> doExtractImpl(processor, enabledNodes), pattern[0].getContainingFile());
  }

  public void initLater() {
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject,
                                                              JavaRefactoringBundle.message("refactoring.extract.method.preview.preparing")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        updateLaterImpl(indicator, false);
      }
    });
  }

  public void updateLater() {
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject,
                                                              JavaRefactoringBundle.message("refactoring.extract.method.preview.updating")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        updateLaterImpl(indicator, true);
      }
    });
  }

  private void updateLaterImpl(@NotNull ProgressIndicator indicator, boolean onlyEnabled) {
    List<DuplicateNode> allNodes = myTree.getModel().getAllDuplicates();
    List<? extends DuplicateNode> selectedNodes = onlyEnabled ? myTree.getModel().getEnabledDuplicates() : allNodes;
    IncrementalProgress progress = new IncrementalProgress(indicator, selectedNodes.size() + 4);

    PsiElement[] pattern = ReadAction.compute(() -> getPatternElements());
    PsiElement[] patternCopy = ReadAction.compute(() -> {
      PsiFile patternFile = pattern[0].getContainingFile();
      return IntroduceParameterHandler.getElementsInCopy(myProject, patternFile, pattern, false);
    });
    progress.increment(); // +1

    ExtractMethodSnapshot copySnapshot = ReadAction.compute(() -> new ExtractMethodSnapshot(mySnapshot, pattern, patternCopy));

    ExtractMethodProcessor copyProcessor = ReadAction.compute(() -> {
      JavaDuplicatesExtractMethodProcessor processor = new JavaDuplicatesExtractMethodProcessor(patternCopy,
                                                                                                ExtractMethodHandler.getRefactoringName());
      return processor.prepareFromSnapshot(copySnapshot, true) ? processor : null;
    });
    if (copyProcessor == null) return;

    Map<DuplicateNode, Match> duplicateMatches = ReadAction.compute(() -> findSelectedDuplicates(copyProcessor, selectedNodes));

    List<Duplicate> excludedDuplicates =
      onlyEnabled && allNodes.size() != duplicateMatches.size()
      ? ReadAction.compute(() -> collectExcludedRanges(allNodes, duplicateMatches.keySet(), patternCopy[0].getContainingFile()))
      : Collections.emptyList();

    ElementsRange patternReplacement = ReadAction.compute(() -> {
      Bounds bounds = new Bounds(patternCopy[0], patternCopy[patternCopy.length - 1]);
      copyProcessor.doExtract();
      return bounds.getElementsRange();
    });
    progress.increment(); // +2

    ReadAction.run(() -> copyProcessor.initParametrizedDuplicates(false));
    progress.increment(); // +3

    List<Duplicate> duplicateReplacements = new ArrayList<>();
    for (Map.Entry<DuplicateNode, Match> entry : duplicateMatches.entrySet()) {
      DuplicateNode duplicateNode = entry.getKey();
      Match duplicate = entry.getValue();

      ReadAction.run(() -> {
        Bounds bounds = new Bounds(duplicate.getMatchStart(), duplicate.getMatchEnd());
        copyProcessor.processMatch(duplicate);
        ElementsRange replacement = bounds.getElementsRange();
        duplicateReplacements.add(new Duplicate(duplicateNode, replacement));
      });
      progress.increment(); // +size()
    }

    PsiMethod method = ReadAction.compute(() -> {
      PsiMethod extractedMethod = copyProcessor.getExtractedMethod();
      return (PsiMethod)CodeStyleManager.getInstance(myProject).reformat(extractedMethod);
    });

    Document refactoredDocument = ReadAction.compute(() -> {
      PsiFile refactoredFile = method.getContainingFile();
      if (refactoredFile != null) {
        VirtualFile vFile = refactoredFile.getViewProvider().getVirtualFile();
        vFile.putUserData(DiffUtil.TEMP_FILE_KEY, Boolean.TRUE); // prevent Go To action to non-physical file
        return FileDocumentManager.getInstance().getDocument(vFile);
      }
      return null;
    });
    progress.increment(); // +4

    if (refactoredDocument != null) {
      ApplicationManager.getApplication().invokeLater(() -> {
        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
        documentManager.doPostponedOperationsAndUnblockDocument(refactoredDocument);
        MethodNode methodNode = myTree.getModel().updateMethod(method);

        initDiff(pattern, patternReplacement, refactoredDocument,
                 methodNode, method.getTextRange(),
                 duplicateReplacements, excludedDuplicates);

        myTree.onUpdateLater();
      });
    }
  }

  private void initDiff(PsiElement @NotNull [] pattern,
                        @NotNull ElementsRange patternReplacement,
                        @NotNull Document refactoredDocument,
                        @NotNull MethodNode methodNode,
                        @NotNull TextRange methodRange,
                        @NotNull List<? extends Duplicate> duplicateReplacements,
                        @NotNull List<? extends Duplicate> excludedDuplicates) {
    PsiFile patternFile = pattern[0].getContainingFile();
    myPatternDocument = FileDocumentManager.getInstance().getDocument(patternFile.getViewProvider().getVirtualFile());
    if (myPatternDocument == null) {
      return;
    }
    myInitialDocumentStamp = myPatternDocument.getModificationStamp();

    List<Range> diffRanges = new ArrayList<>();
    Map<FragmentNode, Couple<TextRange>> linesBounds = new HashMap<>();
    collectDiffRanges(refactoredDocument, diffRanges, linesBounds, duplicateReplacements);
    collectDiffRanges(refactoredDocument, diffRanges, linesBounds, excludedDuplicates);

    PsiElement anchorElement = myAnchor.getElement();
    if (anchorElement != null) {
      int anchorLineNumber = getLineNumberAfter(myPatternDocument, anchorElement.getTextRange());
      int anchorOffset = myPatternDocument.getLineStartOffset(anchorLineNumber);
      Range diffRange = new Range(anchorLineNumber,
                                  anchorLineNumber,
                                  getStartLineNumber(refactoredDocument, methodRange),
                                  getLineNumberAfter(refactoredDocument, methodRange));
      diffRanges.add(diffRange);
      linesBounds.put(methodNode, Couple.of(new TextRange(anchorOffset, anchorOffset),
                                            getLinesRange(methodRange, refactoredDocument)));
    }
    TextRange patternRange = new ElementsRange(pattern).getTextRange();
    TextRange replacementRange = patternReplacement.getTextRange();
    Range diffRange = getDiffRange(patternRange, myPatternDocument, replacementRange, refactoredDocument);
    if (diffRange != null) {
      diffRanges.add(diffRange);
      linesBounds.put(myTree.getModel().getPatternNode(), Couple.of(getLinesRange(patternRange, myPatternDocument),
                                                                    getLinesRange(replacementRange, refactoredDocument)));
    }
    diffRanges.sort(Comparator.comparing(r -> r.start1));

    DiffContentFactory contentFactory = DiffContentFactory.getInstance();
    DocumentContent oldContent = contentFactory.create(myProject, myPatternDocument);
    DocumentContent newContent = contentFactory.create(myProject, refactoredDocument.getText(), patternFile.getFileType(), false);

    myDiffRequest = new PreviewDiffRequest(linesBounds, oldContent, newContent, node -> myTree.selectNode(node));
    myDiffRequest.putUserData(DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER, getDiffComputer(diffRanges));
    myDiffPanel.setRequest(myDiffRequest);
    myDiffRequest.onInitialized();
  }

  private void collectDiffRanges(@NotNull Document refactoredDocument,
                                 @NotNull List<? super Range> diffRanges,
                                 @NotNull Map<FragmentNode, Couple<TextRange>> linesBounds,
                                 @NotNull List<? extends Duplicate> duplicates) {
    for (Duplicate duplicate : duplicates) {
      DuplicateNode duplicateNode = duplicate.myNode;
      TextRange patternRange = duplicateNode.getTextRange();
      TextRange copyRange = duplicate.myCopy.getTextRange();
      Range diffRange = getDiffRange(patternRange, myPatternDocument, copyRange, refactoredDocument);
      if (diffRange != null) {
        diffRanges.add(diffRange);
        linesBounds.put(duplicateNode, Couple.of(getLinesRange(patternRange, myPatternDocument),
                                                 getLinesRange(copyRange, refactoredDocument)));
      }
    }
  }

  private static int getStartLineNumber(Document document, TextRange textRange) {
    return document.getLineNumber(textRange.getStartOffset());
  }

  private static int getLineNumberAfter(Document document, TextRange textRange) {
    return Math.min(document.getLineNumber(textRange.getEndOffset()) + 1, document.getLineCount());
  }

  private static Range getDiffRange(@Nullable TextRange patternRange,
                                    @NotNull Document patternDocument,
                                    @Nullable TextRange refactoredRange,
                                    @NotNull Document refactoredDocument) {
    if (patternRange == null || refactoredRange == null) {
      return null;
    }
    return new Range(getStartLineNumber(patternDocument, patternRange),
                     getLineNumberAfter(patternDocument, patternRange),
                     getStartLineNumber(refactoredDocument, refactoredRange),
                     getLineNumberAfter(refactoredDocument, refactoredRange));
  }

  private static @NotNull TextRange getLinesRange(@NotNull TextRange textRange, @NotNull Document document) {
    int startLine = document.getLineNumber(textRange.getStartOffset());
    int endLine = document.getLineNumber(Math.min(textRange.getEndOffset(), document.getTextLength()));
    return new TextRange(document.getLineStartOffset(startLine), document.getLineEndOffset(endLine));
  }


  private static List<Duplicate> collectExcludedRanges(@NotNull List<? extends DuplicateNode> allNodes,
                                                       @NotNull Set<? extends DuplicateNode> selectedNodes,
                                                       @NotNull PsiFile copyFile) {
    List<Duplicate> excludedRanges = new ArrayList<>();
    for (DuplicateNode node : allNodes) {
      if (selectedNodes.contains(node)) {
        continue;
      }
      ElementsRange elementsRange = node.getElementsRange();
      if (elementsRange != null) {
        ElementsRange copyRange = elementsRange.findCopyInFile(copyFile);
        if (copyRange != null) {
          excludedRanges.add(new Duplicate(node, copyRange));
        }
      }
    }
    return excludedRanges;
  }

  private static void doExtractImpl(@NotNull JavaDuplicatesExtractMethodProcessor processor,
                                    @NotNull List<? extends DuplicateNode> selectedNodes) {
    Map<DuplicateNode, Match> selectedDuplicates = findSelectedDuplicates(processor, selectedNodes);
    processor.doExtract();
    processor.initParametrizedDuplicates(false);

    for (Match duplicate : selectedDuplicates.values()) {
      processor.processMatch(duplicate);
    }
    PsiMethodCallExpression methodCall = processor.getMethodCall();
    if (methodCall != null && methodCall.isValid()) {
      Editor editor = getEditor(methodCall.getContainingFile(), false);
      if (editor != null) {
        editor.getSelectionModel().removeSelection();
        editor.getCaretModel().moveToOffset(methodCall.getTextOffset());
      }
    }
  }

  @Override
  public void onNodeSelected(@NotNull FragmentNode node) {
    if (myDiffRequest != null) {
      myDiffRequest.onNodeSelected(node);
    }
  }

  private static @NotNull Map<DuplicateNode, Match> findSelectedDuplicates(@NotNull ExtractMethodProcessor processor,
                                                                           @NotNull List<? extends DuplicateNode> selectedNodes) {
    Set<TextRange> textRanges = ContainerUtil.map2SetNotNull(selectedNodes, FragmentNode::getTextRange);
    processor.previewRefactoring(textRanges);
    List<Match> duplicates = processor.getAnyDuplicates();
    return filterSelectedDuplicates(selectedNodes, duplicates);
  }

  private static @NotNull Map<DuplicateNode, Match> filterSelectedDuplicates(@NotNull Collection<? extends DuplicateNode> selectedNodes,
                                                                             @Nullable List<Match> allDuplicates) {
    if (ContainerUtil.isEmpty(allDuplicates)) {
      return Collections.emptyMap();
    }
    Map<DuplicateNode, Match> selectedDuplicates = new HashMap<>();
    for (DuplicateNode duplicateNode : selectedNodes) {
      TextRange selectedRange = duplicateNode.getTextRange();
      if (selectedRange != null) {
        for (Match duplicate : allDuplicates) {
          PsiElement start = duplicate.getMatchStart();
          PsiElement end = duplicate.getMatchEnd();
          if (start != null && end != null &&
              selectedRange.equalsToRange(start.getTextRange().getStartOffset(), end.getTextRange().getEndOffset())) {
            selectedDuplicates.put(duplicateNode, duplicate);
            break;
          }
        }
      }
    }
    return selectedDuplicates;
  }

  private static @NotNull DiffUserDataKeysEx.DiffComputer getDiffComputer(@NotNull Collection<? extends Range> ranges) {
    return (text1, text2, policy, innerChanges, indicator) -> {
      InnerFragmentsPolicy fragmentsPolicy = innerChanges ? InnerFragmentsPolicy.WORDS : InnerFragmentsPolicy.NONE;
      LineOffsets offsets1 = LineOffsetsUtil.create(text1);
      LineOffsets offsets2 = LineOffsetsUtil.create(text2);

      List<LineFragment> result = new ArrayList<>();
      ComparisonManagerImpl comparisonManager = ComparisonManagerImpl.getInstanceImpl();
      for (Range range : ranges) {
        result.addAll(comparisonManager.compareLinesInner(range, text1, text2, offsets1, offsets2, policy, fragmentsPolicy, indicator));
      }
      return result;
    };
  }

  public void tryExtractAgain() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    PsiElement[] elements = getPatternElements();
    if (elements.length != 0) {
      PsiFile psiFile = elements[0].getContainingFile();
      if (psiFile != null) {
        ExtractMethodSnapshot.SNAPSHOT_KEY.set(psiFile, mySnapshot);
        try {
          Editor editor = getEditor(psiFile, true);
          ExtractMethodHandler.invokeOnElements(myProject, editor, psiFile, elements);
          return;
        }
        finally {
          ExtractMethodSnapshot.SNAPSHOT_KEY.set(psiFile, null);
        }
      }
    }
    Messages.showErrorDialog(myProject, JavaRefactoringBundle.message("can.t.restore.context.for.method.extraction"),
                             JavaRefactoringBundle.message("failed.to.re.run.refactoring"));
  }

  private PsiElement @NotNull [] getPatternElements() {
    PsiElement[] elements = ContainerUtil.map2Array(myPattern, PsiElement.EMPTY_ARRAY, SmartPsiElementPointer::getElement);
    if (ArrayUtil.contains(null, elements)) {
      return PsiElement.EMPTY_ARRAY;
    }
    return elements;
  }

  private static @Nullable Editor getEditor(@NotNull PsiFile psiFile, boolean canCreate) {
    VirtualFile vFile = psiFile.getViewProvider().getVirtualFile();
    Document document = FileDocumentManager.getInstance().getDocument(vFile);
    if (document == null) {
      return null;
    }

    Project project = psiFile.getProject();
    return EditorFactory.getInstance().editors(document, project)
      .findFirst()
      .orElseGet(() -> canCreate ? EditorFactory.getInstance().createEditor(document, project) : null);
  }

  boolean isModified() {
    return myPatternDocument == null || myPatternDocument.getModificationStamp() != myInitialDocumentStamp;
  }

  private static class Bounds {
    private static final Class[] SKIP_TYPES = {PsiWhiteSpace.class, PsiComment.class, PsiEmptyStatement.class};
    final PsiElement myParent;
    final PsiElement myBefore;
    final PsiElement myAfter;

    Bounds(@NotNull PsiElement start, @NotNull PsiElement end) {
      myParent = start.getParent();
      assert myParent != null : "bounds' parent is null";
      myBefore = PsiTreeUtil.skipSiblingsBackward(start, SKIP_TYPES);
      myAfter = PsiTreeUtil.skipSiblingsForward(end, SKIP_TYPES);
    }

    ElementsRange getElementsRange() {
      PsiElement start = PsiTreeUtil.skipSiblingsForward(myBefore, SKIP_TYPES);
      PsiElement end = PsiTreeUtil.skipSiblingsBackward(myAfter, SKIP_TYPES);
      if (start == null) start = myParent.getFirstChild();
      if (end == null) end = myParent.getLastChild();
      if (start != null && end != null) {
        return new ElementsRange(start, end);
      }
      return new ElementsRange(myParent, myParent);
    }
  }

  private static class Duplicate {
    final DuplicateNode myNode;
    final ElementsRange myCopy;

    Duplicate(DuplicateNode node, ElementsRange copy) {
      myNode = node;
      myCopy = copy;
    }
  }

  static class IncrementalProgress {
    private final ProgressIndicator myIndicator;
    private final double myTotal;
    private int myCount;

    IncrementalProgress(@NotNull ProgressIndicator indicator, int total) {
      myIndicator = indicator;
      myTotal = total;
      indicator.setIndeterminate(false);
    }

    void increment() {
      myIndicator.setFraction(++myCount / myTotal);
    }
  }
}
