
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.ide.util.FQNameCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiImportStaticReferenceElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.util.IncorrectOperationException;

import javax.swing.*;

public class AddImportAction implements QuestionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.actions.AddImportAction");

  private Project myProject;
  private PsiJavaCodeReferenceElement myReference;
  private PsiClass[] myTargetClasses;
  private Editor myEditor;

  public AddImportAction(Project project, PsiJavaCodeReferenceElement ref, Editor editor, PsiClass... targetClasses) {
    myProject = project;
    myReference = ref;
    myTargetClasses = targetClasses;
    myEditor = editor;
  }

  public boolean execute() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    if (!myReference.isValid()){
      return false;
    }

    if (!myReference.isWritable()){
      return false;
    }
    
    for (PsiClass myTargetClass : myTargetClasses) {
      if (!myTargetClass.isValid()) {
        return  false;
      }
    }

    if (myTargetClasses.length == 1){
      addImport(myReference, myTargetClasses[0]);
    }
    else{
      chooseClassAndImport();
    }
    return true;
  }

  private void chooseClassAndImport() {
    final JList list = new JList(myTargetClasses);
    list.setCellRenderer(new FQNameCellRenderer());
    Runnable runnable = new Runnable() {
      public void run() {
        int index = list.getSelectedIndex();
        if (index < 0) return;
        PsiDocumentManager.getInstance(myProject).commitAllDocuments();
        addImport(myReference, myTargetClasses[index]);
      }
    };

    new PopupChooserBuilder(list).
      setTitle(QuickFixBundle.message("class.to.import.chooser.title")).
      setItemChoosenCallback(runnable).
      createPopup().
      showInBestPositionFor(myEditor);
  }

  private void addImport(final PsiJavaCodeReferenceElement ref, final PsiClass targetClass) {
    StatisticsManager.getInstance().incMemberUseCount(null, targetClass);
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            _addImport(ref, targetClass);
          }
        });
      }
    }, QuickFixBundle.message("add.import"), null);
  }

  private void _addImport(PsiJavaCodeReferenceElement ref, PsiClass targetClass) {
    if (ref.isValid() && targetClass.isValid()){
      int caretOffset = myEditor.getCaretModel().getOffset();
      RangeMarker caretMarker = myEditor.getDocument().createRangeMarker(caretOffset, caretOffset);
      int colByOffset = myEditor.offsetToLogicalPosition(caretOffset).column;
      int col = myEditor.getCaretModel().getLogicalPosition().column;
      int virtualSpace = col != colByOffset ? col - colByOffset : 0;
      int line = myEditor.getCaretModel().getLogicalPosition().line;
      LogicalPosition pos = new LogicalPosition(line, 0);
      myEditor.getCaretModel().moveToLogicalPosition(pos);

      try{
        if (ref instanceof PsiImportStaticReferenceElement) {
          ((PsiImportStaticReferenceElement)ref).bindToTargetClass(targetClass);
        }
        else {
          ref.bindToElement(targetClass);
        }
      }
      catch(IncorrectOperationException e){
        LOG.error(e);
      }

      line = myEditor.getCaretModel().getLogicalPosition().line;
      LogicalPosition pos1 = new LogicalPosition(line, col);
      myEditor.getCaretModel().moveToLogicalPosition(pos1);
      if (caretMarker.isValid()){
        LogicalPosition pos2 = myEditor.offsetToLogicalPosition(caretMarker.getStartOffset());
        int newCol = pos2.column + virtualSpace;
        myEditor.getCaretModel().moveToLogicalPosition(new LogicalPosition(pos2.line, newCol));
        myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }
    }

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (myProject.isOpen()) {
          DaemonCodeAnalyzer daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(myProject);
          if (daemonCodeAnalyzer != null) {
            daemonCodeAnalyzer.updateVisibleHighlighters(myEditor);
          }
        }
      }
    });
  }
}
