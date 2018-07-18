// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.preview;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestPanel;
import com.intellij.diff.comparison.ComparisonManagerImpl;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.tools.fragmented.UnifiedDiffTool;
import com.intellij.diff.tools.util.text.LineOffsets;
import com.intellij.diff.tools.util.text.LineOffsetsUtil;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Range;
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
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.refactoring.extractMethod.ExtractMethodHandler.REFACTORING_NAME;

/**
 * @author Pavel.Dolgov
 */
class PreviewDiffPanel extends BorderLayoutPanel implements Disposable, PreviewTreeListener {
  private final Project myProject;
  private final PreviewTree myTree;
  private final List<SmartPsiElementPointer<PsiElement>> myPattern;
  private final ExtractMethodSnapshot mySnapshot;
  private final SmartPsiElementPointer<PsiElement> myAnchor;
  private final DiffRequestPanel myDiffPanel;
  private PreviewDiffRequest myDiffRequest; // accessed in EDT
  private Document myPatternDocument; // accessed in EDT
  private long myInitialDocumentStamp; // accessed in EDT

  public PreviewDiffPanel(@NotNull ExtractMethodProcessor processor, PreviewTree tree) {
    myProject = processor.getProject();
    myTree = tree;
    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(myProject);

    myPattern = ContainerUtil.map2List(processor.getElements(), smartPointerManager::createSmartPsiElementPointer);
    mySnapshot = new ExtractMethodSnapshot(processor);
    myAnchor = smartPointerManager.createSmartPsiElementPointer(processor.getAnchor());

    myDiffPanel = DiffManager.getInstance().createRequestPanel(myProject, this, null);
    myDiffPanel.putContextHints(DiffUserDataKeys.PLACE, "ExtractMethod");
    myDiffPanel.putContextHints(DiffUserDataKeys.FORCE_READ_ONLY, true);
    myDiffPanel.putContextHints(DiffUserDataKeysEx.FORCE_DIFF_TOOL, UnifiedDiffTool.INSTANCE);
    addToCenter(myDiffPanel.getComponent());
  }

  @Override
  public void dispose() {
  }

  public void doExtract() {
    List<DuplicateNode> enabledNodes = myTree.getModel().getEnabledDuplicates();
    PsiElement[] pattern = getPatternElements();
    if (pattern.length == 0) {
      // todo suggest re-running the refactoring
      CommonRefactoringUtil.showErrorHint(myProject, null, "Failed to extract method", REFACTORING_NAME, HelpID.EXTRACT_METHOD);
      return;
    }
    JavaDuplicatesExtractMethodProcessor processor = new JavaDuplicatesExtractMethodProcessor(pattern, REFACTORING_NAME);
    if (!processor.prepareFromSnapshot(mySnapshot, true)) {
      return;
    }

    WriteCommandAction.runWriteCommandAction(myProject, REFACTORING_NAME, null,
                                             () -> doExtractImpl(processor, enabledNodes), pattern[0].getContainingFile());
  }

  public void initLater() {
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, "Preparing Diff") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        initLaterImpl(indicator);
      }
    });
  }

  private void initLaterImpl(@NotNull ProgressIndicator indicator) {
    PreviewTreeModel treeModel = myTree.getModel();
    indicator.setIndeterminate(false);
    List<DuplicateNode> allDuplicates = treeModel.getAllDuplicates();
    int total = allDuplicates.size() + 4, count = 0;

    PsiElement[] pattern = ReadAction.compute(() -> getPatternElements());
    PsiElement[] patternCopy = ReadAction.compute(() -> {
      PsiFile patternFile = pattern[0].getContainingFile();
      return IntroduceParameterHandler.getElementsInCopy(myProject, patternFile, pattern, false);
    });
    indicator.setFraction(++count / (double)total); // +1

    JavaDuplicatesExtractMethodProcessor copyProcessor = ReadAction.compute(() -> {
      JavaDuplicatesExtractMethodProcessor processor = new JavaDuplicatesExtractMethodProcessor(patternCopy, REFACTORING_NAME);
      return processor.prepareFromSnapshot(mySnapshot, false) ? processor : null;
    });

    List<Match> copyDuplicates = ReadAction.compute(() -> {
      copyProcessor.previewRefactoring();
      return PreviewTreeModel.getDuplicates(copyProcessor);
    });

    Map<DuplicateNode, Match> duplicateMatches = ReadAction.compute(() -> filterSelectedDuplicates(allDuplicates, copyDuplicates));

    Bounds patternReplacementBounds = ReadAction.compute(() -> {
      Bounds patternBounds = new Bounds(patternCopy[0], patternCopy[patternCopy.length - 1]);
      copyProcessor.doExtract();
      return patternBounds;
    });
    indicator.setFraction(++count / (double)total); // +2

    ReadAction.run(() -> copyProcessor.initParametrizedDuplicates(false));
    indicator.setFraction(++count / (double)total); // +3

    Map<DuplicateNode, ElementsRange> duplicateReplacements = new TreeMap<>();
    for (Map.Entry<DuplicateNode, Match> entry : duplicateMatches.entrySet()) {
      DuplicateNode duplicateNode = entry.getKey();
      Match duplicate = entry.getValue();

      ReadAction.run(() -> {
        Bounds bounds = new Bounds(duplicate.getMatchStart(), duplicate.getMatchEnd());
        copyProcessor.processMatch(duplicate);
        ElementsRange replacement = bounds.getElementsRange();
        duplicateReplacements.put(duplicateNode, replacement);
      });
      indicator.setFraction(++count / (double)total); // +size()
    }

    PsiMethod method = ReadAction.compute(() -> {
      PsiMethod extractedMethod = copyProcessor.getExtractedMethod();
      return (PsiMethod)CodeStyleManager.getInstance(copyProcessor.getProject()).reformat(extractedMethod);
    });

    ElementsRange patternReplacement = ReadAction.compute(() -> patternReplacementBounds.getElementsRange());

    Document refactoredDocument = ReadAction.compute(() -> {
      PsiFile refactoredFile = method.getContainingFile();
      if (refactoredFile != null) {
        VirtualFile vFile = refactoredFile.getViewProvider().getVirtualFile();
        vFile.putUserData(DiffUtil.TEMP_FILE_KEY, Boolean.TRUE); // prevent Go To action to non-physical file
        return FileDocumentManager.getInstance().getDocument(vFile);
      }
      return null;
    });
    indicator.setFraction(++count / (double)total); // +4

    if (refactoredDocument != null) {
      ApplicationManager.getApplication().invokeLater(() -> {
        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(copyProcessor.getProject());
        documentManager.doPostponedOperationsAndUnblockDocument(refactoredDocument);
        MethodNode methodNode = myTree.getModel().updateMethod(method);

        initDiff(pattern, patternReplacement, refactoredDocument,
                 methodNode, method.getTextRange(),
                 duplicateReplacements);

        myTree.onUpdateLater();
      });
    }
  }

  private void initDiff(@NotNull PsiElement[] pattern,
                        @NotNull ElementsRange patternReplacement,
                        @NotNull Document refactoredDocument,
                        @NotNull MethodNode methodNode,
                        @NotNull TextRange methodRange,
                        @NotNull Map<DuplicateNode, ElementsRange> duplicateReplacements) {
    PsiFile patternFile = pattern[0].getContainingFile();
    myPatternDocument = FileDocumentManager.getInstance().getDocument(patternFile.getViewProvider().getVirtualFile());
    if (myPatternDocument == null) {
      return;
    }
    myInitialDocumentStamp = myPatternDocument.getModificationStamp();

    List<Range> diffRanges = new ArrayList<>();
    Map<FragmentNode, Couple<TextRange>> linesBounds = new HashMap<>();

    for (Map.Entry<DuplicateNode, ElementsRange> entry : duplicateReplacements.entrySet()) {
      DuplicateNode duplicateNode = entry.getKey();
      TextRange patternRange = duplicateNode.getTextRange();
      TextRange matchRange = entry.getValue().getTextRange();
      Range diffRange = getDiffRange(patternRange, myPatternDocument, matchRange, refactoredDocument);
      if (diffRange != null) {
        diffRanges.add(diffRange);
        linesBounds.put(duplicateNode, Couple.of(getLinesRange(patternRange, myPatternDocument),
                                                 getLinesRange(matchRange, refactoredDocument)));
      }
    }
    PsiElement anchorElement = myAnchor.getElement();
    if (anchorElement != null) {
      int anchorOffset = anchorElement.getTextRange().getEndOffset();
      int anchorLineNumber = myPatternDocument.getLineNumber(anchorOffset);
      Range diffRange = new Range(anchorLineNumber,
                                  anchorLineNumber,
                                  getStartLineNumber(refactoredDocument, methodRange),
                                  getLineNumberAfter(refactoredDocument, methodRange));
      diffRanges.add(diffRange);
      linesBounds.put(methodNode, Couple.of(new TextRange(anchorOffset, anchorOffset),
                                            getLinesRange(methodRange, refactoredDocument)));
    }
    TextRange patternRange = ExtractableFragment.getTextRange(pattern);
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
    DocumentContent newContent = contentFactory.create(myProject, refactoredDocument);

    myDiffRequest = new PreviewDiffRequest(linesBounds, oldContent, newContent, node -> myTree.selectNode(node));
    myDiffRequest.putUserData(DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER, getDiffComputer(diffRanges));
    myDiffPanel.setRequest(myDiffRequest);
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

  @NotNull
  private static TextRange getLinesRange(@NotNull TextRange textRange, @NotNull Document document) {
    int startLine = document.getLineNumber(textRange.getStartOffset());
    int endLine = document.getLineNumber(Math.min(textRange.getEndOffset(), document.getTextLength()));
    return new TextRange(document.getLineStartOffset(startLine), document.getLineEndOffset(endLine));
  }

  private static void doExtractImpl(@NotNull JavaDuplicatesExtractMethodProcessor processor,
                                    @NotNull List<DuplicateNode> selectedNodes) {
    processor.previewRefactoring();
    List<Match> duplicates = PreviewTreeModel.getDuplicates(processor);
    Map<DuplicateNode, Match> selectedDuplicates = filterSelectedDuplicates(selectedNodes, duplicates);
    processor.doExtract();
    processor.initParametrizedDuplicates(false);

    for (Match duplicate : selectedDuplicates.values()) {
      processor.processMatch(duplicate);
    }
  }

  @Override
  public void onNodeSelected(@NotNull FragmentNode node) {
    if (myDiffRequest != null) {
      myDiffRequest.onNodeSelected(node);
    }
  }

  @NotNull
  private static Map<DuplicateNode, Match> filterSelectedDuplicates(@NotNull Collection<? extends FragmentNode> selectedNodes,
                                                                    @Nullable List<Match> allDuplicates) {
    if (ContainerUtil.isEmpty(allDuplicates)) {
      return Collections.emptyMap();
    }
    Map<DuplicateNode, Match> selectedDuplicates = new THashMap<>();
    for (FragmentNode node : selectedNodes) {
      if (node instanceof DuplicateNode) {
        DuplicateNode duplicateNode = (DuplicateNode)node;
        TextRange selectedRange = duplicateNode.getTextRange();
        if (selectedRange != null) {
          for (Match duplicate : allDuplicates) {
            PsiElement start = duplicate.getMatchStart();
            PsiElement end = duplicate.getMatchEnd();
            if (start != null && end != null &&
                start.getTextRange().getStartOffset() == selectedRange.getStartOffset() &&
                end.getTextRange().getEndOffset() == selectedRange.getEndOffset()) {
              selectedDuplicates.put(duplicateNode, duplicate);
              break;
            }
          }
        }
      }
    }
    return selectedDuplicates;
  }

  @NotNull
  private static DiffUserDataKeysEx.DiffComputer getDiffComputer(@NotNull Collection<Range> ranges) {
    return (text1, text2, policy, innerChanges, indicator) -> {
      LineOffsets offsets1 = LineOffsetsUtil.create(text1);
      LineOffsets offsets2 = LineOffsetsUtil.create(text2);

      List<LineFragment> result = new ArrayList<>();
      ComparisonManagerImpl comparisonManager = ComparisonManagerImpl.getInstanceImpl();
      for (Range range : ranges) {
        result.addAll(comparisonManager.compareLinesInner(range, text1, text2, offsets1, offsets2, policy, innerChanges, indicator));
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
          Editor editor = getEditor(psiFile);
          ExtractMethodHandler.invokeOnElements(myProject, editor, psiFile, elements);
          return;
        }
        finally {
          ExtractMethodSnapshot.SNAPSHOT_KEY.set(psiFile, null);
        }
      }
    }
    Messages.showErrorDialog(myProject, "Can't restore context for method extraction", "Failed to Re-Run Refactoring");
  }

  @NotNull
  private PsiElement[] getPatternElements() {
    PsiElement[] elements = ContainerUtil.map2Array(myPattern, PsiElement.EMPTY_ARRAY, SmartPsiElementPointer::getElement);
    if (ArrayUtil.contains(null, elements)) {
      return PsiElement.EMPTY_ARRAY;
    }
    return elements;
  }

  @Nullable
  private Editor getEditor(@NotNull PsiFile psiFile) {
    VirtualFile vFile = psiFile.getViewProvider().getVirtualFile();
    Document document = FileDocumentManager.getInstance().getDocument(vFile);
    if (document == null) {
      return null;
    }
    EditorFactory factory = EditorFactory.getInstance();
    Editor[] editors = factory.getEditors(document, myProject);
    if (editors.length != 0) {
      return editors[0];
    }
    return EditorFactory.getInstance().createEditor(document, myProject);
  }

  boolean isModified() {
    return myPatternDocument == null || myPatternDocument.getModificationStamp() != myInitialDocumentStamp;
  }

  private static class Bounds {
    private static final Class[] SKIP_TYPES = {PsiWhiteSpace.class, PsiComment.class, PsiEmptyStatement.class};
    final PsiElement myParent;
    final PsiElement myBefore;
    final PsiElement myAfter;

    public Bounds(@NotNull PsiElement start, @NotNull PsiElement end) {
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

  static class ElementsRange {
    PsiElement myStart;
    PsiElement myEnd;

    public ElementsRange(@NotNull PsiElement start, @NotNull PsiElement end) {
      myStart = start;
      myEnd = end;
    }

    public TextRange getTextRange() {
      return new TextRange(myStart.getTextRange().getStartOffset(), myEnd.getTextRange().getEndOffset());
    }
  }
}
