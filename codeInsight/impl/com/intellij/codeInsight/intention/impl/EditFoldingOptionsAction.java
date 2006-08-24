package com.intellij.codeInsight.intention.impl;

import com.intellij.application.options.EditorOptions;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.ui.search.DefaultSearchableConfigurable;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class EditFoldingOptionsAction implements IntentionAction {
  @NotNull
  public String getText() {
    return ApplicationBundle.message("edit.code.folding.options");
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return editor.getFoldingModel().isOffsetCollapsed(editor.getCaretModel().getOffset());
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    EditorOptions editorOptions = ApplicationManager.getApplication().getComponent(EditorOptions.class);
    final DefaultSearchableConfigurable configurable = new DefaultSearchableConfigurable(editorOptions);
    ShowSettingsUtil.getInstance().editConfigurable(project, configurable, new Runnable() {
      public void run() {
        configurable.enableSearch(ApplicationBundle.message("group.code.folding"));
      }
    });
  }

  public boolean startInWriteAction() {
    return false;
  }
}
