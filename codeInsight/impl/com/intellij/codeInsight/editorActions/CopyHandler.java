package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.List;

public class CopyHandler extends EditorActionHandler {
  private EditorActionHandler myOriginalAction;

  public CopyHandler(final EditorActionHandler originalHandler) {
    myOriginalAction = originalHandler;
  }

  public void execute(final Editor editor, final DataContext dataContext) {
    final Project project = (Project)DataManager.getInstance().getDataContext(editor.getComponent()).getData(DataConstants.PROJECT);
    if (project == null){
      if (myOriginalAction != null){
        myOriginalAction.execute(editor, dataContext);
      }
      return;
    }
    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());

    final CodeInsightSettings settings = CodeInsightSettings.getInstance();

    if (file == null || settings.ADD_IMPORTS_ON_PASTE == CodeInsightSettings.NO){
      if (myOriginalAction != null){
        myOriginalAction.execute(editor, dataContext);
      }
      return;
    }

    final SelectionModel selectionModel = editor.getSelectionModel();
    if(!selectionModel.hasSelection() && !selectionModel.hasBlockSelection()) {
      selectionModel.selectLineAtCaret();
      if (!selectionModel.hasSelection()) return;
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final int[] startOffsets = selectionModel.getBlockSelectionStarts();
    final int[] endOffsets = selectionModel.getBlockSelectionEnds();

    final TextBlockTransferable.ReferenceData[] referenceData = collectReferencesInBlock(file, startOffsets, endOffsets);
    final TextBlockTransferable.FoldingData[] foldingData = collectFoldingsInBlock(editor, startOffsets, endOffsets);

    String rawText = TextBlockTransferable.convertLineSeparators(selectionModel.getSelectedText(), "\n", referenceData, foldingData);
    String escapedText = unescapeIfInsideLiteral(file, startOffsets, endOffsets, rawText);

    final Transferable transferable = new TextBlockTransferable(escapedText != null ? escapedText : rawText,
                                                                referenceData, foldingData,
                                                                escapedText != null ? new RawText(rawText) : null);
    CopyPasteManager.getInstance().setContents(transferable);
  }

  @Nullable
  private static String unescapeIfInsideLiteral(final PsiFile file, final int[] startOffsets,
                                         final int[] endOffsets, String text) {
    boolean isLiteral = true;
    for (int i = 0; i < startOffsets.length && isLiteral; i++) {
      final int startOffset = startOffsets[i];
      final PsiElement elementAtCaret = file.findElementAt(startOffset);
      if (!(elementAtCaret instanceof PsiJavaToken &&
            (((PsiJavaToken) elementAtCaret)).getTokenType() == JavaTokenType.STRING_LITERAL &&
            startOffset > elementAtCaret.getTextRange().getStartOffset() &&
            endOffsets[i] < elementAtCaret.getTextRange().getEndOffset())) {
        isLiteral = false;
      }
    }

    return isLiteral ? StringUtil.unescapeStringCharacters(text) : null;
  }

  private static TextBlockTransferable.ReferenceData[] collectReferencesInBlock(PsiFile file,
                                                                                final int[] startOffsets,
                                                                                final int[] endOffsets) {
    if (file instanceof PsiCompiledElement) {
      file = (PsiFile) ((PsiCompiledElement) file).getMirror();
    }

    final ArrayList<TextBlockTransferable.ReferenceData> array = new ArrayList<TextBlockTransferable.ReferenceData>();
    for (int j = 0; j < startOffsets.length; j++) {
      final int startOffset = startOffsets[j];
      final int endOffset = endOffsets[j];
      final List<PsiElement> elements = CodeInsightUtil.getElementsInRange(file, startOffset, endOffset);
      for (final PsiElement element : elements) {
        if (element instanceof PsiJavaCodeReferenceElement) {
          if (!((PsiJavaCodeReferenceElement)element).isQualified()) {
            final JavaResolveResult resolveResult = ((PsiJavaCodeReferenceElement)element).advancedResolve(false);
            final PsiElement refElement = resolveResult.getElement();
            if (refElement != null && refElement.getContainingFile() != file) {

              if (refElement instanceof PsiClass) {
                if (refElement.getContainingFile() != element.getContainingFile()) {
                  final String qName = ((PsiClass)refElement).getQualifiedName();
                  if (qName != null) {
                    addReferenceData(element, array, startOffset, qName, null);
                  }
                }
              }
              else if (resolveResult.getCurrentFileResolveScope() instanceof PsiImportStaticStatement) {
                final String classQName = ((PsiMember)refElement).getContainingClass().getQualifiedName();
                final String name = ((PsiNamedElement)refElement).getName();
                if (classQName != null && name != null) {
                  addReferenceData(element, array, startOffset, classQName, name);
                }
              }
            }
          }
        }
      }
    }
    return array.toArray(new TextBlockTransferable.ReferenceData[array.size()]);
  }

  private static void addReferenceData(final PsiElement element,
                                final ArrayList<TextBlockTransferable.ReferenceData> array,
                                final int startOffset,
                                final String qClassName, final String staticMemberName) {
    final TextRange range = element.getTextRange();
    array.add(
        new TextBlockTransferable.ReferenceData(
            range.getStartOffset() - startOffset,
            range.getEndOffset() - startOffset,
            qClassName, staticMemberName));
  }

  private static TextBlockTransferable.FoldingData[] collectFoldingsInBlock(final Editor editor,
                                                                            final int[] startOffsets,
                                                                            final int[] endOffsets){
    // might be slow
    //CodeFoldingManager.getInstance(file.getManager().getProject()).updateFoldRegions(editor);

    final ArrayList<TextBlockTransferable.FoldingData> list = new ArrayList<TextBlockTransferable.FoldingData>();
    final FoldRegion[] regions = editor.getFoldingModel().getAllFoldRegions();
    for (final FoldRegion region : regions) {
      if (!region.isValid()) continue;
      for (int j = 0; j < startOffsets.length; j++) {
        if (startOffsets[j] <= region.getStartOffset() && region.getEndOffset() <= endOffsets[j]) {
          list.add(
            new TextBlockTransferable.FoldingData(
              region.getStartOffset() - startOffsets[j],
              region.getEndOffset() - startOffsets[j],
              region.isExpanded()
            )
          );
        }
      }
    }

    return list.toArray(new TextBlockTransferable.FoldingData[list.size()]);
  }
}
