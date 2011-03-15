package com.intellij.find.impl;

import com.intellij.find.FindModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.ui.EditorComboBox;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class RegexpFieldController implements FindModel.FindModelObserver {

  public interface EditorDocumentListener {
    void documentWillBeReplaced(RegexpFieldController controller);
    void documentWasReplaced(RegexpFieldController controller);
  }

  public EditorComboBox getTextField() {
    return myTextField;
  }

  private EditorComboBox myTextField;
  private FindModel myFindModel;

  public EditorDocumentListener getListener() {
    return myListener;
  }

  public void setListener(EditorDocumentListener listener) {
    myListener = listener;
  }

  private EditorDocumentListener myListener;

  public RegexpFieldController(EditorComboBox textField, FindModel findModel, @Nullable EditorDocumentListener listener) {
    myTextField = textField;
    myFindModel = findModel;
    myListener = listener;
    findModel.addObserver(this);
    updateRegexpState();
  }

  public void updateRegexpState() {
    boolean regularExpressions = myFindModel.isRegularExpressions();
    Project project = myTextField.getProject();
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(myTextField.getDocument());
    if (psiFile != null && (!regularExpressions == (psiFile.getFileType() == PlainTextFileType.INSTANCE))) {
      return;
    }
    @NonNls final String s = regularExpressions ? "*.regexp" : "*.txt";
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(s);

    if (regularExpressions && fileType == FileTypes.UNKNOWN) {
      fileType = FileTypeManager.getInstance().getFileTypeByFileName("*.txt"); // RegExp plugin is not installed
    }

    final PsiFile file = PsiFileFactory.getInstance(project).createFileFromText(s, fileType, myTextField.getText(), -1, true);

    Component editorComponent = myTextField.getEditor().getEditorComponent();
    if (editorComponent instanceof  EditorTextField) {
      if (myListener != null) {
        myListener.documentWillBeReplaced(this);
      }
      Document document = PsiDocumentManager.getInstance(project).getDocument(file);
      myTextField.setDocument(document);
      ((EditorTextField)editorComponent).setNewDocumentAndFileType(fileType, document);
      if (myListener != null) {
        myListener.documentWasReplaced(this);
      }
    }

  }

  @Override
  public void findModelChanged(FindModel findModel) {
    updateRegexpState();
  }
}
