package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandler;
import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.Processor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * @author yole
 */
public class IdentifierHighlighterPass extends TextEditorHighlightingPass {
  private final PsiFile myFile;
  private final Editor myEditor;
  private Collection<TextRange> myReadAccessRanges = new ArrayList<TextRange>();
  private Collection<TextRange> myWriteAccessRanges = new ArrayList<TextRange>();
  private int myCaretOffset;
  private PsiElement myTarget;

  private static final HighlightInfoType ourReadHighlightInfoType = new HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES);
  private static final HighlightInfoType ourWriteHighlightInfoType = new HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, EditorColors.WRITE_IDENTIFIER_UNDER_CARET_ATTRIBUTES);

  protected IdentifierHighlighterPass(final Project project, final PsiFile file, final Editor editor) {
    super(project, editor.getDocument());
    myFile = file;
    myEditor = editor;
    myCaretOffset = myEditor.getCaretModel().getOffset();
  }

  public void doCollectInformation(final ProgressIndicator progress) {
    if (!CodeInsightSettings.getInstance().HIGHLIGHT_IDENTIFIER_UNDER_CARET) {
      return;
    }
    myTarget = TargetElementUtilBase.getInstance().findTargetElement(myEditor,
                                                                     TargetElementUtilBase.ELEMENT_NAME_ACCEPTED |
                                                                     TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED,
                                                                     myCaretOffset);
    if (myTarget != null) {
      final ReadWriteAccessDetector detector = ReadWriteAccessDetector.findDetector(myTarget);
      ReferencesSearch.search(myTarget, new LocalSearchScope(myFile)).forEach(new Processor<PsiReference>() {
        public boolean process(final PsiReference psiReference) {
          final TextRange textRange = HighlightUsagesHandler.getRangeToHighlight(psiReference);
          if (detector == null || detector.getReferenceAccess(myTarget, psiReference) == ReadWriteAccessDetector.Access.Read) {
            myReadAccessRanges.add(textRange);
          }
          else {
            myWriteAccessRanges.add(textRange);
          }
          return true;
        }
      });
      PsiElement identifier = HighlightUsagesHandler.getNameIdentifier(myTarget);
      if (identifier != null && PsiUtilBase.isUnderPsiRoot(myFile, identifier)) {
        if (detector != null && detector.isDeclarationWriteAccess(myTarget)) {
          myWriteAccessRanges.add(identifier.getTextRange());
        }
        else {
          myReadAccessRanges.add(identifier.getTextRange());
        }
      }
    }
  }

  public void doApplyInformationToEditor() {
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, 0, myFile.getTextLength(), getHighlights(), getId());
  }

  @Override
  public Collection<HighlightInfo> getHighlights() {
    if (myTarget == null) {
      return Collections.emptyList();
    }
    Collection<HighlightInfo> result = new ArrayList<HighlightInfo>();
    for (TextRange range: myReadAccessRanges) {
      result.add(HighlightInfo.createHighlightInfo(ourReadHighlightInfoType, range, null));
    }
    for (TextRange range: myWriteAccessRanges) {
      result.add(HighlightInfo.createHighlightInfo(ourWriteHighlightInfoType, range, null));
    }
    return result;
  }
}
