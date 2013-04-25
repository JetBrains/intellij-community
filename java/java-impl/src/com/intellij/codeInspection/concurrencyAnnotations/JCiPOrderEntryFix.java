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

/*
 * User: anna
 * Date: 30-Jul-2007
 */
package com.intellij.codeInspection.concurrencyAnnotations;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PathUtil;
import net.jcip.annotations.GuardedBy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class JCiPOrderEntryFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#" + JCiPOrderEntryFix.class.getName());

  @Override
  @NotNull
  public String getText() {
    return "Add jcip-annotations.jar to classpath";
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    if (!(file instanceof PsiJavaFile)) return false;

    final PsiReference reference = TargetElementUtil.findReference(editor);
    if (!(reference instanceof PsiJavaCodeReferenceElement)) return false;
    if (reference.resolve() != null) return false;
    @NonNls final String referenceName = ((PsiJavaCodeReferenceElement)reference).getReferenceName();
    if (referenceName == null) return false;
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return false;
    if (fileIndex.getModuleForFile(virtualFile) == null) return false;
    if (!(((PsiJavaCodeReferenceElement)reference).getParent() instanceof PsiAnnotation &&
          PsiUtil.isLanguageLevel5OrHigher(((PsiJavaCodeReferenceElement)reference)))) return false;
    if (!JCiPUtil.isJCiPAnnotation(referenceName)) return false;
    return true;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final PsiJavaCodeReferenceElement reference = (PsiJavaCodeReferenceElement)TargetElementUtil.findReference(editor);
    LOG.assertTrue(reference != null);
    String jarPath = PathUtil.getJarPathForClass(GuardedBy.class);
    final VirtualFile virtualFile = file.getVirtualFile();
    LOG.assertTrue(virtualFile != null);
    OrderEntryFix.addBundledJarToRoots(project, editor, ModuleUtil.findModuleForFile(virtualFile, project), reference,
                                       "net.jcip.annotations." + reference.getReferenceName(), jarPath);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

}