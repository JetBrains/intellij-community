/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.stubsHierarchy.impl.test;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.stubsHierarchy.impl.ClassAnchorUtil;
import com.intellij.psi.stubsHierarchy.impl.SmartClassAnchor;
import com.intellij.psi.stubsHierarchy.impl.HierarchyService;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * @author lambdamix
 */
public class LogSubtypesAction extends InheritanceAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubsHierarchy.LogSubtypesAction");
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final DataContext dataContext = e.getDataContext();
    PsiClass psiClass = getTarget(dataContext);
    if (psiClass != null) {
      psiClass = (PsiClass)psiClass.getOriginalElement();
      HierarchyService hierarchyService = HierarchyService.instance(project);
      if (hierarchyService != null) {
        LOG.info("Subtypes of " + psiClass + " retrieve started");
        SmartClassAnchor[] classAnchors = hierarchyService.getSingleClassHierarchy().getSubtypes(psiClass);
        String[] subtypes = new String[classAnchors.length];
        LOG.info("Subtypes of " + psiClass + " retrieve PSI started");
        for (int i = 0; i < classAnchors.length; i++) {
          PsiClass subClass = ClassAnchorUtil.retrieve(project, classAnchors[i]);
          subtypes[i] = subClass.toString() + "(" + subClass.getQualifiedName() + ", " +  subClass.getContainingFile().getVirtualFile().getPresentableUrl() +")";
        }
        LOG.info("Subtypes of " + psiClass + "(" + psiClass.getQualifiedName() + "): " + Arrays.toString(subtypes));
      }
    }
  }

  private static PsiClass getTarget(@NotNull final DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return null;

    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor != null) {
      final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file == null) return null;

      final PsiElement targetElement = TargetElementUtilBase.findTargetElement(editor, TargetElementUtilBase.ELEMENT_NAME_ACCEPTED |
                                                                                       TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED |
                                                                                       TargetElementUtilBase.LOOKUP_ITEM_ACCEPTED);

      if (targetElement instanceof PsiClass) {
        return (PsiClass)targetElement;
      }

      final int offset = editor.getCaretModel().getOffset();
      PsiElement element = file.findElementAt(offset);
      while (element != null) {
        if (element instanceof PsiFile) {
          if (!(element instanceof PsiClassOwner)) return null;
          final PsiClass[] classes = ((PsiClassOwner)element).getClasses();
          return classes.length == 1 ? classes[0] : null;
        }
        if (element instanceof PsiClass && !(element instanceof PsiAnonymousClass) && !(element instanceof PsiSyntheticClass)) {
          return (PsiClass)element;
        }
        element = element.getParent();
      }

      return null;
    }
    else {
      final PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
      return element instanceof PsiClass ? (PsiClass)element : null;
    }
  }
}
