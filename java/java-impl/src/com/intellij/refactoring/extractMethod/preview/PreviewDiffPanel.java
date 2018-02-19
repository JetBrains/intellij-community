// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.preview;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestPanel;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.tools.fragmented.UnifiedDiffTool;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.extractMethod.ExtractMethodSnapshot;
import com.intellij.refactoring.extractMethod.JavaDuplicatesExtractMethodProcessor;
import com.intellij.refactoring.introduceParameter.IntroduceParameterHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.duplicates.Match;
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
  private final ExtractMethodSnapshot mySnapshot;
  private final SmartPsiElementPointer<PsiElement> myPatternStart;
  private final SmartPsiElementPointer<PsiElement> myPatternEnd;
  private final SmartPsiElementPointer<PsiElement> myAnchor;
  private final DiffRequestPanel myDiffPanel;
  private RefactoringResult myRefactoringResult; // accessed from EDT

  public PreviewDiffPanel(@NotNull ExtractMethodProcessor processor) {
    myProject = processor.getProject();
    mySnapshot = new ExtractMethodSnapshot(processor);
    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(myProject);
    PsiElement[] elements = processor.getElements();
    myPatternStart = smartPointerManager.createSmartPsiElementPointer(elements[0]);
    myPatternEnd = smartPointerManager.createSmartPsiElementPointer(elements[elements.length - 1]);
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

  public void doExtract(List<DuplicateNode> enabledNodes) {
    PsiElement[] pattern = getPattern();
    if (pattern.length == 0) {
      CommonRefactoringUtil.showErrorHint(myProject, null, "Failed to extract method", REFACTORING_NAME, HelpID.EXTRACT_METHOD);
      return;
    }
    JavaDuplicatesExtractMethodProcessor processor = new JavaDuplicatesExtractMethodProcessor(pattern, REFACTORING_NAME);
    processor.applyFromSnapshot(mySnapshot);
    if (!processor.prepare(true)) {
      return;
    }

    WriteCommandAction.runWriteCommandAction(myProject, REFACTORING_NAME, null,
                                             () -> doExtractImpl(processor, enabledNodes), pattern[0].getContainingFile());
  }

  public void initLater(@NotNull List<DuplicateNode> allNodes, @NotNull Runnable whenDone) {
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, "Preparing Diff") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        initLaterImpl(allNodes, whenDone, indicator);
      }
    });
  }

  private void initLaterImpl(@NotNull List<DuplicateNode> allNodes,
                             @NotNull Runnable whenDone,
                             @NotNull ProgressIndicator indicator) {
    indicator.setIndeterminate(false);
    int total = allNodes.size() + 9, count = 0;
    PsiElement[] patternCopy = ReadAction.compute(() -> {
      PsiElement[] pattern = getPattern();
      PsiFile patternFile = pattern[0].getContainingFile();
      return IntroduceParameterHandler.getElementsInCopy(myProject, patternFile, pattern, false);
    });
    indicator.setFraction(++count / (double)total); // +1

    JavaDuplicatesExtractMethodProcessor copyProcessor = ReadAction.compute(() -> {
      JavaDuplicatesExtractMethodProcessor processor = new JavaDuplicatesExtractMethodProcessor(patternCopy, REFACTORING_NAME);
      processor.applyFromSnapshot(mySnapshot);
      return processor.prepare(false) ? processor : null;
    });
    indicator.setFraction(++count / (double)total); // +2

    List<Match> simpleDuplicates = ReadAction.compute(() -> {
      copyProcessor.previewRefactoring();
      return copyProcessor.getDuplicates();
    });
    indicator.setFraction(++count / (double)total); // +3

    List<Match> copyDuplicates;
    copyDuplicates = simpleDuplicates != null ? simpleDuplicates :
                     ReadAction.compute(() -> copyProcessor.initParametrizedDuplicates(false) ? copyProcessor.getDuplicates() : null);
    indicator.setFraction(++count / (double)total); // +4

    Map<DuplicateNode, Match> allDuplicates = ReadAction.compute(() -> filterSelectedDuplicates(allNodes, copyDuplicates));
    indicator.setFraction(++count / (double)total); // +5

    ElementsRange originalReplacement = ReadAction.compute(() -> {
      Bounds patternBounds = new Bounds(patternCopy[0], patternCopy[patternCopy.length - 1]);
      copyProcessor.doExtract();
      return patternBounds.getElementsRange();
    });
    indicator.setFraction(++count / (double)total); // +6

    Map<DuplicateNode, ElementsRange> duplicateReplacements = new TreeMap<>();
    for (Map.Entry<DuplicateNode, Match> entry : allDuplicates.entrySet()) {
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
    indicator.setFraction(++count / (double)total); // +8

    Document refactoredDocument = ReadAction.compute(() -> {
      PsiFile refactoredFile = method.getContainingFile();
      if (refactoredFile != null) {
        VirtualFile vFile = refactoredFile.getViewProvider().getVirtualFile();
        vFile.putUserData(DiffUtil.TEMP_FILE_KEY, Boolean.TRUE); // prevent Go To action to non-physical file
        Document document = FileDocumentManager.getInstance().getDocument(vFile);
        if (document != null) {
          PsiDocumentManager.getInstance(copyProcessor.getProject()).doPostponedOperationsAndUnblockDocument(document);
          return document;
        }
      }
      return null;
    });
    indicator.setFraction(++count / (double)total); // +9

    if (refactoredDocument != null) {
      ApplicationManager.getApplication().invokeLater(() -> {
        myRefactoringResult = new RefactoringResult(method, originalReplacement, duplicateReplacements, refactoredDocument);
        whenDone.run();
      });
    }
  }

  private static void doExtractImpl(@NotNull JavaDuplicatesExtractMethodProcessor processor,
                                    List<DuplicateNode> selectedNodes) {
    processor.previewRefactoring();
    List<Match> duplicates = processor.getDuplicates();
    if (duplicates == null && processor.initParametrizedDuplicates(false)) {
      duplicates = processor.getDuplicates();
    }
    Map<DuplicateNode, Match> selectedDuplicates = filterSelectedDuplicates(selectedNodes, duplicates);
    processor.doExtract();

    for (Match duplicate : selectedDuplicates.values()) {
      processor.processMatch(duplicate);
    }
  }

  @Override
  public void onFragmentSelected(FragmentNode node, List<DuplicateNode> enabledDuplicates) {
    if (myRefactoringResult == null) {
      showDiff(null);
      return;
    }
    PsiElement[] pattern = getPattern();
    PsiFile patternFile = pattern[0].getContainingFile();
    assert patternFile != null : "patternFile";

    Document patternDocument = FileDocumentManager.getInstance().getDocument(patternFile.getViewProvider().getVirtualFile());
    if (patternDocument == null) {
      return;
    }
    DiffRequest request;
    if (node instanceof MethodNode) {
      request = getMethodDiffRequest(patternDocument, myRefactoringResult.myDocument, myRefactoringResult.myMethod.getTextRange());
    }
    else if (node instanceof DuplicateNode) {
      DuplicateNode duplicateNode = (DuplicateNode)node;
      ElementsRange selectedReplacement = myRefactoringResult.myDuplicateReplacements.get(duplicateNode);
      assert selectedReplacement != null : "selectedReplacement";
      TextRange patternRange = duplicateNode.getTextRange();
      TextRange refactoredRange = selectedReplacement.getTextRange();

      request = getFragmentDiffRequest(patternDocument, patternRange, myRefactoringResult.myDocument, refactoredRange);
    }
    else {
      TextRange patternRange = DuplicateNode.getTextRange(myPatternStart.getElement(), myPatternEnd.getElement());
      TextRange refactoredRange = myRefactoringResult.myOriginalReplacement.getTextRange();

      request = getFragmentDiffRequest(patternDocument, patternRange, myRefactoringResult.myDocument, refactoredRange);
    }
    showDiff(request);
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
        PsiElement selectedStart = duplicateNode.getStart();
        PsiElement selectedEnd = duplicateNode.getEnd();
        if (selectedStart != null && selectedEnd != null) {
          TextRange selectedRange = new TextRange(selectedStart.getTextRange().getStartOffset(), selectedEnd.getTextRange().getEndOffset());
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

  private void showDiff(@Nullable DiffRequest request) {
    myDiffPanel.setRequest(request);
  }

  @Nullable
  private DiffRequest getFragmentDiffRequest(Document patternDocument, TextRange patternRange,
                                             Document refactoredDocument, TextRange refactoredRange) {
    if (patternRange != null && refactoredRange != null) {
      patternRange = extendRangeToStartOfLine(patternDocument, patternRange);
      DiffContentFactory contentFactory = DiffContentFactory.getInstance();
      DocumentContent oldContent = contentFactory.createFragment(myProject, patternDocument, patternRange);
      refactoredRange = extendRangeToStartOfLine(refactoredDocument, refactoredRange);
      DocumentContent newContent = contentFactory.createFragment(myProject, refactoredDocument, refactoredRange);
      return new SimpleDiffRequest(null, oldContent, newContent, null, null);
    }
    return null;
  }

  @NotNull
  private DiffRequest getMethodDiffRequest(Document patternDocument,
                                           Document refactoredDocument, TextRange refactoredRange) {
    DiffContentFactory contentFactory = DiffContentFactory.getInstance();

    PsiElement element = myAnchor.getElement();
    int anchorOffset = element != null ? element.getTextRange().getEndOffset() : patternDocument.getTextLength();
    DocumentContent oldContent = contentFactory.createFragment(myProject, patternDocument, new TextRange(anchorOffset, anchorOffset));
    refactoredRange = extendRangeToStartOfLine(refactoredDocument, refactoredRange);
    DocumentContent newContent = contentFactory.createFragment(myProject, refactoredDocument, refactoredRange);
    return new SimpleDiffRequest(null, oldContent, newContent, null, null);
  }

  private static TextRange extendRangeToStartOfLine(Document document, TextRange range) {
    CharSequence text = document.getCharsSequence();
    for (int offset = range.getStartOffset(); offset > 0; --offset) {
      char c = text.charAt(offset - 1);
      if (c == '\n' || !Character.isWhitespace(c)) {
        return new TextRange(offset, range.getEndOffset());
      }
    }
    return range;
  }

  @NotNull
  private PsiElement[] getPattern() {
    List<PsiElement> pattern = new ArrayList<>();
    PsiElement startElement = myPatternStart.getElement();
    PsiElement endElement = myPatternEnd.getElement();
    if (startElement != null && endElement != null) {
      for (PsiElement element = startElement; element != null; element = element.getNextSibling()) {
        pattern.add(element);
        if (element == endElement) {
          return pattern.toArray(PsiElement.EMPTY_ARRAY);
        }
      }
    }
    return PsiElement.EMPTY_ARRAY;
  }

  private static class Bounds {
    final PsiElement myParent;
    final PsiElement myBefore;
    final PsiElement myAfter;

    public Bounds(@NotNull PsiElement start, @NotNull PsiElement end) {
      myParent = start.getParent();
      assert myParent != null : "bounds' parent is null";
      myBefore = PsiTreeUtil.skipSiblingsBackward(start, PsiWhiteSpace.class, PsiComment.class);
      myAfter = PsiTreeUtil.skipSiblingsForward(end, PsiWhiteSpace.class, PsiComment.class);
    }

    ElementsRange getElementsRange() {
      PsiElement start = myBefore != null ? PsiTreeUtil.skipSiblingsForward(myBefore, PsiWhiteSpace.class, PsiComment.class) : null;
      PsiElement end = myAfter != null ? PsiTreeUtil.skipSiblingsBackward(myAfter, PsiWhiteSpace.class, PsiComment.class) : null;
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

  static class RefactoringResult {
    final PsiMethod myMethod;
    final ElementsRange myOriginalReplacement;
    final Map<DuplicateNode, ElementsRange> myDuplicateReplacements;
    final Document myDocument;

    public RefactoringResult(@NotNull PsiMethod method,
                             @NotNull ElementsRange originalReplacement,
                             @NotNull Map<DuplicateNode, ElementsRange> duplicateReplacements,
                             @NotNull Document document) {
      myMethod = method;
      myOriginalReplacement = originalReplacement;
      myDuplicateReplacements = duplicateReplacements;
      myDocument = document;
    }
  }
}
