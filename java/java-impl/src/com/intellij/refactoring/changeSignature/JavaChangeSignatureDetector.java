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

import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.java.JavaRefactoringSupportProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * User: anna
 * Date: Sep 6, 2010
 */
public class JavaChangeSignatureDetector implements LanguageChangeSignatureDetector {
  private static final Logger LOG = Logger.getInstance("#" + JavaChangeSignatureDetector.class.getName());

  @Override
  public ChangeInfo createCurrentChangeSignature(final @NotNull PsiElement element,
                                                 final @Nullable ChangeInfo changeInfo) {
    final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
    if (method != null && isInsideMethodSignature(element, method) && (changeInfo == null || changeInfo instanceof MyJavaChangeInfo)) {
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
      }
      catch (IncorrectOperationException e) {
        return null;
      }
      final MyJavaChangeInfo fromMethod = new MyJavaChangeInfo(newVisibility, method, newReturnType, parameterInfos, null, method.getName(), method.getName());
      if (changeInfo == null) { //before replacement
        final PsiMethod deepestSuperMethod = method.findDeepestSuperMethod();
        if (deepestSuperMethod != null) {
          if (!deepestSuperMethod.getManager().isInProject(deepestSuperMethod)) return null;
        }
        fromMethod.setSuperMethod(deepestSuperMethod);
        return fromMethod;
      } else {
        final MyJavaChangeInfo info = (MyJavaChangeInfo)changeInfo;
        if (!info.getMethod().equals(method)) return null;
        if (!info.equals(fromMethod)) {
          if (!createParametersInfo(element, parameterInfos, info)) return null;
          if (info.isReturnTypeChanged()) {
            final String visibility = info.getNewVisibility();
            if (Comparing.strEqual(visibility, PsiModifier.PRIVATE) &&
                !info.isArrayToVarargs() &&
                !info.isExceptionSetOrOrderChanged() &&
                !info.isExceptionSetChanged() &&
                !info.isNameChanged() &&
                !info.isParameterSetOrOrderChanged() &&
                !info.isParameterNamesChanged() &&
                !info.isParameterTypesChanged()) {
              return null;
            }
          }
          try {
            final MyJavaChangeInfo javaChangeInfo =
              new MyJavaChangeInfo(newVisibility, method, newReturnType, parameterInfos, info.getNewExceptions(), method.getName(), info.getOldName()) {
                @Override
                protected void fillOldParams(PsiMethod method) {
                  oldParameterNames = info.getOldParameterNames();
                  oldParameterTypes = info.getOldParameterTypes();
                  if (!method.isConstructor()) {
                    try {
                      isReturnTypeChanged = info.isReturnTypeChanged ||
                                            (info.getNewReturnType() != null
                                               ? !Comparing.strEqual(info.getNewReturnType().getTypeText(), newReturnType.getTypeText())
                                               : newReturnType != null);
                    }
                    catch (IncorrectOperationException e) {
                      isReturnTypeChanged = true;
                    }
                  }
                }
              };
            javaChangeInfo.setSuperMethod(info.getSuperMethod());
            return javaChangeInfo;
          }
          catch (IncorrectOperationException e) {
            return null;
          }
        }
        return changeInfo;
      }
    } else {
      final PsiVariable variable = PsiTreeUtil.getParentOfType(element, PsiVariable.class);
      if (variable != null && JavaRefactoringSupportProvider.mayRenameInplace(variable, element)) {
        return new RenameChangeInfo(variable, changeInfo) {
          @Override
          public Language getLanguage() {
            return StdLanguages.JAVA;
          }
        };
      }
    }
    return null;
  }

  private static boolean createParametersInfo(PsiElement element,
                                              ParameterInfoImpl[] parameterInfos,
                                              MyJavaChangeInfo info) {

    final JavaParameterInfo[] oldParameters = info.getNewParameters();
    final String[] oldParameterNames = info.getOldParameterNames();
    final String[] oldParameterTypes =  info.getOldParameterTypes();
    final Map<JavaParameterInfo, Integer> untouchedParams = new HashMap<JavaParameterInfo, Integer>();
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
                                                  oldParameter.getTypeWrapper().getType(element, element.getManager()),
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
        parameterInfos[i] = new ParameterInfoImpl(oldParameter != null ? oldParameter.getOldIndex() : - 1,
                                                  parameterInfo.getName(),
                                                  typeWrapper.getType(element, element.getManager()),
                                                  null);
      }
    }
    return true;
  }


  private static class MyJavaChangeInfo extends JavaChangeInfoImpl  {
    private PsiMethod mySuperMethod;
    private String[] myModifiers;
    private MyJavaChangeInfo(String newVisibility,
                             PsiMethod method,
                             CanonicalTypes.Type newType,
                             @NotNull ParameterInfoImpl[] newParms,
                             ThrownExceptionInfo[] newExceptions,
                             String newName, String oldName) {
      super(newVisibility, method, newName, newType, newParms, newExceptions, false,
            new HashSet<PsiMethod>(),
            new HashSet<PsiMethod>(), oldName);
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

    public String[] getModifiers() {
      return myModifiers;
    }
  }

  @Override
  public boolean accept(final ChangeInfo changeInfo, @NotNull final String oldText, boolean silently) {
    if (changeInfo instanceof MyJavaChangeInfo) {
      final MyJavaChangeInfo info = (MyJavaChangeInfo)changeInfo;
      final PsiMethod method = info.getSuperMethod();

      if (silently || ApplicationManager.getApplication().isUnitTestMode()) {
        temporallyRevertChanges(info.getMethod(), oldText);
        createChangeSignatureProcessor(info, method).run();
        return true;
      }
      final JavaChangeSignatureDialog dialog =
        new JavaChangeSignatureDialog(method.getProject(), new JavaMethodDescriptor(info.getMethod()) {
          @Override
          public String getReturnTypeText() {
            return info.getNewReturnType().getTypeText();
          }
        }, true, method) {
          protected BaseRefactoringProcessor createRefactoringProcessor() {
            return createChangeSignatureProcessor(info, method);
          }

          @Override
          protected void invokeRefactoring(final BaseRefactoringProcessor processor) {
            CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
              @Override
              public void run() {
                temporallyRevertChanges(method, oldText);
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
    } else if (changeInfo instanceof RenameChangeInfo) {
      ((RenameChangeInfo)changeInfo).perform();
      return true;
    }
    return false;

  }

  private static void temporallyRevertChanges(final PsiElement psiElement, final String oldText) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final PsiFile file = psiElement.getContainingFile();
        final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(psiElement.getProject());
        final Document document = documentManager.getDocument(file);
        if (document != null) {
          final TextRange textRange = psiElement.getTextRange();
          document.replaceString(textRange.getStartOffset(), textRange.getEndOffset(), oldText);
          documentManager.commitDocument(document);
        }
      }
    });
  }

  private static ChangeSignatureProcessor createChangeSignatureProcessor(final MyJavaChangeInfo info,
                                                                         final PsiMethod method) {
    return new ChangeSignatureProcessor(method.getProject(), new MyJavaChangeInfo(info.getNewVisibility(), info.getSuperMethod(),
                                                                           info.getNewReturnType(),
                                                                           (ParameterInfoImpl[])info.getNewParameters(),
                                                                           info.getNewExceptions(), info.getNewName(), method.getName()) {
      @Override
      protected void fillOldParams(PsiMethod method) {
        super.fillOldParams(method);
        oldParameterNames = info.getOldParameterNames();
        oldParameterTypes = info.getOldParameterTypes();
      }
    }) {
      @Override
      protected void performRefactoring(UsageInfo[] usages) {
        super.performRefactoring(usages);
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(method.getProject());
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        for (int i = 0; i < info.getModifiers().length; i++) {
          final String modifier = info.getModifiers()[i];
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

  @Override
  public boolean isChangeSignatureAvailable(PsiElement element, ChangeInfo currentInfo) {
    if (currentInfo instanceof JavaChangeInfo) {
      final PsiMethod method = (PsiMethod)currentInfo.getMethod();
      return getSignatureRange(method).contains(element.getTextRange());
    } else if (currentInfo instanceof RenameChangeInfo) {
      final PsiElement nameIdentifier = ((RenameChangeInfo)currentInfo).getNameIdentifier();
      return nameIdentifier != null && nameIdentifier.getTextRange().contains(element.getTextRange());
    }
    return false;
  }

  private static TextRange getSignatureRange(PsiMethod method) {
    final PsiCodeBlock body = method.getBody();
    return new TextRange(method.getModifierList().getTextRange().getStartOffset(), body == null ? method.getTextRange().getEndOffset() : body.getTextRange().getStartOffset() - 1);
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
      protected void run(Result result) throws Throwable {
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

  @Override
  public boolean ignoreChanges(PsiElement element) {
    return PsiTreeUtil.getParentOfType(element, PsiImportList.class) != null;
  }

  private static boolean isInsideMethodSignature(PsiElement element, @NotNull PsiMethod method) {
    final PsiCodeBlock body = method.getBody();
    if (body != null) {
      return element.getTextOffset() < body.getTextOffset() && element.getTextOffset() > method.getModifierList().getTextRange().getEndOffset();
    }
    return method.hasModifierProperty(PsiModifier.ABSTRACT);
  }
}
