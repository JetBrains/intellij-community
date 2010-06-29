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
package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.ui.components.JBList;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

public abstract class ChooseClassAndDoHighlightRunnable implements Runnable {
  private final PsiClass[] myClasses;
  private final Editor myEditor;
  private JList myList;
  private final String myTitle;

  public ChooseClassAndDoHighlightRunnable(PsiClassType[] classTypes, Editor editor, String title) {
    List<PsiClass> classes = resolveClasses(classTypes);
    myClasses = classes.toArray(new PsiClass[classes.size()]);
    myEditor = editor;
    myTitle = title;
  }

  protected ChooseClassAndDoHighlightRunnable(final List<PsiClass> classes, final Editor editor, final String title) {
    myClasses = classes.toArray(new PsiClass[classes.size()]);
    myEditor = editor;
    myTitle = title;
  }

  public static List<PsiClass> resolveClasses(final PsiClassType[] classTypes) {
    List<PsiClass> classes = new ArrayList<PsiClass>();
    for (PsiClassType classType : classTypes) {
      PsiClass aClass = classType.resolve();
      if (aClass != null) classes.add(aClass);
    }
    return classes;
  }

  protected abstract void selected(PsiClass... classes);

  public void run() {
    if (myClasses.length == 1) {
      selected(myClasses[0]);
    }
    else if (myClasses.length > 0) {
      PsiClassListCellRenderer renderer = new PsiClassListCellRenderer();

      Arrays.sort(myClasses, renderer.getComparator());

      if (ApplicationManager.getApplication().isUnitTestMode()) {
        selected(myClasses);
        return;
      }
      Vector<Object> model = new Vector<Object>(Arrays.asList(myClasses));
      model.insertElementAt(CodeInsightBundle.message("highlight.thrown.exceptions.chooser.all.entry"), 0);

      myList = new JBList(model);
      myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      myList.setCellRenderer(renderer);

      final PopupChooserBuilder builder = new PopupChooserBuilder(myList);
      renderer.installSpeedSearch(builder);

      final Runnable callback = new Runnable() {
        public void run() {
          int idx = myList.getSelectedIndex();
          if (idx < 0) return;
          if (idx > 0) {
            selected(myClasses[idx-1]);
          }
          else {
            selected(myClasses);
          }
        }
      };

      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          builder.
            setTitle(myTitle).
            setItemChoosenCallback(callback).
            createPopup().
            showInBestPositionFor(myEditor);
        }
      });
    }
  }
}
