package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.ide.util.FQNameCellRenderer;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlExtension;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * @author Dmitry Avdeev
*/
public class ImportNSAction implements QuestionAction {
  private final Map<String, String> myNamespaces;
  private final XmlFile myFile;
  @Nullable private final XmlTag myTag;
  private final Editor myEditor;
  private final String myTitle;

  public ImportNSAction(final Map<String, String> namespaces, XmlFile file, @Nullable XmlTag tag, Editor editor, final String title) {

    myNamespaces = namespaces;
    myFile = file;
    myTag = tag;
    myEditor = editor;
    myTitle = title;
  }

  public boolean execute() {
    final Object[] objects = myNamespaces.keySet().toArray();
    Arrays.sort(objects);
    final JList list = new JList(objects);
    list.setCellRenderer(new FQNameCellRenderer());
    list.setSelectedIndex(0);
    final Runnable runnable = new Runnable() {

      public void run() {
        final String namespace = (String)list.getSelectedValue();
        if (namespace != null) {
            final Project project = myFile.getProject();
            new WriteCommandAction.Simple(project, myFile) {

              protected void run() throws Throwable {
                final String prefix = myNamespaces.get(namespace);
                XmlExtension.getExtension(myFile).insertNamespaceDeclaration(myFile,
                                                                             myEditor,
                                                                             Collections.singleton(namespace),
                                                                             prefix,
                                                                             new XmlExtension.Runner<String, IncorrectOperationException>() {
                    public void run(final String s) throws IncorrectOperationException {
                      if (prefix != null && myTag != null && myTag.isValid() && !myTag.getNamespacePrefix().equals(prefix)) {
                          myTag.setName(prefix + ":" + myTag.getLocalName());
                      }
                    }
                  }
                );
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
