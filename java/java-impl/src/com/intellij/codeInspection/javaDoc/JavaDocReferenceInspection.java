// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.proximity.PsiProximityComparator;
import org.jetbrains.annotations.NotNull;

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
      DataManager.getInstance().getDataContextFromFocusAsync()
                 .onSuccess(dataContext -> {
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
                   LookupManager.getInstance(project).showLookup(editor, items.toArray(LookupElement.EMPTY_ARRAY));
                 });
    }
  }

  private static class AddQualifierFix implements LocalQuickFix {
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
        DataManager.getInstance()
                   .getDataContextFromFocusAsync()
                   .onSuccess(dataContext ->
          JBPopupFactory.getInstance()
            .createPopupChooserBuilder(originalClasses)
            .setTitle(QuickFixBundle.message("add.qualifier.original.class.chooser.title"))
            .setItemChosenCallback((psiClass) -> {
              if (!element.isValid()) return;
              WriteCommandAction.writeCommandAction(project, element.getContainingFile()).run(() -> {
                if (psiClass.isValid()) {
                  PsiDocumentManager.getInstance(project).commitAllDocuments();
                  element.bindToElement(psiClass);
                }
              });
            })
            .setRenderer(new FQNameCellRenderer())
            .createPopup()
            .showInBestPositionFor(dataContext));
      }
    }
  }
}
