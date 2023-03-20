/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.debugger.memory.action;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.sun.jdi.ArrayType;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class JumpToTypeSourceAction extends ClassesActionBase {

  @Override
  protected boolean isEnabled(AnActionEvent e) {
    final PsiClass psiClass = getPsiClass(e, true);

    return super.isEnabled(e) && psiClass != null && psiClass.isPhysical();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  protected void perform(AnActionEvent e) {
    final PsiClass psiClass = getPsiClass(e, false);
    if (psiClass != null) {
      NavigationUtil.activateFileWithPsiElement(psiClass);
    }
  }

  @Nullable
  private PsiClass getPsiClass(AnActionEvent e, boolean fromUpdate) {
    ReferenceType selectedClass = fromUpdate ? e.getUpdateSession()
      .compute(this, "selectedClass", ActionUpdateThread.EDT, () -> DebuggerActionUtil.getSelectedClass(e))
                                             : DebuggerActionUtil.getSelectedClass(e);
    final Project project = e.getProject();
    if (selectedClass == null || project == null) {
      return null;
    }

    final ReferenceType targetClass = getObjectType(selectedClass);
    if (targetClass != null) {
      return DebuggerUtils.findClass(targetClass.name(), project, GlobalSearchScope.allScope(project));
    }

    return null;
  }

  @Nullable
  private static ReferenceType getObjectType(@NotNull ReferenceType ref) {
    if (!(ref instanceof ArrayType)) {
      return ref;
    }

    final String elementTypeName = ref.name().replace("[]", "");
    final VirtualMachine vm = ref.virtualMachine();
    final List<ReferenceType> referenceTypes = vm.classesByName(elementTypeName);
    if (referenceTypes.size() == 1) {
      return referenceTypes.get(0);
    }

    return null;
  }
}
