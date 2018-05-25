// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.intention.AbstractIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.IconUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
class GutterIntentionAction extends AbstractIntentionAction implements Comparable<IntentionAction>, Iconable, ShortcutProvider {
  private final AnAction myAction;
  private final int myOrder;
  private final Icon myIcon;
  private String myText;

  private GutterIntentionAction(AnAction action, int order, Icon icon) {
    myAction = action;
    myOrder = order;
    myIcon = icon;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final RelativePoint relativePoint = JBPopupFactory.getInstance().guessBestPopupLocation(editor);
    myAction.actionPerformed(
      new AnActionEvent(relativePoint.toMouseEvent(), ((EditorEx)editor).getDataContext(), myText, new Presentation(),
                        ActionManager.getInstance(), 0));
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myText != null ? StringUtil.isNotEmpty(myText) : isAvailable(createActionEvent((EditorEx)editor));
  }

  @NotNull
  private static AnActionEvent createActionEvent(EditorEx editor) {
    return AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, editor.getDataContext());
  }

  private boolean isAvailable(@NotNull AnActionEvent event) {
    if (myText == null) {
      myAction.update(event);
      if (event.getPresentation().isEnabled() && event.getPresentation().isVisible()) {
        String text = event.getPresentation().getText();
        myText = text != null ? text : StringUtil.notNullize(myAction.getTemplatePresentation().getText());
      }
      else {
        myText = "";
      }
    }
    return StringUtil.isNotEmpty(myText);
  }

  @Override
  @NotNull
  public String getText() {
    return StringUtil.notNullize(myText);
  }

  static void addActions(@NotNull Editor hostEditor,
                         @NotNull ShowIntentionsPass.IntentionsInfo intentions, Project project, List<RangeHighlighterEx> result) {
    AnActionEvent event = createActionEvent((EditorEx)hostEditor);
    for (RangeHighlighterEx highlighter : result) {
      addActions(project, highlighter, intentions.guttersToShow, event);
    }
  }

  private static void addActions(@NotNull Project project,
                                 @NotNull RangeHighlighterEx info,
                                 @NotNull List<? super HighlightInfo.IntentionActionDescriptor> descriptors,
                                 @NotNull AnActionEvent event) {
    final GutterIconRenderer r = info.getGutterIconRenderer();
    if (r == null || DumbService.isDumb(project) && !DumbService.isDumbAware(r)) {
      return;
    }
    List<HighlightInfo.IntentionActionDescriptor> list = new ArrayList<>();
    for (AnAction action : new AnAction[]{r.getClickAction(), r.getMiddleButtonClickAction(), r.getRightButtonClickAction(),
      r.getPopupMenuActions()}) {
      if (action != null) {
        addActions(action, list, r, 0, event);
      }
    }
    descriptors.addAll(list);
  }

  private static void addActions(@NotNull AnAction action,
                                 @NotNull List<? super HighlightInfo.IntentionActionDescriptor> descriptors,
                                 @NotNull GutterIconRenderer renderer,
                                 int order,
                                 @NotNull AnActionEvent event) {
    if (action instanceof ActionGroup) {
      AnAction[] children = ((ActionGroup)action).getChildren(null);
      for (int i = 0; i < children.length; i++) {
        addActions(children[i], descriptors, renderer, i + order, event);
      }
    }
    Icon icon = action.getTemplatePresentation().getIcon();
    if (icon == null) icon = renderer.getIcon();
    if (icon.getIconWidth() < 16) icon = IconUtil.toSize(icon, 16, 16);
    final GutterIntentionAction gutterAction = new GutterIntentionAction(action, order, icon);
    if (!gutterAction.isAvailable(event)) return;
    descriptors.add(new HighlightInfo.IntentionActionDescriptor(gutterAction, Collections.emptyList(), null, icon) {
      @NotNull
      @Override
      public String getDisplayName() {
        return gutterAction.getText();
      }
    });
  }

  @SuppressWarnings("unchecked")
  @Override
  public int compareTo(@NotNull IntentionAction o) {
    if (o instanceof GutterIntentionAction) {
      return myOrder - ((GutterIntentionAction)o).myOrder;
    }
    return 0;
  }

  @Override
  public Icon getIcon(@IconFlags int flags) {
    return myIcon;
  }

  @Nullable
  @Override
  public ShortcutSet getShortcut() {
    return myAction.getShortcutSet();
  }
}
