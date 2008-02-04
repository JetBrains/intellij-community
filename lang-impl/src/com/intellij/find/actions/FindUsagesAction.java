package com.intellij.find.actions;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.find.FindBundle;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageView;

public class FindUsagesAction extends AnAction {

  public FindUsagesAction() {
    setInjectedContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) {
      return;
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    UsageTarget[] usageTargets = e.getData(UsageView.USAGE_TARGETS_KEY);
    if (usageTargets != null) {
      usageTargets[0].findUsages();
      return;
    }

    chooseAmbiguousTarget(e, project);
  }

  private static void chooseAmbiguousTarget(final AnActionEvent e, final Project project) {
    Editor editor = e.getData(PlatformDataKeys.EDITOR);
    if (editor != null) {
      int offset = editor.getCaretModel().getOffset();
      if (GotoDeclarationAction.chooseAmbiguousTarget(editor, offset, new PsiElementProcessor<PsiElement>() {
        public boolean execute(final PsiElement element) {
          new PsiElement2UsageTargetAdapter(element).findUsages();
          return false;
        }
      }, FindBundle.message("find.usages.ambiguous.title"))) return;
    }
    Messages.showMessageDialog(project,
          FindBundle.message("find.no.usages.at.cursor.error"),
          CommonBundle.getErrorTitle(),
          Messages.getErrorIcon()
        );
  }

  public void update(AnActionEvent event){
    FindUsagesInFileAction.updateFindUsagesAction(event);
  }
}
