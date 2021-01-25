// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import org.jetbrains.annotations.NotNull;

public class ToggleShowDocsOnHoverAction extends ToggleAction implements HintManagerImpl.ActionToIgnore {
  private final DocumentationManager myDocumentationManager;
  private final boolean myOnToolbar;

  public ToggleShowDocsOnHoverAction(DocumentationManager documentationManager, boolean onToolbar) {
    super(CodeInsightBundle.messagePointer("javadoc.show.on.mouse.move"));
    myDocumentationManager = documentationManager;
    myOnToolbar = onToolbar;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    if (myDocumentationManager == null || myOnToolbar && myDocumentationManager.myToolWindow != null) {
      e.getPresentation().setEnabledAndVisible(false);
    }

  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return EditorSettingsExternalizable.getInstance().isShowQuickDocOnMouseOverElement();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    EditorSettingsExternalizable.getInstance().setShowQuickDocOnMouseOverElement(state);
  }
}
