// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.changeSignature;

import com.intellij.codeInsight.daemon.impl.quickfix.DefineParamsDefaultValueAction;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.inplace.InplaceChangeSignature;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static com.intellij.refactoring.changeSignature.ParameterInfo.NEW_PARAMETER;

class DetectedJavaChangeInfo extends JavaChangeInfoImpl {
  private PsiMethod mySuperMethod;
  private final String[] myModifiers;

  DetectedJavaChangeInfo(@PsiModifier.ModifierConstant @NotNull String newVisibility,
                         PsiMethod method,
                         CanonicalTypes.Type newType,
                         ParameterInfoImpl @NotNull [] newParms,
                         ThrownExceptionInfo[] newExceptions,
                         String newName, @NotNull String oldName, final boolean delegate) {
    super(newVisibility, method, newName, newType, newParms, newExceptions, delegate, new HashSet<>(), new HashSet<>(), oldName);
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
  static DetectedJavaChangeInfo createFromMethod(PsiMethod method, final boolean delegate) {
    final String newVisibility = VisibilityUtil.getVisibilityModifier(method.getModifierList());
    final PsiType returnType = method.getReturnType();
    final CanonicalTypes.Type newReturnType = returnType != null ? CanonicalTypes.createTypeWrapper(returnType) : null;
    final ParameterInfoImpl[] parameterInfos = ParameterInfoImpl.fromMethod(method);
    for (ParameterInfoImpl parameterInfo : parameterInfos) {
      if (!parameterInfo.getTypeWrapper().isValid()) {
        return null;
      }
    }
    final DetectedJavaChangeInfo fromMethod = new DetectedJavaChangeInfo(newVisibility, method, newReturnType, parameterInfos, null, method.getName(), method.getName(), delegate);
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
  DetectedJavaChangeInfo createNextInfo(final PsiMethod method, boolean delegate) {
    final DetectedJavaChangeInfo fromMethod = createFromMethod(method, delegate);
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
          new DetectedJavaChangeInfo(fromMethod.getNewVisibility(), getMethod(), fromMethod.newReturnType, fromMethod.newParms, getNewExceptions(), method.getName(), getOldName(), delegate) {
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
                                                                                        method.getName(), isGenerateDelegate()) {
      @Override
      protected void fillOldParams(PsiMethod method) {
        super.fillOldParams(method);
        oldParameterNames = DetectedJavaChangeInfo.this.getOldParameterNames();
        oldParameterTypes = DetectedJavaChangeInfo.this.getOldParameterTypes();
      }
    }) {
      @Override
      protected void performRefactoring(UsageInfo @NotNull [] usages) {
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
        parameterInfos[i] = ParameterInfoImpl.create(oldParameter.getOldIndex())
          .withName(oldParameter.getName())
          .withType(oldParameter.getTypeWrapper())
          .withDefaultValue(null);
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
        parameterInfos[i] = ParameterInfoImpl.create(oldParameter != null ? oldParameter.getOldIndex() : NEW_PARAMETER)
          .withName(parameterInfo.getName())
          .withType(typeWrapper)
          .withDefaultValue(null);
      }
    }
    return true;
  }

  void perform(final String oldText, Editor editor, boolean silently) {
    final PsiMethod method = getSuperMethod();

    Project project = getMethod().getProject();
    final PsiMethod currentMethod = getMethod();
    final TextRange signatureRange = JavaChangeSignatureDetector.getSignatureRange(currentMethod);
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    final Document document = documentManager.getDocument(currentMethod.getContainingFile());
    if (silently || ApplicationManager.getApplication().isUnitTestMode()) {
      final String currentSignature = signatureRange.substring(currentMethod.getContainingFile().getText());
      InplaceChangeSignature.temporallyRevertChanges(JavaChangeSignatureDetector.getSignatureRange(currentMethod), document, oldText, project);
      PsiMethod prototype;
      if (isGenerateDelegate()) {
        for (JavaParameterInfo info : getNewParameters()) {
          if (info.isNew()) {
            ((ParameterInfoImpl)info).setDefaultValue("null"); //to be replaced with template expr
          }
        }
        prototype = JavaChangeSignatureUsageProcessor.generateDelegatePrototype(this);
      }
      else {
        prototype = null;
      }
      createChangeSignatureProcessor(method).run();
      InplaceChangeSignature.temporallyRevertChanges(JavaChangeSignatureDetector.getSignatureRange(currentMethod), document, currentSignature, project);
      if (prototype != null) {
        WriteCommandAction.runWriteCommandAction(project, JavaBundle.message("command.name.delegate.detected.change"), null, () -> {
          PsiMethod delegate = currentMethod.getContainingClass().findMethodBySignature(prototype, false);
          PsiExpression expression = delegate != null ? LambdaUtil.extractSingleExpressionFromBody(delegate.getBody()) : null;
          if (expression instanceof PsiMethodCallExpression) {

            PsiExpression[] expressions = ((PsiMethodCallExpression)expression).getArgumentList().getExpressions();
            JavaParameterInfo[] parameters = getNewParameters();
            PsiExpression[] toBeDefault =
              Arrays.stream(parameters)
                .filter(ParameterInfo::isNew)
                .map(info -> {
                  int i = ArrayUtil.find(parameters, info);
                  return expressions[i];
                }).toArray(PsiExpression[]::new);
            DefineParamsDefaultValueAction.startTemplate(project, editor, toBeDefault, delegate);
          }
        });
      }
      return;
    }
    final JavaMethodDescriptor descriptor = new JavaMethodDescriptor(currentMethod) {
      @Override
      public String getReturnTypeText() {
        return getNewReturnType().getTypeText();
      }
    };
    final JavaChangeSignatureDialog dialog =
      new JavaChangeSignatureDialog(method.getProject(), descriptor, true, method) {
        @Override
        protected BaseRefactoringProcessor createRefactoringProcessor() {
          return createChangeSignatureProcessor(method);
        }

        @Override
        protected void invokeRefactoring(final BaseRefactoringProcessor processor) {
          CommandProcessor.getInstance().executeCommand(myProject, () -> {
            InplaceChangeSignature.temporallyRevertChanges(JavaChangeSignatureDetector.getSignatureRange(currentMethod), document, oldText, project);
            doRefactor(processor);
          }, RefactoringBundle.message("changeSignature.refactoring.name"), null);
        }

        private void doRefactor(BaseRefactoringProcessor processor) {
          super.invokeRefactoring(processor);
        }
      };
    dialog.showAndGet();
  }
}
