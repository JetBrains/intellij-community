/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.codeInsight.hint.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.hint.ImplementationViewComponent;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.navigation.ImplementationSearcher;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.ui.popup.NotLookupOrSearchCondition;
import com.intellij.ui.popup.PopupUpdateProcessor;

import java.util.*;

public class ShowImplementationsAction extends AnAction {

  public ShowImplementationsAction() {
    setEnabledInModalContext(true);
    setInjectedContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    PsiFile file = LangDataKeys.PSI_FILE.getData(dataContext);

    if (project == null) return;

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiElement element;
    if (editor != null) {
      element = TargetElementUtilBase.findTargetElement(editor, TargetElementUtilBase.getInstance().getAllAccepted());
    }
    else {
      element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
      if (file != null) {
        final FileEditor fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(file.getVirtualFile());
        if (fileEditor instanceof TextEditor) {
          editor = ((TextEditor)fileEditor).getEditor();
        }
      }
    }
    final PsiElement adjustedElement =
      TargetElementUtilBase.getInstance().adjustElement(editor, TargetElementUtilBase.getInstance().getAllAccepted(), element, null);
    if (adjustedElement != null) {
      element = adjustedElement;
    }
    final PsiReference ref;
    if (element == null && editor != null) {
      ref = TargetElementUtilBase.findReference(editor, editor.getCaretModel().getOffset());

      if (ref != null) {
        element = TargetElementUtilBase.getInstance().adjustReference(ref);
      }
    }
    else {
      ref = null;
    }

    String text = "";
    PsiElement[] impls = null;
    if (element != null) {
      //if (element instanceof PsiPackage) return;

      impls = getSelfAndImplementations(editor, element);
      text = SymbolPresentationUtil.getSymbolPresentableText(element);
    }
    else if (ref instanceof PsiPolyVariantReference) {
      final PsiPolyVariantReference polyReference = (PsiPolyVariantReference)ref;
      text = polyReference.getRangeInElement().substring(polyReference.getElement().getText());
      final ResolveResult[] results = polyReference.multiResolve(false);
      final List<PsiElement> implsList = new ArrayList<PsiElement>(results.length);

      for (ResolveResult result : results) {
        final PsiElement resolvedElement = result.getElement();

        if (resolvedElement != null && resolvedElement.isPhysical()) {
          implsList.add(resolvedElement);
        }
      }

      if (!implsList.isEmpty()) {
        implsList.toArray( impls = new PsiElement[implsList.size()] );
      }
    }

    showImplementations(impls, project, text, editor);
  }

  private static void updateElementImplementations(final PsiElement element, final Editor editor, final Project project) {
    PsiElement[] impls = null;
    String text = "";
    if (element != null) {
     // if (element instanceof PsiPackage) return;

      impls = getSelfAndImplementations(editor, element);
      text = SymbolPresentationUtil.getSymbolPresentableText(element);
    }

    showImplementations(impls, project, text, editor);
  }

  private static void showImplementations(final PsiElement[] impls, final Project project, final String text, final Editor editor) {
    if (impls == null || impls.length == 0) return;

    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.quickdefinition");
    if (LookupManager.getInstance(project).getActiveLookup() != null) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.quickdefinition.lookup");
    }

    final ImplementationViewComponent component = new ImplementationViewComponent(impls);
    if (component.hasElementsToShow()) {
      final PopupUpdateProcessor updateProcessor = new PopupUpdateProcessor(new Condition<PsiElement>() {
        public boolean value(final PsiElement element) {
          updateElementImplementations(element, editor, project);
          return false;
        }
      });
      final JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(component, component.getPrefferedFocusableComponent())
        .setRequestFocusCondition(project, NotLookupOrSearchCondition.INSTANCE)
        .setProject(project)
        .addListener(updateProcessor)
        .addUserData(updateProcessor)
        .setDimensionServiceKey(project, "ShowImplementationPopup", false)
        .setResizable(true)
        .setMovable(true)
        .setTitle(CodeInsightBundle.message("implementation.view.title", text))
        .createPopup();
      popup.showInBestPositionFor(DataManager.getInstance().getDataContext());
      component.setHint(popup);
    }

  }

  private static PsiElement[] getSelfAndImplementations(Editor editor, PsiElement element) {
    ImplementationSearcher handler = new ImplementationSearcher() {
      protected PsiElement[] filterElements(Editor editor, PsiElement element, PsiElement[] targetElements, final int offset) {
        Set<PsiElement> unique = new LinkedHashSet<PsiElement>(Arrays.asList(targetElements));
        for (PsiElement elt : targetElements) {
          PsiFile psiFile = elt.getContainingFile();
          final PsiFile originalFile = psiFile.getOriginalFile();
          if (originalFile != null) psiFile = originalFile;
          if (psiFile.getVirtualFile() == null) unique.remove(elt);
        }
        return unique.toArray(new PsiElement[unique.size()]);
      }
    };

    int offset = editor == null ? 0 : editor.getCaretModel().getOffset();
    final PsiElement[] handlerImplementations = handler.searchImplementations(editor, element, offset, true, true);
    if (handlerImplementations.length > 0) return handlerImplementations;

    PsiFile psiFile = element.getContainingFile();
    if (psiFile == null) {
      // Magically, it's null for ant property declarations.
      element = element.getNavigationElement();
      psiFile = element.getContainingFile();
      if (psiFile == null) return PsiElement.EMPTY_ARRAY;
    }
    return psiFile.getVirtualFile() != null ? new PsiElement[] {element} : PsiElement.EMPTY_ARRAY;
  }
}