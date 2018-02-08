// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.preview;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestPanel;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.TextRange;
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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.refactoring.extractMethod.ExtractMethodHandler.REFACTORING_NAME;

/**
 * @author Pavel.Dolgov
 */
class PreviewDiffPanel extends BorderLayoutPanel implements Disposable, PreviewTreeListener {
  private final ExtractMethodSnapshot mySnapshot;
  private final SmartPsiElementPointer<PsiElement> myPatternStart;
  private final SmartPsiElementPointer<PsiElement> myPatternEnd;
  private final DiffRequestPanel myDiffPanel;

  public PreviewDiffPanel(ExtractMethodProcessor processor) {
    mySnapshot = new ExtractMethodSnapshot(processor);
    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(processor.getProject());
    PsiElement[] elements = processor.getElements();
    myPatternStart = smartPointerManager.createSmartPsiElementPointer(elements[0]);
    myPatternEnd = smartPointerManager.createSmartPsiElementPointer(elements[elements.length - 1]);

    myDiffPanel = DiffManager.getInstance().createRequestPanel(processor.getProject(), this, null);
    myDiffPanel.putContextHints(DiffUserDataKeys.PLACE, "ExtractMethod");
    myDiffPanel.putContextHints(DiffUserDataKeys.FORCE_READ_ONLY, true);
    addToCenter(myDiffPanel.getComponent());
  }

  @Override
  public void dispose() {
  }

  public void doExtract() {
    // todo filter out excluded duplicates
    PsiElement[] pattern = getPattern();
    if (pattern.length == 0) {
      CommonRefactoringUtil.showErrorHint(mySnapshot.myProject, null, "Failed to extract method", REFACTORING_NAME, HelpID.EXTRACT_METHOD);
      return;
    }
    JavaDuplicatesExtractMethodProcessor processor = new JavaDuplicatesExtractMethodProcessor(pattern, REFACTORING_NAME);
    processor.applyFromSnapshot(mySnapshot);
    if (!processor.prepare(true)) {
      return;
    }

    WriteCommandAction.runWriteCommandAction(mySnapshot.myProject, REFACTORING_NAME, null,
                                             () -> doExtractImpl(processor), pattern[0].getContainingFile());
  }

  private static void doExtractImpl(@NotNull JavaDuplicatesExtractMethodProcessor processor) {
    processor.previewRefactoring();
    List<Match> duplicates = processor.getDuplicates();
    if (duplicates == null && processor.initParametrizedDuplicates(false)) {
      duplicates = processor.getDuplicates();
    }
    processor.doExtract();

    if (!ContainerUtil.isEmpty(duplicates)) {
      for (Match duplicate : duplicates) {
        processor.processMatch(duplicate);
      }
    }
  }


  @Override
  public void onFragmentSelected(FragmentNode node, List<DuplicateNode> enabledDuplicates) {
    PsiElement[] pattern = getPattern();
    PsiFile patternFile = pattern[0].getContainingFile();
    PsiElement[] patternCopy = IntroduceParameterHandler.getElementsInCopy(mySnapshot.myProject, patternFile, pattern, false);

    JavaDuplicatesExtractMethodProcessor copyProcessor = new JavaDuplicatesExtractMethodProcessor(patternCopy, REFACTORING_NAME);
    copyProcessor.applyFromSnapshot(mySnapshot);
    if (!copyProcessor.prepare(false)) {
      return;
    }
    copyProcessor.previewRefactoring();
    List<Match> copyDuplicates = copyProcessor.getDuplicates();
    if (copyDuplicates == null && copyProcessor.initParametrizedDuplicates(false)) {
      copyDuplicates = copyProcessor.getDuplicates();
    }
    if (copyDuplicates == null) return;
    Match selectedDuplicate = null;
    if (node instanceof DuplicateNode) {
      PsiElement selectedStart = ((DuplicateNode)node).getStart();
      PsiElement selectedEnd = ((DuplicateNode)node).getEnd();
      if (selectedStart != null && selectedEnd != null) {
        TextRange selectedRange = new TextRange(selectedStart.getTextRange().getStartOffset(), selectedEnd.getTextRange().getEndOffset());
        for (Match duplicate : copyDuplicates) {
          PsiElement start = duplicate.getMatchStart();
          PsiElement end = duplicate.getMatchEnd();
          if (start != null && end != null) {
            if (start.getTextRange().getStartOffset() == selectedRange.getStartOffset() &&
                end.getTextRange().getEndOffset() == selectedRange.getEndOffset()) {
              selectedDuplicate = duplicate;
              break;
            }
          }
        }
      }
    }

    Bounds patternBounds = new Bounds(patternCopy[0], patternCopy[patternCopy.length - 1]);
    copyProcessor.doExtract();
    ElementsRange patternReplacement = patternBounds.getElementsRange();

    ElementsRange selectedReplacement = null;
    for (Match duplicate : copyDuplicates) {
      Bounds selectedBounds = null;
      if (duplicate == selectedDuplicate) {
        selectedBounds = new Bounds(duplicate.getMatchStart(), duplicate.getMatchEnd());
      }
      copyProcessor.processMatch(duplicate);

      if (selectedBounds != null) {
        selectedReplacement = selectedBounds.getElementsRange();
      }
    }

    Document patternDocument = FileDocumentManager.getInstance().getDocument(patternFile.getViewProvider().getVirtualFile());
    if (patternDocument == null) {
      return;
    }

    PsiMethod method = copyProcessor.getExtractedMethod();
    PsiFile refactoredFile = method.getContainingFile();
    if (refactoredFile != null) {
      method = (PsiMethod)CodeStyleManager.getInstance(method.getProject()).reformat(method);

      Document refactoredDocument = FileDocumentManager.getInstance().getDocument(refactoredFile.getViewProvider().getVirtualFile());
      if (refactoredDocument != null) {
        PsiDocumentManager.getInstance(refactoredFile.getProject()).doPostponedOperationsAndUnblockDocument(refactoredDocument);

        SimpleDiffRequest request;
        if (node instanceof MethodNode) {
          request = getMethodDiffRequest(method, refactoredDocument);
        }
        else if (node instanceof DuplicateNode) {
          TextRange refactoredRange =
            selectedReplacement != null ? selectedReplacement.getTextRange() : new TextRange(0, refactoredDocument.getTextLength());
          request = getFragmentDiffRequest(patternDocument, ((DuplicateNode)node).getStart(), ((DuplicateNode)node).getEnd(),
                                           refactoredDocument, refactoredRange);
        }
        else {
          request = getFragmentDiffRequest(patternDocument, myPatternStart.getElement(), myPatternEnd.getElement(),
                                           refactoredDocument, patternReplacement.getTextRange());
        }
        myDiffPanel.setRequest(request);
      }
    }
  }

  private SimpleDiffRequest getFragmentDiffRequest(Document patternDocument, PsiElement patternStart, PsiElement patternEnd,
                                                   Document refactoredDocument, TextRange refactoredRange) {
    if (patternStart != null && patternEnd != null) {
      TextRange patternRange = new TextRange(patternStart.getTextRange().getStartOffset(), patternEnd.getTextRange().getEndOffset());
      DiffContentFactory contentFactory = DiffContentFactory.getInstance();
      DocumentContent oldContent = contentFactory.createFragment(mySnapshot.myProject, patternDocument, patternRange);
      DocumentContent newContent = contentFactory.createFragment(mySnapshot.myProject, refactoredDocument, refactoredRange);
      return new SimpleDiffRequest(null, oldContent, newContent, null, null);
    }
    return null;
  }

  @NotNull
  private SimpleDiffRequest getMethodDiffRequest(PsiMethod method, Document refactoredDocument) {
    DiffContentFactory contentFactory = DiffContentFactory.getInstance();
    DocumentContent oldContent = contentFactory.create(mySnapshot.myProject, "", JavaFileType.INSTANCE);
    DocumentContent newContent = contentFactory.createFragment(mySnapshot.myProject, refactoredDocument, method.getTextRange());
    return new SimpleDiffRequest(null, oldContent, newContent, null, null);
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
}
