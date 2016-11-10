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
package com.intellij.refactoring.changeSignature.inplace;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.FinishMarkAction;
import com.intellij.openapi.command.impl.StartMarkAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.ChangeInfo;
import com.intellij.refactoring.changeSignature.ChangeSignatureHandler;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.PositionTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;

public class InplaceChangeSignature implements DocumentListener {
  public static final Key<InplaceChangeSignature> INPLACE_CHANGE_SIGNATURE = Key.create("EditorInplaceChangeSignature");
  private ChangeInfo myCurrentInfo;
  private ChangeInfo myStableChange;
  private String myInitialSignature;
  private Editor myEditor;
  private LanguageChangeSignatureDetector<ChangeInfo> myDetector;

  private final Project myProject;
  private final PsiDocumentManager myDocumentManager;
  private final ArrayList<RangeHighlighter> myHighlighters = new ArrayList<>();
  private StartMarkAction myMarkAction;
  private Balloon myBalloon;
  private boolean myDelegate;

  public InplaceChangeSignature(Project project, Editor editor, @NotNull PsiElement element) {
    myDocumentManager = PsiDocumentManager.getInstance(project);
    myProject = project;
    try {
      myMarkAction = StartMarkAction.start(editor, project, ChangeSignatureHandler.REFACTORING_NAME);
    }
    catch (StartMarkAction.AlreadyStartedException e) {
      final int exitCode = Messages.showYesNoDialog(myProject, e.getMessage(), ChangeSignatureHandler.REFACTORING_NAME, "Navigate to Started", "Cancel", Messages.getErrorIcon());
      if (exitCode == Messages.CANCEL) return;
      PsiElement method = myStableChange.getMethod();
      VirtualFile virtualFile = PsiUtilCore.getVirtualFile(method);
      new OpenFileDescriptor(project, virtualFile, method.getTextOffset()).navigate(true);
      return;
    }


    myEditor = editor;
    myDetector = LanguageChangeSignatureDetectors.INSTANCE.forLanguage(element.getLanguage());
    myStableChange = myDetector.createInitialChangeInfo(element);
    myInitialSignature = myDetector.extractSignature(myStableChange);
    TextRange highlightingRange = myDetector.getHighlightingRange(myStableChange);

    HighlightManager highlightManager = HighlightManager.getInstance(myProject);
    TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.LIVE_TEMPLATE_ATTRIBUTES);
    highlightManager.addRangeHighlight(editor, highlightingRange.getStartOffset(), highlightingRange.getEndOffset(), attributes, false, myHighlighters);
    for (RangeHighlighter highlighter : myHighlighters) {
      highlighter.setGreedyToRight(true);
      highlighter.setGreedyToLeft(true);
    }
    myEditor.getDocument().addDocumentListener(this);
    showBalloon();
    myEditor.putUserData(INPLACE_CHANGE_SIGNATURE, this);
  }

  @Nullable
  public static InplaceChangeSignature getCurrentRefactoring(Editor editor) {
    return editor.getUserData(INPLACE_CHANGE_SIGNATURE);
  }

  public ChangeInfo getCurrentInfo() {
    return myCurrentInfo;
  }

  public String getInitialSignature() {
    return myInitialSignature;
  }

  @NotNull
  public ChangeInfo getStableChange() {
    return myStableChange;
  }

  public void cancel() {
    TextRange highlightingRange = myDetector.getHighlightingRange(getStableChange());
    Document document = myEditor.getDocument();
    String initialSignature = myInitialSignature;
    detach();
    temporallyRevertChanges(highlightingRange, document, initialSignature, myProject);
  }

  @Override
  public void beforeDocumentChange(DocumentEvent event) {}

  @Override
  public void documentChanged(DocumentEvent event) {
    myDocumentManager.performWhenAllCommitted(() -> {
      if (myDetector == null) {
        return;
      }
      PsiFile file = myDocumentManager.getPsiFile(event.getDocument());
      if (file == null) {
        return;
      }
      PsiElement element = file.findElementAt(event.getOffset());
      if (myDetector.ignoreChanges(element)) return;

      if (element instanceof PsiWhiteSpace) {
        PsiElement method = myStableChange.getMethod();
        if (PsiTreeUtil.skipSiblingsForward(element, PsiWhiteSpace.class) == method) {
          return;
        }
      }

      if (!myDetector.isChangeSignatureAvailableOnElement(element, myStableChange)) {
        detach();
        return;
      }

      if (myCurrentInfo == null) {
        myCurrentInfo = myStableChange;
      }
      String signature = myDetector.extractSignature(myCurrentInfo);
      ChangeInfo changeInfo = myDetector.createNextChangeInfo(signature, myCurrentInfo, myDelegate);
      if (changeInfo == null && myCurrentInfo != null) {
        myStableChange = myCurrentInfo;
      }
      myCurrentInfo = changeInfo;
    });
  }

  protected void showBalloon() {
    JBCheckBox checkBox = new JBCheckBox(RefactoringBundle.message("delegation.panel.delegate.via.overloading.method"));
    checkBox.addActionListener(e -> myDelegate = checkBox.isSelected());
    final BalloonBuilder balloonBuilder = JBPopupFactory.getInstance().createDialogBalloonBuilder(checkBox, null).setSmallVariant(true);
    myBalloon = balloonBuilder.createBalloon();
    myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    final JBPopupFactory popupFactory = JBPopupFactory.getInstance();
    myBalloon.show(new PositionTracker<Balloon>(myEditor.getContentComponent()) {
      @Override
      public RelativePoint recalculateLocation(Balloon object) {
        int offset = myStableChange.getMethod().getTextOffset();
        VisualPosition visualPosition = myEditor.offsetToVisualPosition(offset);
        Point point = myEditor.visualPositionToXY(new VisualPosition(visualPosition.line, visualPosition.column));
        return new RelativePoint(myEditor.getContentComponent(), point);
      }
    }, Balloon.Position.above);
  }

  public void detach() {
    myEditor.getDocument().removeDocumentListener(this);
    HighlightManager highlightManager = HighlightManager.getInstance(myProject);
    for (RangeHighlighter highlighter : myHighlighters) {
      highlightManager.removeSegmentHighlighter(myEditor, highlighter);
    }
    myHighlighters.clear();
    myBalloon.hide();
    FinishMarkAction.finish(myProject, myEditor, myMarkAction);
    myEditor.putUserData(INPLACE_CHANGE_SIGNATURE, null);
  }

  public static void temporallyRevertChanges(final TextRange signatureRange,
                                             final Document document,
                                             final String initialSignature,
                                             Project project) {
    WriteCommandAction.runWriteCommandAction(project, () -> {
      document.replaceString(signatureRange.getStartOffset(), signatureRange.getEndOffset(), initialSignature);
      PsiDocumentManager.getInstance(project).commitDocument(document);
    });
  }
}
