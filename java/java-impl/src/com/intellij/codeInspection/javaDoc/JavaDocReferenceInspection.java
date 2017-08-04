/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInspection.javaDoc;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFix;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptorBase;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.FQNameCellRenderer;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.proximity.PsiProximityComparator;
import com.intellij.ui.components.JBList;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class JavaDocReferenceInspection extends JavaDocReferenceInspectionBase {
  @Override
  protected LocalQuickFix createAddQualifierFix(PsiJavaCodeReferenceElement reference) {
    List<PsiClass> classesToImport = new ImportClassFix(reference).getClassesToImport();
    return classesToImport.isEmpty() ? null : new AddQualifierFix(classesToImport);
  }
  @Override
  protected RenameReferenceQuickFix createRenameReferenceQuickFix(Set<String> unboundParams) {
    return new RenameReferenceQuickFix(unboundParams);
  }

  private static class RenameReferenceQuickFix implements LocalQuickFix {
    private final Set<String> myUnboundParams;

    public RenameReferenceQuickFix(Set<String> unboundParams) {
      myUnboundParams = unboundParams;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return "Change to ...";
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
      final AsyncResult<DataContext> asyncResult = DataManager.getInstance().getDataContextFromFocus();
      asyncResult.doWhenDone((Consumer<DataContext>)dataContext -> {
        final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
        assert editor != null;
        final TextRange textRange = ((ProblemDescriptorBase)descriptor).getTextRange();
        editor.getSelectionModel().setSelection(textRange.getStartOffset(), textRange.getEndOffset());

        final String word = editor.getSelectionModel().getSelectedText();

        if (word == null || StringUtil.isEmptyOrSpaces(word)) {
          return;
        }
        final List<LookupElement> items = new ArrayList<>();
        for (String variant : myUnboundParams) {
          items.add(LookupElementBuilder.create(variant));
        }
        LookupManager.getInstance(project).showLookup(editor, items.toArray(new LookupElement[items.size()]));
      });
    }
  }

  private static class AddQualifierFix implements LocalQuickFix{
    private final List<PsiClass> originalClasses;

    public AddQualifierFix(final List<PsiClass> originalClasses) {
      this.originalClasses = originalClasses;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return QuickFixBundle.message("add.qualifier");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
      PsiJavaCodeReferenceElement element = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiJavaCodeReferenceElement.class);
      if (element != null) {
        Collections.sort(originalClasses, new PsiProximityComparator(element.getElement()));
        JList<PsiClass> list = new JBList<>(originalClasses.toArray(new PsiClass[originalClasses.size()]));
        list.setCellRenderer(new FQNameCellRenderer());
        final Runnable runnable = () -> {
          if (!element.isValid()) return;
          final int index = list.getSelectedIndex();
          if (index < 0) return;
          new WriteCommandAction(project, element.getContainingFile()){
            @Override
            protected void run(@NotNull final Result result) throws Throwable {
              final PsiClass psiClass = originalClasses.get(index);
              if (psiClass.isValid()) {
                PsiDocumentManager.getInstance(project).commitAllDocuments();
                element.bindToElement(psiClass);
              }
            }
          }.execute();
        };
        final AsyncResult<DataContext> asyncResult = DataManager.getInstance().getDataContextFromFocus();
        asyncResult.doWhenDone((Consumer<DataContext>)dataContext -> new PopupChooserBuilder(list).
          setTitle(QuickFixBundle.message("add.qualifier.original.class.chooser.title")).
          setItemChoosenCallback(runnable).
          createPopup().
          showInBestPositionFor(dataContext));
      }
    }
  }
}
