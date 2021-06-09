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
package com.intellij.codeInsight.daemon;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.util.NlsActions;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;

/**
 * @author Dmitry Avdeev
 */
public class NavigateAction<T extends PsiElement> extends AnAction {
  private final LineMarkerInfo<T> myInfo;
  @Nullable private final String myOriginalActionId;

  public NavigateAction(@NotNull @NlsActions.ActionText String text,
                        @NotNull LineMarkerInfo<T> info,
                        @Nullable String originalActionId) {
    super(text);
    myInfo = info;
    myOriginalActionId = originalActionId;
    if (originalActionId != null) {
      ShortcutSet set = ActionManager.getInstance().getAction(originalActionId).getShortcutSet();
      setShortcutSet(set);
    }
  }

  public NavigateAction(@NotNull LineMarkerInfo<T> info) {
    myInfo = info;
    myOriginalActionId = null;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (myInfo.getNavigationHandler() != null) {
      MouseEvent mouseEvent = (MouseEvent)e.getInputEvent();
      T element = myInfo.getElement();
      if (element == null || !element.isValid()) return;

      myInfo.getNavigationHandler().navigate(mouseEvent, element);
    }
  }

  @NotNull
  public static <T extends PsiElement> LineMarkerInfo<T> setNavigateAction(@NotNull LineMarkerInfo<T> info, @NotNull @NlsActions.ActionText String text, @Nullable String originalActionId) {
    return setNavigateAction(info, text, originalActionId, null);
  }

  @NotNull
  public static <T extends PsiElement> LineMarkerInfo<T> setNavigateAction(@NotNull LineMarkerInfo<T> info, @NotNull @NlsActions.ActionText String text,
                                                                           @Nullable String originalActionId, @Nullable Icon icon) {
    NavigateAction<T> action = new NavigateAction<>(text, info, originalActionId);

    if (icon != null) {
      action.getTemplatePresentation().setIcon(icon);
    }

    info.setNavigateAction(action);
    return info;
  }

  @Nullable
  public String getOriginalActionId() {
    return myOriginalActionId;
  }
}
