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
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.intention.AbstractIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
class GutterIntentionAction extends AbstractIntentionAction implements Comparable<IntentionAction> {
  private final AnAction myAction;
  private final int myOrder;
  private String myText;

  private GutterIntentionAction(AnAction action, int order) {
    myAction = action;
    myOrder = order;
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
    if (myText == null) {
      AnActionEvent event = AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, ((EditorEx)editor).getDataContext());
      myAction.update(event);
      myText = event.getPresentation().getText();
      if (myText == null) myText = myAction.getTemplatePresentation().getText();
      if (myText == null) myText = "";
      else {
        ShortcutSet shortcutSet = myAction.getShortcutSet();
        Shortcut[] shortcuts = shortcutSet.getShortcuts();
        Shortcut element = ArrayUtil.getFirstElement(shortcuts);
        if (element != null) {
          String text = KeymapUtil.getShortcutText(element);
          myText += " (" + text + ")";
        }
      }
    }
    return StringUtil.isNotEmpty(myText);
  }

  @Override
  @NotNull
  public String getText() {
    return StringUtil.notNullize(myText);
  }

  static void addActions(@NotNull Project project,
                         @NotNull Editor editor,
                         @NotNull PsiFile psiFile,
                         @NotNull RangeHighlighterEx info,
                         @NotNull List<HighlightInfo.IntentionActionDescriptor> descriptors) {
    final GutterIconRenderer renderer = info.getGutterIconRenderer();
    if (renderer == null || DumbService.isDumb(project) && !DumbService.isDumbAware(renderer)) {
      return;
    }
    addActions(project, editor, psiFile, renderer.getClickAction(), descriptors, renderer, 0);
    addActions(project, editor, psiFile, renderer.getMiddleButtonClickAction(), descriptors, renderer, 0);
    addActions(project, editor, psiFile, renderer.getRightButtonClickAction(), descriptors, renderer, 0);
    addActions(project, editor, psiFile, renderer.getPopupMenuActions(), descriptors, renderer, 0);
  }

  private static void addActions(@NotNull Project project,
                                 @NotNull Editor editor,
                                 @NotNull PsiFile psiFile,
                                 @Nullable AnAction action,
                                 @NotNull List<HighlightInfo.IntentionActionDescriptor> descriptors,
                                 @NotNull GutterIconRenderer renderer,
                                 int order) {
    if (action == null) {
      return;
    }
    if (action instanceof ActionGroup) {
      AnAction[] children = ((ActionGroup)action).getChildren(null);
      for (int i = 0; i < children.length; i++) {
        AnAction child = children[i];
        addActions(project, editor, psiFile, child, descriptors, renderer, i + order);
      }
    }
    final IntentionAction gutterAction = new GutterIntentionAction(action, order);
    if (!gutterAction.isAvailable(project, editor, psiFile)) return;
    Icon icon = action.getTemplatePresentation().getIcon();
    if (icon == null) icon = renderer.getIcon();
    if (icon.getIconWidth() < 16) icon = IconUtil.toSize(icon, 16, 16);
    HighlightInfo.IntentionActionDescriptor descriptor =
      new HighlightInfo.IntentionActionDescriptor(gutterAction, Collections.<IntentionAction>emptyList(), null, icon) {
        @Nullable
        @Override
        public String getDisplayName() {
          return gutterAction.getText();
        }
      };
    descriptors.add(descriptor);
  }

  @SuppressWarnings("unchecked")
  @Override
  public int compareTo(@NotNull IntentionAction o) {
    if (o instanceof GutterIntentionAction) {
      return myOrder - ((GutterIntentionAction)o).myOrder;
    }
    return 0;
  }
}
