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
package com.intellij.refactoring.changeSignature;

import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: Sep 6, 2010
 */
public class JavaChangeSignatureDetector implements LanguageChangeSignatureDetector {
  private static final Logger LOG = Logger.getInstance("#" + JavaChangeSignatureDetector.class.getName());

  @Override
  public ChangeInfo createInitialChangeInfo(final @NotNull PsiElement element) {
    final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
    if (method != null && isInsideMethodSignature(element, method)) {
      //do not initialize change signature on return type change
      if (element.getTextRange().getEndOffset() <= method.getTextOffset()) return null;
      return DetectedJavaChangeInfo.createFromMethod(method);
    } else {
      final PsiVariable variable = PsiTreeUtil.getParentOfType(element, PsiVariable.class);
      if (variable != null) {
        return new RenameChangeInfo(variable, null) {
          @Override
          public Language getLanguage() {
            return StdLanguages.JAVA;
          }
        };
      }
    }
    return null;
  }

  @Override
  public boolean performChange(final ChangeInfo changeInfo, ChangeInfo initialChangeInfo, @NotNull final String oldText, boolean silently) {
    if (changeInfo instanceof DetectedJavaChangeInfo) {
      return ((DetectedJavaChangeInfo)changeInfo).perform(initialChangeInfo, oldText, silently);
    } else if (changeInfo instanceof RenameChangeInfo) {
      ((RenameChangeInfo)changeInfo).perform();
      return true;
    }
    return false;

  }

  @Override
  public boolean isChangeSignatureAvailableOnElement(PsiElement element, ChangeInfo currentInfo) {
    if (currentInfo instanceof RenameChangeInfo) {
      final PsiElement nameIdentifier = ((RenameChangeInfo)currentInfo).getNameIdentifier();
      if (nameIdentifier != null) {
        final TextRange nameIdentifierTextRange = nameIdentifier.getTextRange();
        return nameIdentifierTextRange.contains(element.getTextRange()) ||
               nameIdentifierTextRange.getEndOffset() == element.getTextOffset();
      }
    }
    else if (currentInfo instanceof JavaChangeInfo) {
      final PsiMethod method = (PsiMethod)currentInfo.getMethod();
      return getSignatureRange(method).contains(element.getTextRange());
    }
    return false;
  }

  @Override
  public boolean ignoreChanges(PsiElement element) {
    if (element instanceof PsiMethod) return true;
    return PsiTreeUtil.getParentOfType(element, PsiImportList.class) != null;
  }

  @Nullable
  @Override
  public TextRange getHighlightingRange(ChangeInfo changeInfo) {
    if (changeInfo == null) return null;
    if (changeInfo instanceof RenameChangeInfo) {
      PsiElement nameIdentifier = ((RenameChangeInfo)changeInfo).getNameIdentifier();
      return nameIdentifier != null ? nameIdentifier.getTextRange() : null;
    }

    PsiElement method = changeInfo.getMethod();
    return method instanceof PsiMethod ? getSignatureRange((PsiMethod)method) : null;
  }

  @Nullable
  @Override
  public String extractSignature(PsiElement element, @NotNull ChangeInfo initialChangeInfo) {
    final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
    if (method != null && isInsideMethodSignature(element, method) && method == initialChangeInfo.getMethod()) {
      final PsiCodeBlock body = method.getBody();
      final TextRange signatureRange = new TextRange(0, body != null ? body.getStartOffsetInParent() : method.getTextLength());
      return signatureRange.substring(method.getText());
    } else if (element instanceof PsiIdentifier && element.getParent() instanceof PsiNamedElement) {
      return element.getText();
    }
    return null;
  }

  @Override
  public ChangeInfo createNextChangeInfo(String signature, @NotNull final ChangeInfo currentInfo, String initialName) {
    final PsiElement currentInfoMethod = currentInfo.getMethod();
    if (currentInfoMethod == null) {
      return null;
    }
    final Project project = currentInfoMethod.getProject();
    if (currentInfo instanceof RenameChangeInfo) {
      return currentInfo;
    }
    
    final PsiMethod oldMethod = (PsiMethod)currentInfo.getMethod();
    String visibility = "";
    PsiClass containingClass = oldMethod.getContainingClass();
    if (containingClass != null && containingClass.isInterface()) {
      visibility = PsiModifier.PUBLIC + " ";
    }
    PsiMethod method = JavaPsiFacade.getElementFactory(project).createMethodFromText((visibility + signature).trim(), oldMethod);
    return ((DetectedJavaChangeInfo)currentInfo).createNextInfo(method);
  }

  private static boolean isInsideMethodSignature(PsiElement element, @NotNull PsiMethod method) {
    final PsiCodeBlock body = method.getBody();
    final TextRange textRange = element.getTextRange();
    final PsiModifierList psiModifierList = method.getModifierList();
    if (psiModifierList instanceof LightModifierList) return false;
    if (body != null) {
      return textRange.getEndOffset() <= body.getTextOffset() && textRange.getStartOffset() >= psiModifierList.getTextRange().getEndOffset();
    }
    return textRange.getStartOffset() >= psiModifierList.getTextRange().getEndOffset() &&
           textRange.getEndOffset() <= method.getTextRange().getEndOffset();
  }

  public static TextRange getSignatureRange(PsiMethod method) {
    final PsiCodeBlock body = method.getBody();
    if (body != null) {
      return new TextRange(method.getTextRange().getStartOffset(), body.getTextOffset());
    }
    return new TextRange(method.getTextRange().getStartOffset(),
                         method.getTextRange().getEndOffset());
  }

  @Override
  public boolean isMoveParameterAvailable(PsiElement element, boolean left) {
    if (element instanceof PsiParameter) {
      final PsiParameter parameter = (PsiParameter)element;
      final PsiElement declarationScope = parameter.getDeclarationScope();
      if (declarationScope instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)declarationScope;
        final int parameterIndex = method.getParameterList().getParameterIndex(parameter);
        if (left) {
          return parameterIndex > 0;
        } else {
          return parameterIndex < method.getParameterList().getParametersCount() - 1;
        }
      }
    }
    return false;
  }

  @Override
  public void moveParameter(final PsiElement element, final Editor editor, final boolean left) {
    final PsiParameter parameter = (PsiParameter)element;
    final PsiMethod method = (PsiMethod)parameter.getDeclarationScope();
    final int parameterIndex = method.getParameterList().getParameterIndex(parameter);
    new WriteCommandAction(element.getProject(), MOVE_PARAMETER){
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        final PsiParameterList parameterList = method.getParameterList();
        final PsiParameter[] parameters = parameterList.getParameters();
        final int deltaOffset = editor.getCaretModel().getOffset() - parameter.getTextRange().getStartOffset();
        final PsiParameter frst = left ? parameters[parameterIndex - 1] : parameter;
        final PsiParameter scnd = left ? parameter : parameters[parameterIndex + 1];
        final int startOffset = frst.getTextRange().getStartOffset();
        final int endOffset = scnd.getTextRange().getEndOffset();

        final PsiFile file = method.getContainingFile();
        final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
        if (document != null) {
          final String comma_whitespace_between =
            document.getText().substring(frst.getTextRange().getEndOffset(), scnd.getTextRange().getStartOffset());
          document.replaceString(startOffset, endOffset, scnd.getText() + comma_whitespace_between + frst.getText());
          editor.getCaretModel().moveToOffset(document.getText().indexOf(parameter.getText(), startOffset) + deltaOffset);
        }
      }
    }.execute();
  }
}
