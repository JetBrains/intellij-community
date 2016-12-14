package org.jetbrains.debugger.memory.action;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.xdebugger.XDebugSession;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class JumpToTypeSourceAction extends ClassesActionBase {

  @Override
  protected boolean isEnabled(AnActionEvent e) {
    final PsiClass psiClass = getPsiClass(e);

    return super.isEnabled(e) && psiClass != null && psiClass.isPhysical();
  }

  @Override
  protected void perform(AnActionEvent e) {
    final PsiClass psiClass = getPsiClass(e);
    if (psiClass != null) {
      NavigationUtil.activateFileWithPsiElement(psiClass);
    }
  }

  @Nullable
  private PsiClass getPsiClass(AnActionEvent e) {
    ReferenceType selectedClass = getSelectedClass(e);
    XDebugSession session = getDebugSession(e);
    if (selectedClass == null || session == null) {
      return null;
    }

    ReferenceType targetClass = getObjectType(selectedClass);
    if (targetClass != null) {
      Project project = session.getProject();
      return DebuggerUtils
          .findClass(targetClass.name(), project, GlobalSearchScope.allScope(project));
    }

    return null;
  }

  @Nullable
  private ReferenceType getObjectType(@NotNull ReferenceType ref) {
    if (!(ref instanceof ArrayType)) {
      return ref;
    }

    String elementTypeName = ref.name().replace("[]", "");
    VirtualMachine vm = ref.virtualMachine();
    final List<ReferenceType> referenceTypes = vm.classesByName(elementTypeName);
    if (referenceTypes.size() == 1) {
      return referenceTypes.get(0);
    }

    return null;
  }
}
