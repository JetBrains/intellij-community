package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;

/**
 * @author Dmitry Avdeev
*/
public class ImportNSAction implements QuestionAction {
  private final Collection<String> myList;
  private final String myPrefix;
  private final PsiFile myFile;
  private final Editor myEditor;
  private final String myTitle;

  public ImportNSAction(final Collection<String> list, final String prefix, PsiFile file, Editor editor, final String title) {

    myList = list;
    myPrefix = prefix;
    myFile = file;
    myEditor = editor;
    myTitle = title;
  }

  public boolean execute() {
    final Object[] objects = myList.toArray();
    Arrays.sort(objects);
    final JList list = new JList(objects);
    list.setSelectedIndex(0);
    final Runnable runnable = new Runnable() {

      public void run() {
        final String value = (String)list.getSelectedValue();
        if (value != null) {
            final Project project = myFile.getProject();
            new WriteCommandAction.Simple(project, myFile) {

              protected void run() throws Throwable {
                CreateNSDeclarationIntentionFix.insertTaglibDeclaration((XmlFile)myFile, value, project, myPrefix);

              }
            }.executeSilently();
        }
      }
    };
    if (list.getModel().getSize() == 1) {
      runnable.run();
    } else {
      new PopupChooserBuilder(list).
        setTitle(myTitle).
        setItemChoosenCallback(runnable).
        createPopup().
        showInBestPositionFor(myEditor);
    }

    return true;
  }
}
