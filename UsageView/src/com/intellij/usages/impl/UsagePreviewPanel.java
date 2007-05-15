package com.intellij.usages.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.*;
import java.util.List;

/**
 * @author cdr
 */
public class UsagePreviewPanel extends JPanel implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.usages.impl.UsagePreviewPanel");
  private Editor myEditor;
  private final UsageViewImpl myUsageView;
  private final Project myProject;
  private String myTitle;

  public UsagePreviewPanel(final JTree usageTree, final UsageViewImpl usageView, final Project project) {
    myUsageView = usageView;
    myProject = project;
    usageTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(final TreeSelectionEvent e) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            List<UsageInfo> infos = usageView.getSelectedUsageInfos();
            if (infos != null) {
              resetEditor(infos);
            }
          }
        });
      }
    });

    setLayout(new BorderLayout());
  }

  private void resetEditor(@NotNull final List<UsageInfo> infos) {
    PsiElement psiElement = infos.get(0).getElement();
    if (psiElement == null) return;
    PsiFile psiFile = psiElement.getContainingFile();
    if (psiFile == null) return;

    Document document = PsiDocumentManager.getInstance(psiFile.getProject()).getDocument(psiFile);
    if (document == null) return;
    String title = UsageViewBundle.message("preview.title", psiFile.getName());
    if (myEditor == null || document != myEditor.getDocument() || !Comparing.strEqual(title, myTitle)) {
      releaseEditor();
      removeAll();
      myEditor = createEditor(psiFile, document);
      myTitle = title;
      JComponent titleComp = new JLabel(myTitle);
      add(titleComp, BorderLayout.NORTH);
      add(myEditor.getComponent(), BorderLayout.CENTER);

      revalidate();
    }

    final Editor editor = myEditor;
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        highlight(infos, editor);
      }
    });
  }

  private void highlight(final List<UsageInfo> infos, final Editor editor) {
    if (editor != myEditor) return; //already disposed
    myEditor.getMarkupModel().removeAllHighlighters();
    for (int i = infos.size()-1; i>=0; i--) { // finish with the first usage so that caret end up there
      UsageInfo info = infos.get(i);
      PsiElement psiElement = info.getElement();
      if (psiElement == null || !psiElement.isValid()) continue;
      int offsetInFile = psiElement.getTextOffset();

      EditorColorsManager colorManager = EditorColorsManager.getInstance();
      TextAttributes attributes = colorManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);

      TextRange elementRange = psiElement.getTextRange();
      TextRange infoRange = info.getRange();
      TextRange textRange = elementRange.contains(infoRange) ? elementRange.cutOut(infoRange) : elementRange;
      // hack to determine element range to highlight
      if (psiElement instanceof PsiNamedElement && !(psiElement instanceof PsiFile)) {
        PsiFile psiFile = psiElement.getContainingFile();
        PsiElement nameElement = psiFile.findElementAt(offsetInFile);
        if (nameElement != null) {
          textRange = nameElement.getTextRange();
        }

        // highlight injected element in host document textrange
        PsiElement hostElement = psiFile.getContext();
        if (hostElement != null) {
          textRange = textRange.shiftRight(hostElement.getTextRange().getStartOffset());
        }
      }
      myEditor.getMarkupModel().addRangeHighlighter(textRange.getStartOffset(), textRange.getEndOffset(),
                                                    HighlighterLayer.ADDITIONAL_SYNTAX, attributes, HighlighterTargetArea.EXACT_RANGE);
      myEditor.getCaretModel().moveToOffset(textRange.getEndOffset());
    }
    myEditor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
  }

  private static final Key<UsagePreviewPanel> PREVIEW_EDITOR_FLAG = Key.create("PREVIEW_EDITOR_FLAG");
  private Editor createEditor(final PsiFile psiFile, Document document) {
    Project project = psiFile.getProject();

    Editor editor = EditorFactory.getInstance().createEditor(document, project, psiFile.getFileType(), true);

    EditorSettings settings = editor.getSettings();
    settings.setLineMarkerAreaShown(false);
    settings.setFoldingOutlineShown(false);
    settings.setAdditionalColumnsCount(0);
    settings.setAdditionalLinesCount(0);
    settings.setVirtualSpace(true);

    editor.putUserData(PREVIEW_EDITOR_FLAG, this);
    return editor;
  }

  public void dispose() {
    releaseEditor();
    for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
      if (editor.getProject() == myProject && editor.getUserData(PREVIEW_EDITOR_FLAG) == this) {
        LOG.error("Editor was not released:"+editor);
      }
    }
  }


  private void releaseEditor() {
    if (myEditor != null) {
      EditorFactory.getInstance().releaseEditor(myEditor);
      myEditor = null;
    }
  }

  public void update() {
    List<UsageInfo> infos = myUsageView.getSelectedUsageInfos();
    if (infos == null) {
      removeAll();
      JComponent titleComp = new JLabel(UsageViewBundle.message("select.the.usage.to.preview"));
      add(titleComp, BorderLayout.CENTER);
      revalidate();
    }
    else {
      resetEditor(infos);
    }
  }
}
