package org.jetbrains.debugger.memory.action;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.xdebugger.XDebugSession;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.Nullable;

public class JumpToTypeSourceAction extends ClassesActionBase {

  @Override
  protected boolean isEnabled(AnActionEvent e) {
    final PsiClass psiClass = getPsiClass(e);

    return super.isEnabled(e) && psiClass != null && psiClass.isPhysical();
  }

  @Override
  protected void perform(AnActionEvent e) {
    final PsiClass psiClass = getPsiClass(e);
    if(psiClass != null) {
      NavigationUtil.openFileWithPsiElement(psiClass, true, true);
    }
  }

  @Nullable
  private PsiClass getPsiClass(AnActionEvent e) {
    ReferenceType selectedClass = getSelectedClass(e);
    XDebugSession session = getDebugSession(e);
    if(selectedClass == null || session == null) {
      return null;
    }

    Project project = session.getProject();
    return DebuggerUtils.findClass(selectedClass.name(), project, GlobalSearchScope.allScope(project));
  }
}
