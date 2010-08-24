/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.ui;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;

import javax.swing.*;

/**
 * ComboBox with Editor and Strings as item
 * @author dsl
 */
public class StringComboboxEditor extends EditorComboBoxEditor {
  public static final Key<JComboBox> COMBO_BOX_KEY = Key.create("COMBO_BOX_KEY");

  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.StringComboboxEditor");
  private final Project myProject;

  public StringComboboxEditor(final Project project, final FileType fileType, ComboBox comboBox) {
    super(project, fileType);
    myProject = project;
    final PsiFile file = PsiFileFactory.getInstance(project).createFileFromText("a.dummy", StdFileTypes.PLAIN_TEXT, "", 0, true);
    final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    assert document != null;
    document.putUserData(COMBO_BOX_KEY, comboBox);
    super.setItem(document);
  }

  public Object getItem() {
    final String text = ((Document) super.getItem()).getText();
    LOG.assertTrue(text != null);
    return text;
  }

  public void setItem(Object anObject) {
    if (anObject == null) anObject = "";
    if (anObject.equals(getItem())) return;
    final String s = (String)anObject;
    new WriteCommandAction(myProject) {
      protected void run(Result result) throws Throwable {
        getDocument().setText(s);
      }
    }.execute();

    final Editor editor = getEditor();
    if (editor != null) editor.getCaretModel().moveToOffset(s.length());
  }
}
