package com.intellij.find.impl;

import com.intellij.find.FindBundle;
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.ProjectScope;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.impl.UsageViewImpl;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author cdr
 */
public class ShowRecentFindUsagesGroup extends ActionGroup {
  public void update(final AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null);
    e.getPresentation().setVisible(project != null);
  }

  public AnAction[] getChildren(@Nullable final AnActionEvent e) {
    if (e == null) return EMPTY_ARRAY;
    Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) return EMPTY_ARRAY;
    final FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager();
    List<FindUsagesManager.SearchData> history = new ArrayList<FindUsagesManager.SearchData>(findUsagesManager.getFindUsageHistory());
    Collections.reverse(history);

    String description =
      ActionManager.getInstance().getAction(UsageViewImpl.SHOW_RECENT_FIND_USAGES_ACTION_ID).getTemplatePresentation().getDescription();

    List<AnAction> children = new ArrayList<AnAction>(history.size());
    for (final FindUsagesManager.SearchData data : history) {
      if (data.myElements == null) {
        continue;
      }
      PsiElement psiElement = data.myElements[0].getElement();
      if (psiElement == null) continue;
      String scopeString = data.myOptions.searchScope == null ? null : data.myOptions.searchScope.getDisplayName();
      String text = FindBundle.message("recent.find.usages.action.popup", StringUtil.capitalize(UsageViewUtil.getType(psiElement)),
                                       UsageViewUtil.getDescriptiveName(psiElement),
                                       scopeString == null ? ProjectScope.getAllScope(psiElement.getProject()).getDisplayName() : scopeString);
      AnAction action = new AnAction(text, description, psiElement.getIcon(0)) {
        public void actionPerformed(final AnActionEvent e) {
          findUsagesManager.rerunAndRecallFromHistory(data);
        }
      };
      children.add(action);
    }
    return children.toArray(new AnAction[children.size()]);
  }
}
