// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.lang.LangBundle;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class GotoRelatedSymbolAction extends AnAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    PsiElement element = getContextElement(e.getDataContext());
    e.getPresentation().setEnabled(element != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();

    final PsiElement element = getContextElement(dataContext);
    if (element == null) return;

    // it's calculated in advance because `NavigationUtil.collectRelatedItems` might be
    // calculated under a cancellable progress, and we can't use the data context anymore,
    // since it can't be reused between swing events
    RelativePoint popupLocation = JBPopupFactory.getInstance().guessBestPopupLocation(dataContext);

    List<GotoRelatedItem> items = NavigationUtil.collectRelatedItems(element, dataContext);
    if (items.isEmpty()) {
      final JComponent label = HintUtil.createErrorLabel(LangBundle.message("hint.text.no.related.symbols"));
      label.setBorder(JBUI.Borders.empty(2, 7));
      JBPopupFactory.getInstance().createBalloonBuilder(label)
        .setFadeoutTime(3000)
        .setFillColor(HintUtil.getErrorColor())
        .createBalloon()
        .show(popupLocation, Balloon.Position.above);
      return;
    }

    if (items.size() == 1 && items.get(0).getElement() != null) {
      items.get(0).navigate();
      return;
    }
    NavigationUtil.getRelatedItemsPopup(items, LangBundle.message("popup.title.choose.target")).show(popupLocation);
  }

  @TestOnly
  @NotNull
  public static List<GotoRelatedItem> getItems(@NotNull PsiFile psiFile, @Nullable Editor editor, @Nullable DataContext dataContext) {
    return NavigationUtil.collectRelatedItems(getContextElement(psiFile, editor), dataContext);
  }

  @Nullable
  private static PsiElement getContextElement(@NotNull DataContext dataContext) {
    PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    if (file != null && editor != null) {
      return getContextElement(file, editor);
    }
    return element == null ? file : element;
  }

  @NotNull
  private static PsiElement getContextElement(@NotNull PsiFile psiFile, @Nullable Editor editor) {
    PsiElement contextElement = psiFile;
    if (editor != null) {
      PsiElement element = psiFile.findElementAt(editor.getCaretModel().getOffset());
      if (element != null) {
        contextElement = element;
      }
    }
    return contextElement;
  }
}
