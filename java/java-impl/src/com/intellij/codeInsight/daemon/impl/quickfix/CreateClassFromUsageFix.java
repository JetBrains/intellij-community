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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mike
 */
public class CreateClassFromUsageFix extends CreateClassFromUsageBaseFix {

  public CreateClassFromUsageFix(PsiJavaCodeReferenceElement refElement, CreateClassKind kind) {
    super(kind, refElement);
  }

  @Override
  public String getText(String varName) {
    return QuickFixBundle.message("create.class.from.usage.text", myKind.getDescription(), varName);
  }


  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    final PsiJavaCodeReferenceElement element = getRefElement();
    if (element == null) return;
    if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;
    final String superClassName = getSuperClassName(element);
    final PsiClass aClass = CreateFromUsageUtils.createClass(element, myKind, superClassName);
    if (aClass == null) return;

    ApplicationManager.getApplication().runWriteAction(
      () -> {
        PsiJavaCodeReferenceElement refElement = element;
        try {
          refElement = (PsiJavaCodeReferenceElement)refElement.bindToElement(aClass);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }

        IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

        Navigatable descriptor = PsiNavigationSupport.getInstance().createNavigatable(refElement.getProject(),
                                                                                      aClass.getContainingFile()
                                                                                            .getVirtualFile(),
                                                                                      aClass.getTextOffset());
        descriptor.navigate(true);
      }
    );
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
