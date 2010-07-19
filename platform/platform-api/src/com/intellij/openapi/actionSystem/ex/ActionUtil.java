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
package com.intellij.openapi.actionSystem.ex;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

public class ActionUtil {
  @NonNls private static final String WAS_ENABLED_BEFORE_DUMB = "WAS_ENABLED_BEFORE_DUMB";
  @NonNls public static final String WOULD_BE_ENABLED_IF_NOT_DUMB_MODE = "WOULD_BE_ENABLED_IF_NOT_DUMB_MODE";
  @NonNls private static final String WOULD_BE_VISIBLE_IF_NOT_DUMB_MODE = "WOULD_BE_VISIBLE_IF_NOT_DUMB_MODE";

  private ActionUtil() {
  }

  public static void showDumbModeWarning(AnActionEvent... events) {
    Project project = null;
    List<String> actionNames = new ArrayList<String>();
    for (final AnActionEvent event : events) {
      final String s = event.getPresentation().getText();
      if (StringUtil.isNotEmpty(s)) {
        actionNames.add(s);
      }

      final Project _project = PlatformDataKeys.PROJECT.getData(event.getDataContext());
      if (_project != null && project == null) {
        project = _project;
      }
    }

    if (project == null) {
      return;
    }

    String message;
    final String beAvailableUntil = " available while " + ApplicationNamesInfo.getInstance().getProductName() + " is updating indices";
    if (actionNames.isEmpty()) {
      message = "This action is not" + beAvailableUntil;
    } else if (actionNames.size() == 1) {
      message = "'" + actionNames.get(0) + "' action is not" + beAvailableUntil;
    } else {
      message = "None of the following actions are" + beAvailableUntil + ": " + StringUtil.join(actionNames, ", ");
    }

    DumbService.getInstance(project).showDumbModeNotification(message);
  }

  /**
   * @param action action
   * @param e action event
   * @param beforeActionPerformed whether to call
   * {@link com.intellij.openapi.actionSystem.AnAction#beforeActionPerformedUpdate(com.intellij.openapi.actionSystem.AnActionEvent)}
   * or
   * {@link com.intellij.openapi.actionSystem.AnAction#update(com.intellij.openapi.actionSystem.AnActionEvent)}
   * @return true if update tried to access indices in dumb mode
   */
  public static boolean performDumbAwareUpdate(AnAction action, AnActionEvent e, boolean beforeActionPerformed) {
    final Presentation presentation = e.getPresentation();
    final Boolean wasEnabledBefore = (Boolean)presentation.getClientProperty(WAS_ENABLED_BEFORE_DUMB);
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    final boolean dumbMode = project != null && DumbService.getInstance(project).isDumb();
    if (wasEnabledBefore != null && !dumbMode) {
      presentation.putClientProperty(WAS_ENABLED_BEFORE_DUMB, null);
      presentation.setEnabled(wasEnabledBefore.booleanValue());
      presentation.setVisible(true);
    }
    final boolean enabledBeforeUpdate = presentation.isEnabled();

    final boolean notAllowed = dumbMode && !action.isDumbAware();

    try {
      if (beforeActionPerformed) {
        action.beforeActionPerformedUpdate(e);
      }
      else {
        action.update(e);
      }
      presentation.putClientProperty(WOULD_BE_ENABLED_IF_NOT_DUMB_MODE, notAllowed && presentation.isEnabled());
      presentation.putClientProperty(WOULD_BE_VISIBLE_IF_NOT_DUMB_MODE, notAllowed && presentation.isVisible());
    }
    catch (IndexNotReadyException e1) {
      if (notAllowed) {
        return true;
      }
      throw e1;
    }
    finally {
      if (notAllowed) {
        if (wasEnabledBefore == null) {
          presentation.putClientProperty(WAS_ENABLED_BEFORE_DUMB, enabledBeforeUpdate);
        }
        presentation.setEnabled(false);
      }
    }
    
    return false;
  }

  public static boolean lastUpdateAndCheckDumb(AnAction action, AnActionEvent e, boolean visibilityMatters) {
    performDumbAwareUpdate(action, e, true);

    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project != null && DumbService.getInstance(project).isDumb() && !action.isDumbAware()) {
      if (Boolean.FALSE.equals(e.getPresentation().getClientProperty(WOULD_BE_ENABLED_IF_NOT_DUMB_MODE))) {
        return false;
      }
      if (visibilityMatters && Boolean.FALSE.equals(e.getPresentation().getClientProperty(WOULD_BE_VISIBLE_IF_NOT_DUMB_MODE))) {
        return false;
      }

      showDumbModeWarning(e);
      return false;
    }

    if (!e.getPresentation().isEnabled()) {
      return false;
    }
    if (visibilityMatters && !e.getPresentation().isVisible()) {
      return false;
    }

    return true;
  }

}
