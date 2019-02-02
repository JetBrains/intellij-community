// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.actions;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.chains.SimpleDiffRequestChain;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.tools.simple.SimpleDiffTool;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public class ShowBlankDiffWindowAction extends DumbAwareAction {
  public ShowBlankDiffWindowAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();

    String defaultText = null;
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor != null && editor.getSelectionModel().hasSelection()) {
      defaultText = editor.getSelectionModel().getSelectedText();
    }

    DocumentContent content1 = DiffContentFactory.getInstance().createEditable(project, StringUtil.notNullize(defaultText), null);
    DocumentContent content2 = DiffContentFactory.getInstance().createEditable(project, "", null);

    SimpleDiffRequest request = new SimpleDiffRequest(null, content1, content2, null, null);

    SimpleDiffRequestChain chain = new SimpleDiffRequestChain(request);
    chain.putUserData(DiffUserDataKeysEx.FORCE_DIFF_TOOL, SimpleDiffTool.INSTANCE);

    DiffManager.getInstance().showDiff(project, chain, DiffDialogHints.DEFAULT);
  }
}
