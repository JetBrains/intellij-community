/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator;
import com.intellij.codeInsight.navigation.actions.GotoSuperAction;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.MethodCellRenderer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.FindSuperElementsHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaGotoSuperHandler implements CodeInsightActionHandler {
  @Override
  public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed(GotoSuperAction.FEATURE_ID);

    int offset = editor.getCaretModel().getOffset();
    PsiElement[] superElements = findSuperElements(file, offset);
    if (superElements == null || superElements.length == 0) return;
    if (superElements.length == 1) {
      PsiElement superElement = superElements[0].getNavigationElement();
      final PsiFile containingFile = superElement.getContainingFile();
      if (containingFile == null) return;
      final VirtualFile virtualFile = containingFile.getVirtualFile();
      if (virtualFile == null) return;
      OpenFileDescriptor descriptor = new OpenFileDescriptor(project, virtualFile, superElement.getTextOffset());
      FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
    } else {
      if (superElements[0] instanceof PsiMethod) {
        boolean showMethodNames = !PsiUtil.allMethodsHaveSameSignature((PsiMethod[])superElements);
        PsiElementListNavigator.openTargets(editor, (PsiMethod[])superElements,
                                            CodeInsightBundle.message("goto.super.method.chooser.title"),
                                            CodeInsightBundle.message("goto.super.method.findUsages.title", ((PsiMethod)superElements[0]).getName()),
                                            new MethodCellRenderer(showMethodNames));
      }
      else {
        NavigationUtil.getPsiElementPopup(superElements, CodeInsightBundle.message("goto.super.class.chooser.title")).showInBestPositionFor(editor);
      }
    }
  }

  @Nullable
  private PsiElement[] findSuperElements(PsiFile file, int offset) {
    PsiNameIdentifierOwner parent = getElement(file, offset);
    if (parent == null) return null;

    return FindSuperElementsHelper.findSuperElements(parent);
  }

  protected PsiNameIdentifierOwner getElement(PsiFile file, int offset) {
    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;

    PsiNameIdentifierOwner parent = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiClass.class);
    if (parent == null)
      return null;
    return parent;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
