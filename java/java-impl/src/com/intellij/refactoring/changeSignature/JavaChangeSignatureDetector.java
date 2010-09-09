/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;

/**
 * User: anna
 * Date: Sep 6, 2010
 */
public class JavaChangeSignatureDetector implements LanguageChangeSignatureDetector {
  private static final Logger LOG = Logger.getInstance("#" + JavaChangeSignatureDetector.class.getName());

  @Override
  public ChangeInfo createCurrentChangeSignature(final @NotNull PsiElement element,
                                                 final @Nullable ChangeInfo changeInfo) {
    PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
    if (method != null && isInsideMethodSignature(element, method)) {
      final String newVisibility = VisibilityUtil.getVisibilityModifier(method.getModifierList());
      final PsiType returnType = method.getReturnType();
      final CanonicalTypes.Type newReturnType;
      final ParameterInfoImpl[] parameterInfos;
      try {
        newReturnType = returnType != null ? CanonicalTypes.createTypeWrapper(returnType) : null;
        parameterInfos = ParameterInfoImpl.fromMethod(method);
      }
      catch (IncorrectOperationException e) {
        return null;
      }
      final MyJavaChangeInfo fromMethod = new MyJavaChangeInfo(newVisibility, method, newReturnType, parameterInfos, null, method.getName());
      if (changeInfo == null) { //before replacement
        fromMethod.setSuperMethod(method.findDeepestSuperMethod());
        return fromMethod;
      } else {
        final MyJavaChangeInfo info = (MyJavaChangeInfo)changeInfo;
        if (!info.getMethod().equals(method)) return null;
        if (!info.equals(fromMethod)) {
          final JavaParameterInfo[] oldParameters = info.getNewParameters();
          for (int i = 0; i < parameterInfos.length; i++) {
            ParameterInfoImpl parameterInfo = parameterInfos[i];
            JavaParameterInfo oldParameter = null;
            for (JavaParameterInfo parameter : oldParameters) {
              if (Comparing.strEqual(parameter.getName(), parameterInfo.getName()) &&
                  Comparing.strEqual(parameter.getTypeText(), parameterInfo.getTypeText())) {
                oldParameter = parameter;
                break;
              }
            }
            if (oldParameter == null && oldParameters.length > i && info.getOldParameterNames().length > i) {
              if (Comparing.strEqual(info.getOldParameterNames()[i], parameterInfo.getName()) ||
                  Comparing.strEqual(info.getOldParameterTypes()[i], parameterInfo.getTypeText())) {
                oldParameter = oldParameters[i];
              }
            }
            final int oldParameterIndex = oldParameter != null ? oldParameter.getOldIndex() : -1;
            parameterInfos[i] = new ParameterInfoImpl(oldParameterIndex,
                                                      parameterInfo.getName(),
                                                      parameterInfo.getTypeWrapper().getType(element, element.getManager()),
                                                      oldParameterIndex == -1 ? "intellijidearulezzz" : "");
          }
          final MyJavaChangeInfo javaChangeInfo =
            new MyJavaChangeInfo(newVisibility, method, newReturnType, parameterInfos, info.getNewExceptions(), info.getOldName()) {
              @Override
              protected void fillOldParams(PsiMethod method) {
                oldParameterNames = info.getOldParameterNames();
                oldParameterTypes = info.getOldParameterTypes();
              }
            };
          javaChangeInfo.setSuperMethod(info.getSuperMethod());
          return javaChangeInfo;
        }
      }
    }
    return null;
  }

  private static class MyJavaChangeInfo extends JavaChangeInfoImpl  {
    private PsiMethod mySuperMethod;
    private MyJavaChangeInfo(String newVisibility,
                             PsiMethod method,
                             CanonicalTypes.Type newType,
                             @NotNull ParameterInfoImpl[] newParms,
                             ThrownExceptionInfo[] newExceptions,
                             String oldName) {
      super(newVisibility, method, method.getName(), newType, newParms, newExceptions, false,
            new HashSet<PsiMethod>(),
            new HashSet<PsiMethod>(), oldName);
    }

    @Override
    protected void setupPropagationEnabled(PsiParameter[] parameters, ParameterInfoImpl[] newParms) {
      isPropagationEnabled = false;
    }

    public PsiMethod getSuperMethod() {
      if (mySuperMethod == null) {
        return getMethod();
      }
      return mySuperMethod;
    }

    public void setSuperMethod(PsiMethod superMethod) {
      mySuperMethod = superMethod;
    }
  }

  @Override
  public boolean showDialog(ChangeInfo changeInfo, @NotNull final String oldText) {
    if (changeInfo instanceof MyJavaChangeInfo) {
      final MyJavaChangeInfo info = (MyJavaChangeInfo)changeInfo;
      final PsiMethod method = info.getSuperMethod();
      final JavaChangeSignatureDialog dialog =
        new JavaChangeSignatureDialog(method.getProject(), new JavaMethodDescriptor(info.getMethod()) {
          @Override
          public String getReturnTypeText() {
            return info.getNewReturnType().getTypeText();
          }
        }, true, method) {
          protected BaseRefactoringProcessor createRefactoringProcessor() {
            return new ChangeSignatureProcessor(myProject, new MyJavaChangeInfo(info.getNewVisibility(), info.getSuperMethod(),
                                                                                info.getNewReturnType(),
                                                                                (ParameterInfoImpl[])info.getNewParameters(),
                                                                                info.getNewExceptions(), info.getOldName()) {
              @Override
              protected void fillOldParams(PsiMethod method) {
                oldParameterNames = info.getOldParameterNames();
                oldParameterTypes = info.getOldParameterTypes();
              }
            });
          }

          @Override
          protected void invokeRefactoring(final BaseRefactoringProcessor processor) {
            CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
              @Override
              public void run() {
                ApplicationManager.getApplication().runWriteAction(new Runnable() {
                  @Override
                  public void run() {
                    final PsiFile file = method.getContainingFile();
                    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
                    final Document document = documentManager.getDocument(file);
                    if (document != null) {
                      document.setText(oldText);
                      documentManager.commitDocument(document);
                    }
                  }
                });
                doRefactor(processor);
              }
            }, RefactoringBundle.message("changing.signature.of.0", UsageViewUtil.getDescriptiveName(info.getMethod())), null);
          }

          private void doRefactor(BaseRefactoringProcessor processor) {
            super.invokeRefactoring(processor);
          }
        };
      dialog.show();
      return dialog.isOK();
    }
    return false;

  }

  @Override
  public boolean isChangeSignatureAvailable(PsiElement element, ChangeInfo currentInfo) {
    if (currentInfo instanceof JavaChangeInfo) {
      return element instanceof PsiIdentifier && Comparing.equal(currentInfo.getMethod(), element.getParent());
    }
    return false;
  }

  @Nullable
  @Override
  public TextRange getHighlightingRange(PsiElement element) {
    element = element.getParent();
    if (element instanceof PsiMethod) {
      final PsiCodeBlock body = ((PsiMethod)element).getBody();
      return new TextRange(element.getTextRange().getStartOffset(), body == null ? element.getTextRange().getEndOffset() : body.getTextRange().getStartOffset() - 1);
    }
    return null;
  }

  @Override
  public boolean wasBanned(PsiElement element, @NotNull ChangeInfo bannedInfo) {
    final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    return method != null && isInsideMethodSignature(element, method) && Comparing.equal(method, bannedInfo.getMethod());
  }

  private static boolean isInsideMethodSignature(PsiElement element, @NotNull PsiMethod method) {
    final PsiCodeBlock body = method.getBody();
    if (body != null) {
      return element.getTextOffset() < body.getTextOffset() && element.getTextOffset() > method.getModifierList().getTextRange().getEndOffset();
    }
    return method.hasModifierProperty(PsiModifier.ABSTRACT);
  }
}
