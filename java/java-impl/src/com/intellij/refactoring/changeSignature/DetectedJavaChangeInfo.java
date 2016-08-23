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

import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * User: anna
 * Date: 11/3/11
 */
class DetectedJavaChangeInfo extends JavaChangeInfoImpl {
  private PsiMethod mySuperMethod;
  private final String[] myModifiers;

  DetectedJavaChangeInfo(@PsiModifier.ModifierConstant String newVisibility,
                         PsiMethod method,
                         CanonicalTypes.Type newType,
                         @NotNull ParameterInfoImpl[] newParms,
                         ThrownExceptionInfo[] newExceptions,
                         String newName, String oldName) {
    super(newVisibility, method, newName, newType, newParms, newExceptions, false, new HashSet<>(), new HashSet<>(), oldName);
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    myModifiers = new String[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      final PsiModifierList modifierList = parameter.getModifierList();
      if (modifierList != null) {
        final String text = modifierList.getText();
        myModifiers[i] = text;
      }
    }
  }

  @Nullable
  static DetectedJavaChangeInfo createFromMethod(PsiMethod method) {
    final String newVisibility = VisibilityUtil.getVisibilityModifier(method.getModifierList());
    final PsiType returnType = method.getReturnType();
    final CanonicalTypes.Type newReturnType;
    final ParameterInfoImpl[] parameterInfos;
    try {
      newReturnType = returnType != null ? CanonicalTypes.createTypeWrapper(returnType) : null;
      parameterInfos = ParameterInfoImpl.fromMethod(method);
      for (ParameterInfoImpl parameterInfo : parameterInfos) {
        if (!parameterInfo.getTypeWrapper().isValid()) {
          return null;
        }
      }

      if (PsiTreeUtil.findChildOfType(method.getParameterList(), PsiErrorElement.class) != null) {
        return null;
      }
    }
    catch (IncorrectOperationException e) {
      return null;
    }
    final DetectedJavaChangeInfo fromMethod = new DetectedJavaChangeInfo(newVisibility, method, newReturnType, parameterInfos, null, method.getName(), method.getName());
    final PsiMethod deepestSuperMethod = method.findDeepestSuperMethod();
    if (deepestSuperMethod != null) {
      if (!deepestSuperMethod.getManager().isInProject(deepestSuperMethod)) return null;
    }
    fromMethod.setSuperMethod(deepestSuperMethod);
    return fromMethod;
  }

  @Override
  protected void setupPropagationEnabled(PsiParameter[] parameters, ParameterInfoImpl[] newParams) {
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

  public String[] getModifiers() {
    return myModifiers;
  }

  @Override
  protected boolean checkMethodEquality() {
    return false;
  }

  @Nullable
  ChangeInfo createNextInfo(final PsiMethod method) {
    final DetectedJavaChangeInfo fromMethod = createFromMethod(method);
    if (fromMethod == null) return null;
    if (!this.equals(fromMethod)) {
      if (!createParametersInfo(fromMethod.newParms)) return null;
      if ((fromMethod.newReturnType != null && getNewReturnType() == null) ||
          (fromMethod.newReturnType == null && getNewReturnType() != null) ||
          (fromMethod.newReturnType != null && getNewReturnType() != null && !Comparing.strEqual(getNewReturnType().getTypeText(),
                                                                                                 fromMethod.newReturnType.getTypeText()))) {
        final String visibility = getNewVisibility();
        if (Comparing.strEqual(visibility, PsiModifier.PRIVATE) &&
            !isArrayToVarargs() &&
            !isExceptionSetOrOrderChanged() &&
            !isExceptionSetChanged() &&
            !isNameChanged() &&
            !isParameterSetOrOrderChanged() &&
            !isParameterNamesChanged() &&
            !isParameterTypesChanged()) {
          return null;
        }
      }

      try {
        final DetectedJavaChangeInfo javaChangeInfo =
          new DetectedJavaChangeInfo(newVisibility, method, fromMethod.newReturnType, fromMethod.newParms, getNewExceptions(), method.getName(), getOldName()) {
            @Override
            protected void fillOldParams(PsiMethod method) {
              oldParameterNames = DetectedJavaChangeInfo.this.getOldParameterNames();
              oldParameterTypes = DetectedJavaChangeInfo.this.getOldParameterTypes();
              if (!method.isConstructor()) {
                try {
                  isReturnTypeChanged = isReturnTypeChanged ||
                                        (DetectedJavaChangeInfo.this.getNewReturnType() != null
                                         ? !Comparing.strEqual(DetectedJavaChangeInfo.this.getNewReturnType().getTypeText(), newReturnType.getTypeText())
                                         : newReturnType != null);
                }
                catch (IncorrectOperationException e) {
                  isReturnTypeChanged = true;
                }
              }

              for (int i = 0, length = Math.min(newParms.length, oldParameterNames.length); i < length; i++) {
                ParameterInfoImpl parm = newParms[i];
                if (parm.getName().equals(oldParameterNames[i]) && parm.getTypeText().equals(oldParameterTypes[i])) {
                  parm.oldParameterIndex = i;
                }
              }
            }
          };
        javaChangeInfo.setSuperMethod(getSuperMethod());
        return javaChangeInfo;
      }
      catch (IncorrectOperationException e) {
        return null;
      }
    }
    return this;
  }

  ChangeSignatureProcessor createChangeSignatureProcessor(final PsiMethod method) {
    return new ChangeSignatureProcessor(method.getProject(), new DetectedJavaChangeInfo(getNewVisibility(), getSuperMethod(),
                                                                                        getNewReturnType(),
                                                                                        (ParameterInfoImpl[])getNewParameters(),
                                                                                        getNewExceptions(), getNewName(),
                                                                                        method.getName()) {
      @Override
      protected void fillOldParams(PsiMethod method) {
        super.fillOldParams(method);
        oldParameterNames = DetectedJavaChangeInfo.this.getOldParameterNames();
        oldParameterTypes = DetectedJavaChangeInfo.this.getOldParameterTypes();
      }
    }) {
      @Override
      protected void performRefactoring(@NotNull UsageInfo[] usages) {
        super.performRefactoring(usages);
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(method.getProject());
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        for (int i = 0; i < getModifiers().length; i++) {
          final String modifier = getModifiers()[i];
          final PsiModifierList modifierList = parameters[i].getModifierList();
          if (modifierList != null && !Comparing.strEqual(modifier, modifierList.getText())) {
            final PsiModifierList newModifierList =
              elementFactory.createParameterFromText((modifier.isEmpty() ? "" : modifier + " ") + "type name", method).getModifierList();
            if (newModifierList != null) {
              modifierList.replace(newModifierList);
            }
          }
        }
      }
    };
  }

  private boolean createParametersInfo(ParameterInfoImpl[] parameterInfos) {
    final JavaParameterInfo[] oldParameters = getNewParameters();
    final String[] oldParameterNames = getOldParameterNames();
    final String[] oldParameterTypes = getOldParameterTypes();
    final Map<JavaParameterInfo, Integer> untouchedParams = new HashMap<>();
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

      if (oldParameter != null) {
        parameterInfos[i] = new ParameterInfoImpl(oldParameter.getOldIndex(),
                                                  oldParameter.getName(),
                                                  oldParameter.getTypeWrapper(),
                                                  null);
        untouchedParams.put(parameterInfos[i], oldParameter.getOldIndex());
      }
    }

    for (int i = 0; i < parameterInfos.length; i++) {
      ParameterInfoImpl parameterInfo = parameterInfos[i];
      if (!untouchedParams.containsKey(parameterInfo)) {
        JavaParameterInfo oldParameter = null;
        if (oldParameters.length > i && oldParameterNames.length > i) {
          if (Comparing.strEqual(oldParameterNames[i], parameterInfo.getName()) ||
              Comparing.strEqual(oldParameterTypes[i], parameterInfo.getTypeText())) {
            if (!untouchedParams.containsValue(oldParameters[i].getOldIndex())) {
              oldParameter = oldParameters[i];
            }
          }
        }
        final CanonicalTypes.Type typeWrapper = parameterInfo.getTypeWrapper();
        if (!typeWrapper.isValid()) return false;
        parameterInfos[i] = new ParameterInfoImpl(oldParameter != null ? oldParameter.getOldIndex() : -1,
                                                  parameterInfo.getName(),
                                                  typeWrapper,
                                                  null);
      }
    }
    return true;
  }

  boolean perform(ChangeInfo initialChangeInfo, final String oldText, boolean silently) {
    final PsiMethod method = getSuperMethod();

    final PsiMethod currentMethod = (PsiMethod)initialChangeInfo.getMethod();
    if (silently || ApplicationManager.getApplication().isUnitTestMode()) {
      final TextRange signatureRange = JavaChangeSignatureDetector.getSignatureRange(currentMethod);
      final String currentSignature = currentMethod.getContainingFile().getText().substring(signatureRange.getStartOffset(),
                                                                                            signatureRange.getEndOffset());
      temporallyRevertChanges(currentMethod, oldText);
      createChangeSignatureProcessor(method).run();
      temporallyRevertChanges(currentMethod, currentSignature, JavaChangeSignatureDetector.getSignatureRange(currentMethod));
      return true;
    }
    final JavaMethodDescriptor descriptor = new JavaMethodDescriptor(currentMethod) {
      @Override
      public String getReturnTypeText() {
        return getNewReturnType().getTypeText();
      }
    };
    final JavaChangeSignatureDialog dialog =
      new JavaChangeSignatureDialog(method.getProject(), descriptor, true, method) {
        protected BaseRefactoringProcessor createRefactoringProcessor() {
          return createChangeSignatureProcessor(method);
        }

        @Override
        protected void invokeRefactoring(final BaseRefactoringProcessor processor) {
          CommandProcessor.getInstance().executeCommand(myProject, () -> {
            temporallyRevertChanges(method, oldText);
            doRefactor(processor);
          }, RefactoringBundle.message("changing.signature.of.0", DescriptiveNameUtil.getDescriptiveName(currentMethod)), null);
        }

        private void doRefactor(BaseRefactoringProcessor processor) {
          super.invokeRefactoring(processor);
        }
      };
    return dialog.showAndGet();
  }

  private static void temporallyRevertChanges(final PsiElement psiElement, final String oldText) {
    temporallyRevertChanges(psiElement, oldText, psiElement.getTextRange());
  }

  private static void temporallyRevertChanges(final PsiElement psiElement,
                                              final String oldText,
                                              final TextRange textRange) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      final PsiFile file = psiElement.getContainingFile();
      final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(psiElement.getProject());
      final Document document = documentManager.getDocument(file);
      if (document != null) {
        document.replaceString(textRange.getStartOffset(), textRange.getEndOffset(), oldText);
        documentManager.commitDocument(document);
      }
    });
  }
}
