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
package com.intellij.refactoring.changeSignature;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeSignature.inplace.LanguageChangeSignatureDetector;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: Sep 6, 2010
 */
public class JavaChangeSignatureDetector implements LanguageChangeSignatureDetector<DetectedJavaChangeInfo> {
  private static final Logger LOG = Logger.getInstance("#" + JavaChangeSignatureDetector.class.getName());

  @NotNull
  @Override
  public DetectedJavaChangeInfo createInitialChangeInfo(final @NotNull PsiElement element) {
    return DetectedJavaChangeInfo.createFromMethod(PsiTreeUtil.getParentOfType(element, PsiMethod.class), false);
  }

  @Override
  public void performChange(final DetectedJavaChangeInfo changeInfo, @NotNull final String oldText) {
    changeInfo.perform(changeInfo, oldText, true);
  }

  @Override
  public boolean isChangeSignatureAvailableOnElement(PsiElement element, DetectedJavaChangeInfo currentInfo) {
    final PsiMethod method = currentInfo.getMethod();
    TextRange range = method.getTextRange();
    PsiCodeBlock body = method.getBody();
    if (body != null) {
      range = new TextRange(range.getStartOffset(), body.getTextOffset());
    }
    return element.getContainingFile() == method.getContainingFile() && range.contains(element.getTextRange());
  }

  @Override
  public boolean ignoreChanges(PsiElement element) {
    if (element instanceof PsiMethod) return true;
    return PsiTreeUtil.getParentOfType(element, PsiImportList.class) != null;
  }

  @Override
  public TextRange getHighlightingRange(@NotNull DetectedJavaChangeInfo changeInfo) {
    PsiElement method = changeInfo.getMethod();
    return method != null ? getSignatureRange((PsiMethod)method) : null;
  }

  @Override
  public DetectedJavaChangeInfo createNextChangeInfo(String signature, @NotNull final DetectedJavaChangeInfo currentInfo, boolean delegate) {
    final PsiElement currentInfoMethod = currentInfo.getMethod();
    if (currentInfoMethod == null) {
      return null;
    }
    final Project project = currentInfoMethod.getProject();

    final PsiMethod oldMethod = currentInfo.getMethod();
    String visibility = "";
    PsiClass containingClass = oldMethod.getContainingClass();
    if (containingClass != null && containingClass.isInterface()) {
      visibility = PsiModifier.PUBLIC + " ";
    }
    PsiMethod method = JavaPsiFacade.getElementFactory(project).createMethodFromText((visibility + signature).trim(), oldMethod);
    return currentInfo.createNextInfo(method, delegate);
  }

  public static TextRange getSignatureRange(PsiMethod method) {
    int endOffset = method.getThrowsList().getTextRange().getEndOffset();
    int startOffset = method.getTextRange().getStartOffset();
    return new TextRange(startOffset, endOffset);
  }
}
