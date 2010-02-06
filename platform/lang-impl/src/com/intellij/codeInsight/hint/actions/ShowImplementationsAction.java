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
package com.intellij.codeInsight.hint.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.documentation.DocumentationManager;
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.popup.NotLookupOrSearchCondition;
import com.intellij.ui.popup.PopupUpdateProcessor;
import org.jetbrains.annotations.NonNls;

import java.util.*;

public class ShowImplementationsAction extends AnAction {
  @NonNls public static final String CODEASSISTS_QUICKDEFINITION_LOOKUP_FEATURE = "codeassists.quickdefinition.lookup";
  @NonNls public static final String CODEASSISTS_QUICKDEFINITION_FEATURE = "codeassists.quickdefinition";

  public ShowImplementationsAction() {
    setEnabledInModalContext(true);
    setInjectedContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    performForContext(e.getDataContext());
  }

  public void performForContext(DataContext dataContext) {
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    PsiFile file = LangDataKeys.PSI_FILE.getData(dataContext);

    if (project == null) return;

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    boolean isInvokedFromEditor = editor != null;
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

    String text = "";
    PsiElement[] impls = new PsiElement[0];
    PsiReference ref = null;

    final PsiElement adjustedElement =
      TargetElementUtilBase.getInstance().adjustElement(editor, TargetElementUtilBase.getInstance().getAllAccepted(), element, null);
    if (adjustedElement != null) {
      element = adjustedElement;
    } else if (file != null && editor != null) {
      element = DocumentationManager.getInstance(project).getElementFromLookup(editor, file);
    }

    if (editor != null) {
      ref = TargetElementUtilBase.findReference(editor, editor.getCaretModel().getOffset());
      if (element == null && ref != null) {
        element = TargetElementUtilBase.getInstance().adjustReference(ref);
      }
    }

    if (element != null) {
      //if (element instanceof PsiPackage) return;

      impls = getSelfAndImplementations(editor, element);
      text = SymbolPresentationUtil.getSymbolPresentableText(element);
    }

    if (impls.length == 0 && ref instanceof PsiPolyVariantReference) {
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
    

    showImplementations(impls, project, text, editor, file, isInvokedFromEditor);
  }

  protected void updateElementImplementations(final PsiElement element, final Editor editor, final Project project, final PsiFile file) {
    PsiElement[] impls = null;
    String text = "";
    if (element != null) {
     // if (element instanceof PsiPackage) return;

      impls = getSelfAndImplementations(editor, element);
      text = SymbolPresentationUtil.getSymbolPresentableText(element);
    }

    showImplementations(impls, project, text, editor, file, false);
  }

  protected void showImplementations(final PsiElement[] impls, final Project project, final String text, final Editor editor, final PsiFile file,
                                     boolean invokedFromEditor) {
    if (impls == null || impls.length == 0) return;

    FeatureUsageTracker.getInstance().triggerFeatureUsed(CODEASSISTS_QUICKDEFINITION_FEATURE);
    if (LookupManager.getInstance(project).getActiveLookup() != null) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(CODEASSISTS_QUICKDEFINITION_LOOKUP_FEATURE);
    }

    int index = 0;
    if (invokedFromEditor && file != null && impls.length > 1) {
      final VirtualFile virtualFile = file.getVirtualFile();
      final PsiFile containingFile = impls[0].getContainingFile();
      if (virtualFile != null && containingFile != null && virtualFile.equals(containingFile.getVirtualFile())) {
        index = 1;
      }
    }
    final ImplementationViewComponent component = new ImplementationViewComponent(impls, index);
    if (component.hasElementsToShow()) {
      final PopupUpdateProcessor updateProcessor = new PopupUpdateProcessor(project) {
        public void updatePopup(Object lookupItemObject) {
          final PsiElement element = lookupItemObject instanceof PsiElement ? (PsiElement)lookupItemObject : DocumentationManager.getInstance(project).getElementFromLookup(editor, file);
          updateElementImplementations(element, editor, project, file);
        }
      };
      final String title = CodeInsightBundle.message("implementation.view.title", text);
      final JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(component, component.getPrefferedFocusableComponent())
        .setRequestFocusCondition(project, NotLookupOrSearchCondition.INSTANCE)
        .setProject(project)
        .addListener(updateProcessor)
        .addUserData(updateProcessor)
        .setDimensionServiceKey(project, "ShowImplementationPopup", false)
        .setResizable(true)
        .setMovable(true)
        .setTitle(title)
        .createPopup();
      popup.showInBestPositionFor(DataManager.getInstance().getDataContext());
      component.setHint(popup, title);
    }

  }

  private static PsiElement[] getSelfAndImplementations(Editor editor, PsiElement element) {
    ImplementationSearcher handler = new ImplementationSearcher() {
      protected PsiElement[] filterElements(PsiElement element, PsiElement[] targetElements, final int offset) {
        Set<PsiElement> unique = new LinkedHashSet<PsiElement>(Arrays.asList(targetElements));
        for (PsiElement elt : targetElements) {
          PsiFile psiFile = elt.getContainingFile().getOriginalFile();
          if (psiFile.getVirtualFile() == null) unique.remove(elt);
        }
        // special case for Python (PY-237)
        // if the definition is the tree parent of the target element, filter out the target element
        for (int i = 1; i < targetElements.length; i++) {
          if (PsiTreeUtil.isAncestor(targetElements[i], targetElements[0], true)) {
            unique.remove(targetElements[0]);
            break;
          }
        }
        return unique.toArray(new PsiElement[unique.size()]);
      }
    };

    int offset = editor == null ? 0 : editor.getCaretModel().getOffset();
    final PsiElement[] handlerImplementations = handler.searchImplementations(element, offset, !(element instanceof PomTargetPsiElement), true);
    if (handlerImplementations.length > 0) return handlerImplementations;

    PsiFile psiFile = element.getContainingFile();
    if (psiFile == null) {
      // Magically, it's null for ant property declarations.
      element = element.getNavigationElement();
      psiFile = element.getContainingFile();
      if (psiFile == null) return PsiElement.EMPTY_ARRAY;
    }
    if (psiFile.getVirtualFile() != null && (element.getTextRange() != null || element instanceof PsiFile)) {
      return new PsiElement[]{element};
    }
    else {
      return PsiElement.EMPTY_ARRAY;
    }
  }

  @Override
  public void update(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null);
  }
}
