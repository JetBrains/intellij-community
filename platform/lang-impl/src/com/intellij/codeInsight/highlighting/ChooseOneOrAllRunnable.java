/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

public abstract class ChooseOneOrAllRunnable<T extends PsiElement> implements Runnable {
  private final T[] myClasses;
  private final Editor myEditor;
  private JList myList;
  private final String myTitle;

  public ChooseOneOrAllRunnable(final List<T> classes, final Editor editor, final String title, Class<T> type) {
    myClasses = ArrayUtil.toObjectArray(classes, type);
    myEditor = editor;
    myTitle = title;
  }

  protected abstract void selected(T... classes);

  @Override
  public void run() {
    if (myClasses.length == 1) {
      //TODO: cdr this place should produce at least warning
      // selected(myClasses[0]);
      selected((T[])ArrayUtil.toObjectArray(myClasses[0].getClass(), myClasses[0]));
    }
    else if (myClasses.length > 0) {
      PsiElementListCellRenderer<T> renderer = createRenderer();

      Arrays.sort(myClasses, renderer.getComparator());

      if (ApplicationManager.getApplication().isUnitTestMode()) {
        selected(myClasses);
        return;
      }
      Vector<Object> model = new Vector<>(Arrays.asList(myClasses));
      model.insertElementAt(CodeInsightBundle.message("highlight.thrown.exceptions.chooser.all.entry"), 0);

      myList = new JBList(model);
      myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      myList.setCellRenderer(renderer);

      final PopupChooserBuilder builder = new PopupChooserBuilder(myList);
      renderer.installSpeedSearch(builder);

      final Runnable callback = () -> {
        int idx = myList.getSelectedIndex();
        if (idx < 0) return;
        if (idx > 0) {
          selected((T[])ArrayUtil.toObjectArray(myClasses[idx-1].getClass(), myClasses[idx-1]));
        }
        else {
          selected(myClasses);
        }
      };

      ApplicationManager.getApplication().invokeLater(() -> builder.
        setTitle(myTitle).
        setItemChoosenCallback(callback).
        createPopup().
        showInBestPositionFor(myEditor));
    }
  }

  protected abstract PsiElementListCellRenderer<T> createRenderer();
}
