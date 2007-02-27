package com.intellij.usages.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.usageView.UsageViewBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.*;

/**
 * @author cdr
 */
public class UsagePreviewPanel extends JPanel implements Disposable {
  private Editor myEditor;

  public UsagePreviewPanel(final JTree usageTree, final UsageViewImpl usageView) {
    usageTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(final TreeSelectionEvent e) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            PsiElement psiElement = usageView.getSelectedPsiElement();
            if (psiElement != null) {
              resetEditor(psiElement);
            }
          }
        });
      }
    });

    setLayout(new BorderLayout());
  }

  private void resetEditor(@NotNull final PsiElement psiElement) {
    PsiFile psiFile = psiElement.getContainingFile();
    if (psiFile == null) return;

    Document document = PsiDocumentManager.getInstance(psiFile.getProject()).getDocument(psiFile);
    if (myEditor == null || document != myEditor.getDocument()) {
      releaseEditor();
      removeAll();
      myEditor = createEditor(psiFile, document);
      JComponent title = new JLabel(UsageViewBundle.message("preview.title", psiFile.getName()));
      add(title, BorderLayout.NORTH);
      add(myEditor.getComponent(), BorderLayout.CENTER);

      revalidate();
    }

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        highlightElement(psiElement);
      }
    });
  }

  private void highlightElement(final PsiElement psiElement) {
    if (psiElement instanceof PsiFile) return;
    int offset = psiElement.getTextOffset();
    myEditor.getCaretModel().moveToOffset(offset);
    myEditor.getScrollingModel().scrollToCaret(ScrollType.CENTER);

    EditorColorsManager colorManager = EditorColorsManager.getInstance();
    TextAttributes attributes = colorManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);

    myEditor.getMarkupModel().removeAllHighlighters();
    TextRange textRange = psiElement.getTextRange();
    // hack to determine element range to highlight
    if (psiElement instanceof PsiNamedElement) {
      PsiFile psiFile = psiElement.getContainingFile();
      PsiElement nameElement = psiFile.findElementAt(offset);
      if (nameElement != null) {
        textRange = nameElement.getTextRange();
      }
    }
    myEditor.getMarkupModel().addRangeHighlighter(textRange.getStartOffset(), textRange.getEndOffset(), HighlighterLayer.ADDITIONAL_SYNTAX,
                                                attributes, HighlighterTargetArea.EXACT_RANGE);
  }

  private static Editor createEditor(final PsiFile psiFile, Document document) {
    Project project = psiFile.getProject();
    Editor editor = EditorFactory.getInstance().createEditor(document, project, psiFile.getFileType(), true);

    EditorSettings settings = editor.getSettings();
    settings.setLineMarkerAreaShown(false);
    settings.setFoldingOutlineShown(false);
    settings.setAdditionalColumnsCount(0);
    settings.setAdditionalLinesCount(0);
    settings.setVirtualSpace(true);

    return editor;
  }

  public void dispose() {
    releaseEditor();
  }

  private void releaseEditor() {
    if (myEditor != null) {
      EditorFactory.getInstance().releaseEditor(myEditor);
      myEditor = null;
    }
  }
}
