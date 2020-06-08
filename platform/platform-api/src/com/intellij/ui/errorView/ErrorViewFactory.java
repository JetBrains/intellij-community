// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.errorView;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.ErrorTreeView;
import org.jetbrains.annotations.Nullable;

public interface ErrorViewFactory {
  ErrorTreeView createErrorTreeView(Project project, @Nullable String helpId, boolean createExitAction, AnAction[] extraPopupMenuActions,
                                    AnAction[] extraRightToolbarGroupActions, ContentManagerProvider contentManagerProvider);


  final class SERVICE {
    private SERVICE() {
    }

    public static ErrorViewFactory getInstance() {
      return ServiceManager.getService(ErrorViewFactory.class);
    }
  }
}
