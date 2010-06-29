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

/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Sep 4, 2002
 * Time: 6:26:27 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.ui.components.JBList;
import com.intellij.util.IncorrectOperationException;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;

public class ImplementAbstractMethodHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.ImplementAbstractMethodHandler");

  private final Project myProject;
  private final Editor myEditor;
  private final PsiMethod myMethod;
  private JList myList;

  public ImplementAbstractMethodHandler(Project project, Editor editor, PsiMethod method) {
    myProject = project;
    myEditor = editor;
    myMethod = method;
  }

  public void invoke() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    final PsiClass[][] result = new PsiClass[1][];
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        final PsiClass psiClass = myMethod.getContainingClass();
        if (!psiClass.isValid()) return;
        result[0] = getClassImplementations(psiClass);
      }
    }, CodeInsightBundle.message("intention.implement.abstract.method.searching.for.descendants.progress"), true, myProject);

    if (result[0] == null) return;

    if (result[0].length == 0) {
      Messages.showMessageDialog(myProject,
                                 CodeInsightBundle.message("intention.implement.abstract.method.error.no.classes.message"),
                                 CodeInsightBundle.message("intention.implement.abstract.method.error.no.classes.title"),
                                 Messages.getInformationIcon());
      return;
    }

    if (result[0].length == 1) {
      implementInClass(result[0][0]);
      return;
    }

    PsiClassListCellRenderer renderer = new PsiClassListCellRenderer();
    Arrays.sort(result[0], renderer.getComparator());

    myList = new JBList(result[0]);
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.setCellRenderer(renderer);

    final Runnable runnable = new Runnable(){
      public void run() {
        int index = myList.getSelectedIndex();
        if (index < 0) return;
        PsiElement element = (PsiElement)myList.getSelectedValue();
        implementInClass((PsiClass)element);
      }
    };

    final PopupChooserBuilder builder = new PopupChooserBuilder(myList);
    renderer.installSpeedSearch(builder);

    builder.
      setTitle(CodeInsightBundle.message("intention.implement.abstract.method.class.chooser.title")).
      setItemChoosenCallback(runnable).
      createPopup().
      showInBestPositionFor(myEditor);
  }

  private void implementInClass(final PsiClass psiClass) {
    if (!psiClass.isValid()) return;
    if (!FileDocumentManager.getInstance().requestWriting(PsiDocumentManager.getInstance(myProject).getDocument(psiClass.getContainingFile()), myProject)) {
      MessagesEx.fileIsReadOnly(myProject, psiClass.getContainingFile().getVirtualFile()).showNow();
      return;
    }

    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              CodeInsightUtilBase.prepareFileForWrite(psiClass.getContainingFile());
              OverrideImplementUtil.overrideOrImplement(psiClass, myMethod);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        });
      }
    }, CodeInsightBundle.message("intention.implement.abstract.method.command.name"), null);
  }

  private PsiClass[] getClassImplementations(final PsiClass psiClass) {
    ArrayList<PsiClass> list = new ArrayList<PsiClass>();
    for (PsiClass inheritor : ClassInheritorsSearch.search(psiClass, psiClass.getUseScope(), true)) {
      if (!inheritor.isInterface()) {
        PsiMethod method = inheritor.findMethodBySignature(myMethod, true);
        if (method == null || !method.getContainingClass().equals(psiClass)) continue;
        list.add(inheritor);
      }
    }

    return list.toArray(new PsiClass[list.size()]);
  }
}
