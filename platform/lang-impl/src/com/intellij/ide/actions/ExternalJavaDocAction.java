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

package com.intellij.ide.actions;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lang.documentation.ExternalDocumentationHandler;
import com.intellij.lang.documentation.ExternalDocumentationProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.Set;

public class ExternalJavaDocAction extends AnAction {

  public ExternalJavaDocAction() {
    setInjectedContext(true);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }

    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    PsiElement element = getElement(dataContext, editor);
    if (element == null) {
      Messages.showMessageDialog(
        project,
        IdeBundle.message("message.please.select.element.for.javadoc"),
        IdeBundle.message("title.no.element.selected"),
        Messages.getErrorIcon()
      );
      return;
    }


    PsiFile context = CommonDataKeys.PSI_FILE.getData(dataContext);

    PsiElement originalElement = getOriginalElement(context, editor);
    DocumentationManager.storeOriginalElement(project, originalElement, element);
    final DocumentationProvider provider = DocumentationManager.getProviderFromElement(element);

    if (provider instanceof ExternalDocumentationHandler && ((ExternalDocumentationHandler)provider).handleExternal(element, originalElement)) {
      return;
    }

    final List<String> urls = provider.getUrlFor(element, originalElement);
    if (urls != null && !urls.isEmpty()) {
      showExternalJavadoc(urls, PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext));
    }
    else if (provider instanceof ExternalDocumentationProvider) {
      final ExternalDocumentationProvider externalDocumentationProvider = (ExternalDocumentationProvider)provider;
      if (externalDocumentationProvider.canPromptToConfigureDocumentation(element)) {
        externalDocumentationProvider.promptToConfigureDocumentation(element);
      }
    }
  }

  public static void showExternalJavadoc(@NotNull List<String> urls, Component component) {
    Set<String> set = new THashSet<String>(urls);
    if (set.size() > 1) {
      JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<String>("Choose external documentation root", ArrayUtil.toStringArray(set)) {
        @Override
        public PopupStep onChosen(final String selectedValue, final boolean finalChoice) {
          BrowserUtil.browse(selectedValue);
          return FINAL_CHOICE;
        }
      }).showInBestPositionFor(DataManager.getInstance().getDataContext(component));
    }
    else if (set.size() == 1) {
      BrowserUtil.browse(urls.get(0));
    }
  }

  @Nullable
  private static PsiElement getOriginalElement(final PsiFile context, final Editor editor) {
    return (context!=null && editor!=null)? context.findElementAt(editor.getCaretModel().getOffset()):null;
  }

  @Override
  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    PsiElement element = getElement(dataContext, editor);
    final PsiElement originalElement = getOriginalElement(CommonDataKeys.PSI_FILE.getData(dataContext), editor);
    DocumentationManager.storeOriginalElement(CommonDataKeys.PROJECT.getData(dataContext), originalElement, element);
    final DocumentationProvider provider = DocumentationManager.getProviderFromElement(element);
    boolean enabled;
    if (provider instanceof ExternalDocumentationProvider) {
      final ExternalDocumentationProvider edProvider = (ExternalDocumentationProvider)provider;
      enabled = edProvider.hasDocumentationFor(element, originalElement) || edProvider.canPromptToConfigureDocumentation(element);
    }
    else {
      final List<String> urls = provider.getUrlFor(element, originalElement);
      enabled = urls != null && !urls.isEmpty();
    }
    if (editor != null) {
      presentation.setEnabled(enabled);
      if (ActionPlaces.isMainMenuOrActionSearch(event.getPlace())) {
        presentation.setVisible(true);
      }
      else {
        presentation.setVisible(enabled);
      }
    }
    else{
      presentation.setEnabled(enabled);
      presentation.setVisible(true);
    }
  }

  private static PsiElement getElement(DataContext dataContext, Editor editor) {
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    if (element == null && editor != null) {
      PsiReference reference = TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset());
      if (reference != null) {
        element = reference.getElement();
      }
    }
    return element;
  }
}