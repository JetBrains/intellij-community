// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testIntegration;

import com.intellij.lang.LangBundle;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public final class GenerateFromTestCreatorsGroup extends ActionGroup {
  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    if (e == null) {
      return AnAction.EMPTY_ARRAY;
    }
    Project project = e.getData(CommonDataKeys.PROJECT);
    PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (project == null || file == null || editor == null) {
      return AnAction.EMPTY_ARRAY;
    }
    List<AnAction> result = new SmartList<>();
    for (TestCreator creator : LanguageTestCreators.INSTANCE.allForLanguage(file.getLanguage())) {
      final class Action extends AnAction {
        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
          return ActionUpdateThread.BGT;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          creator.createTest(project, editor, file);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
          String text = creator instanceof ItemPresentation ? ((ItemPresentation)creator).getPresentableText() : null;
          Presentation presentation = e.getPresentation();
          presentation.setText(ObjectUtils.notNull(text, LangBundle.message("action.test.text")));
          presentation.setEnabledAndVisible(creator.isAvailable(project, editor, file));
        }

        @Override
        public boolean isDumbAware() {
          return DumbService.isDumbAware(creator);
        }
      }
      result.add(new Action());
    }
    return result.toArray(AnAction.EMPTY_ARRAY);
  }
}
