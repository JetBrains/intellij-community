// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.List;
import java.util.Objects;

/**
 * Action that displays a popup with a list of duplicate elements (classes, methods, fields, etc.) passed to constructor.
 * Code for the currently selected element in the list is displayed in the preview and also highlighted in the editor.
 * Selecting an element from the list closes the popup and navigates to the selected element.
 */
@NotNullByDefault
public class ShowDuplicateElementsFix extends PsiBasedModCommandAction<NavigatablePsiElement> {
  private final List<SmartPsiElementPointer<NavigatablePsiElement>> myNavigatablePsiElements;

  public ShowDuplicateElementsFix(List<? extends NavigatablePsiElement> duplicates) {
    super(NavigatablePsiElement.class);
    myNavigatablePsiElements = ContainerUtil.map(duplicates, dup -> SmartPointerManager.createPointer(dup));
  }

  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("show.duplicate.elements.family");
  }

  @Override
  protected Presentation getPresentation(ActionContext context, NavigatablePsiElement section) {
    var name = QuickFixBundle.message("show.duplicate.elements.text", elementName(getDuplicatePsiElements()));
    return Presentation.of(name);
  }

  @Override
  protected ModCommand perform(ActionContext context, NavigatablePsiElement element) {
    var title = QuickFixBundle.message("show.duplicate.elements.popup.title");
    var navigateActions = ContainerUtil.map(getDuplicatePsiElements(), d -> navigateAction(d));
    return ModCommand.chooseAction(title, navigateActions);
  }

  private static String elementName(List<NavigatablePsiElement> elements) {
    if (!elements.isEmpty()) {
      NavigatablePsiElement element = elements.get(0);
      if (element instanceof PsiMethod method) {
        return method.getName() + "()";
      }
      return Objects.requireNonNullElse(element.getName(), "");
    }
    return "";
  }

  @Override
  protected IntentionPreviewInfo generatePreview(ActionContext context, NavigatablePsiElement element) {
    var builder = new HtmlBuilder();
    var elements = getDuplicatePsiElements();
    for (int i = 0; i < elements.size(); i++) {
      if (i != 0) {
        builder.append(HtmlChunk.br());
      }
      var current = elements.get(i);
      builder.append(IntentionPreviewInfo.navigatePreviewHtmlChunk(current.getContainingFile(), current.getTextOffset()));
    }
    return new IntentionPreviewInfo.Html(builder.toFragment());
  }

  private List<NavigatablePsiElement> getDuplicatePsiElements() {
    return ContainerUtil.mapNotNull(myNavigatablePsiElements, SmartPsiElementPointer::getElement);
  }

  private static ModCommandAction navigateAction(NavigatablePsiElement navigatablePsiElement) {
    return new NavigateToAction(navigatablePsiElement);
  }

  private static class NavigateToAction extends PsiBasedModCommandAction<NavigatablePsiElement> {
    @IntentionFamilyName
    private final String myFamilyName;

    private NavigateToAction(NavigatablePsiElement navigatablePsiElement) {
      super(navigatablePsiElement);
      myFamilyName =
        QuickFixBundle.message("show.duplicate.elements.navigate.family", JavaElementKind.fromElement(navigatablePsiElement).object());
    }

    @Override
    public String getFamilyName() {
      return myFamilyName;
    }

    @Override
    protected Presentation getPresentation(ActionContext context, NavigatablePsiElement element) {
      int lineNumber = element.getContainingFile().getFileDocument().getLineNumber(element.getTextOffset());
      var title = QuickFixBundle.message("show.duplicate.elements.navigate.text", (lineNumber + 1));
      return Presentation.of(title).withHighlighting(element.getTextRange());
    }

    @Override
    protected IntentionPreviewInfo generatePreview(ActionContext context, NavigatablePsiElement element) {
      return IntentionPreviewInfo.snippet(element);
    }


    @Override
    protected ModCommand perform(ActionContext context, NavigatablePsiElement element) {
      return NavigateToDuplicateElementFix.createSelectCommand(element);
    }
  }
}
