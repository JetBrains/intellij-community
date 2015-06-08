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
import com.intellij.psi.PsiElement;

import java.awt.event.MouseEvent;

/**
 * @author Dmitry Avdeev
 */
public class NavigateAction<T extends PsiElement> extends AnAction {
  private LineMarkerInfo<T> myInfo;

  public NavigateAction(String text,
                        LineMarkerInfo<T> info,
                        String originalActionId) {
    super(text);
    myInfo = info;
    ShortcutSet set = ActionManager.getInstance().getAction(originalActionId).getShortcutSet();
    setShortcutSet(set);
  }

  public NavigateAction(LineMarkerInfo<T> info) {
    myInfo = info;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    if (myInfo.getNavigationHandler() != null) {
      MouseEvent mouseEvent = (MouseEvent)e.getInputEvent();
      T element = myInfo.getElement();
      if (element == null || !element.isValid()) return;

      myInfo.getNavigationHandler().navigate(mouseEvent, element);
    }
  }

  public static <T extends PsiElement> LineMarkerInfo<T> setNavigateAction(LineMarkerInfo<T> info, String text, String originalActionId) {
    NavigateAction<T> action = new NavigateAction<T>(text, info, originalActionId);
    info.setNavigateAction(action);
    return info;
  }
}
