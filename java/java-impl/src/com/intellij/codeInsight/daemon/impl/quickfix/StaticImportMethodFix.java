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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.JavaProjectCodeInsightSettings;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.HintAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.*;
import com.intellij.psi.util.proximity.PsiProximityComparator;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Processor;
import com.intellij.util.containers.LinkedMultiMap;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class StaticImportMethodFix implements IntentionAction, HintAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.StaticImportMethodFix");
  private final SmartPsiElementPointer<PsiMethodCallExpression> myMethodCall;
  private List<PsiMethod> candidates;

  public StaticImportMethodFix(@NotNull PsiMethodCallExpression methodCallExpression) {
    myMethodCall = SmartPointerManager.getInstance(methodCallExpression.getProject()).createSmartPsiElementPointer(methodCallExpression);
  }

  @Override
  @NotNull
  public String getText() {
    String text = QuickFixBundle.message("static.import.method.text");
    if (candidates != null && candidates.size() == 1) {
      text += " '" + getMethodPresentableText() + "'";
    }
    else {
      text += "...";
    }
    return text;
  }

  @NotNull
  private String getMethodPresentableText() {
    return PsiFormatUtil.formatMethod(candidates.get(0), PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME |
                                                                               PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
                                                                               PsiFormatUtilBase.SHOW_FQ_NAME, 0);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file)
           && file instanceof PsiJavaFile
           && myMethodCall.getElement() != null
           && myMethodCall.getElement().isValid()
           && myMethodCall.getElement().getMethodExpression().getQualifierExpression() == null
           && myMethodCall.getElement().resolveMethod() == null
           && file.getManager().isInProject(file)
           && !(candidates == null ? candidates = getMethodsToImport() : candidates).isEmpty()
      ;
  }

  private PsiType getExpectedType() {
    final PsiMethodCallExpression methodCall = myMethodCall.getElement();
    if (methodCall == null) return null;
    final PsiElement parent = PsiUtil.skipParenthesizedExprUp(methodCall.getParent());

    if (parent instanceof PsiVariable) {
      if (methodCall.equals(PsiUtil.skipParenthesizedExprDown(((PsiVariable)parent).getInitializer()))) {
        return ((PsiVariable)parent).getType();
      }
    }
    else if (parent instanceof PsiAssignmentExpression) {
      if (methodCall.equals(PsiUtil.skipParenthesizedExprDown(((PsiAssignmentExpression)parent).getRExpression()))) {
        return ((PsiAssignmentExpression)parent).getLExpression().getType();
      }
    }
    else if (parent instanceof PsiReturnStatement) {
      final PsiElement psiElement = PsiTreeUtil.getParentOfType(parent, PsiLambdaExpression.class, PsiMethod.class);
      if (psiElement instanceof PsiLambdaExpression) {
        return LambdaUtil.getFunctionalInterfaceReturnType(((PsiLambdaExpression)psiElement).getFunctionalInterfaceType());
      }
      else if (psiElement instanceof PsiMethod) {
        return ((PsiMethod)psiElement).getReturnType();
      }
    }
    else if (parent instanceof PsiExpressionList) {
      final PsiElement pParent = parent.getParent();
      if (pParent instanceof PsiCallExpression && parent.equals(((PsiCallExpression)pParent).getArgumentList())) {
        final JavaResolveResult resolveResult = ((PsiCallExpression)pParent).resolveMethodGenerics();
        final PsiElement psiElement = resolveResult.getElement();
        if (psiElement instanceof PsiMethod) {
          final PsiMethod psiMethod = (PsiMethod)psiElement;
          final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
          final int idx = ArrayUtilRt.find(((PsiExpressionList)parent).getExpressions(), PsiUtil.skipParenthesizedExprUp(methodCall));
          if (idx > -1 && parameters.length > 0) {
            PsiType parameterType = parameters[Math.min(idx, parameters.length - 1)].getType();
            if (idx >= parameters.length - 1) {
              final PsiParameter lastParameter = parameters[parameters.length - 1];
              if (lastParameter.isVarArgs()) {
                parameterType = ((PsiEllipsisType)lastParameter.getType()).getComponentType();
              }
            }
            return resolveResult.getSubstitutor().substitute(parameterType);
          }
          else {
            return null;
          }
        }
      }
    }
    else if (parent instanceof PsiLambdaExpression) {
      return LambdaUtil.getFunctionalInterfaceReturnType(((PsiLambdaExpression)parent).getFunctionalInterfaceType());
    }
    return null;
  }

  @NotNull
  private List<PsiMethod> getMethodsToImport() {
    final Project project = myMethodCall.getProject();
    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);
    final PsiMethodCallExpression element = myMethodCall.getElement();
    PsiReferenceExpression reference = element.getMethodExpression();
    final PsiExpressionList argumentList = element.getArgumentList();
    String name = reference.getReferenceName();
    final List<PsiMethod> list = new ArrayList<PsiMethod>();
    if (name == null) return list;
    GlobalSearchScope scope = element.getResolveScope();
    final Map<PsiClass, Boolean> possibleClasses = new HashMap<PsiClass, Boolean>();
    final PsiType expectedType = getExpectedType();
    final List<PsiMethod> applicableList = new ArrayList<PsiMethod>();
    final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(project).getResolveHelper();

    final MultiMap<PsiClass, PsiMethod> deprecated = new LinkedMultiMap<PsiClass, PsiMethod>();
    final MultiMap<PsiClass, PsiMethod> suggestions = new LinkedMultiMap<PsiClass, PsiMethod>();
    class RegisterMethodsProcessor {
      private void registerMethod(PsiClass containingClass, Collection<PsiMethod> methods) {
        final Boolean alreadyMentioned = possibleClasses.get(containingClass);
        if (alreadyMentioned == Boolean.TRUE) return;
        if (alreadyMentioned == null) {
          if (!methods.isEmpty()) {
            list.add(methods.iterator().next());
          }
          possibleClasses.put(containingClass, false);
        }
        for (PsiMethod method : methods) {
          if (!PsiUtil.isAccessible(project, method, element, containingClass)) {
            continue;
          }
          PsiSubstitutor substitutorForMethod = resolveHelper
            .inferTypeArguments(method.getTypeParameters(), method.getParameterList().getParameters(),
                                argumentList.getExpressions(),
                                PsiSubstitutor.EMPTY, element.getParent(), DefaultParameterTypeInferencePolicy.INSTANCE);
          if (PsiUtil.isApplicable(method, substitutorForMethod, argumentList)) {
            final PsiType returnType = substitutorForMethod.substitute(method.getReturnType());
            if (expectedType == null || returnType == null || TypeConversionUtil.isAssignable(expectedType, returnType)) {
              applicableList.add(method);
              possibleClasses.put(containingClass, true);
              break;
            }
          }
        }
      }
    }

    final RegisterMethodsProcessor registrar = new RegisterMethodsProcessor();
    cache.processMethodsWithName(name, scope, new Processor<PsiMethod>() {
      @Override
      public boolean process(PsiMethod method) {
        ProgressManager.checkCanceled();
        if (JavaCompletionUtil.isInExcludedPackage(method, false)
            || !method.hasModifierProperty(PsiModifier.STATIC)) return true;
        PsiFile file = method.getContainingFile();
        final PsiClass containingClass = method.getContainingClass();
        if (file instanceof PsiJavaFile
            //do not show methods from default package
            && !((PsiJavaFile)file).getPackageName().isEmpty()) {
          if (isEffectivelyDeprecated(method)) {
            deprecated.putValue(containingClass, method);
            return processCondition();
          }
          suggestions.putValue(containingClass, method);
        }
        return processCondition();
      }

      private boolean isEffectivelyDeprecated(PsiMethod method) {
        if (method.isDeprecated()) {
          return true;
        }
        PsiClass aClass = method.getContainingClass();
        while (aClass != null) {
          if (aClass.isDeprecated()) {
            return true;
          }
          aClass = aClass.getContainingClass();
        }
        return false;
      }

      private boolean processCondition() {
        return suggestions.size() + deprecated.size() < 50;
      }
    });

    for (Map.Entry<PsiClass, Collection<PsiMethod>> methodEntry : suggestions.entrySet()) {
      registrar.registerMethod(methodEntry.getKey(), methodEntry.getValue());
    }
    
    for (Map.Entry<PsiClass, Collection<PsiMethod>> deprecatedMethod : deprecated.entrySet()) {
      registrar.registerMethod(deprecatedMethod.getKey(), deprecatedMethod.getValue());
    }

    List<PsiMethod> result = applicableList.isEmpty() ? list : applicableList;
    for (int i = result.size() - 1; i >= 0; i--) {
      ProgressManager.checkCanceled();
      PsiMethod method = result.get(i);
      // check for manually excluded
      if (isExcluded(method)) {
        result.remove(i);
      }
    }
    Collections.sort(result, new PsiProximityComparator(argumentList));
    return result;
  }

  public static boolean isExcluded(PsiMember method) {
    String name = PsiUtil.getMemberQualifiedName(method);
    return name != null && JavaProjectCodeInsightSettings.getSettings(method.getProject()).isExcluded(name);
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file) {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final List<PsiMethod> methodsToImport = getMethodsToImport();
        if (methodsToImport.isEmpty()) return;
        createQuestionAction(methodsToImport, project, editor).execute();
      }
    });
  }

  @NotNull
  private StaticImportMethodQuestionAction createQuestionAction(List<PsiMethod> methodsToImport, @NotNull Project project, Editor editor) {
    return new StaticImportMethodQuestionAction(project, editor, methodsToImport, myMethodCall);
  }

  private ImportClassFixBase.Result doFix(Editor editor) {
    if (candidates.isEmpty()) {
      return ImportClassFixBase.Result.POPUP_NOT_SHOWN;
    }
    
    final StaticImportMethodQuestionAction action = createQuestionAction(candidates, myMethodCall.getProject(), editor);

    final PsiMethodCallExpression element = myMethodCall.getElement();
    if (element == null) {
      return ImportClassFixBase.Result.POPUP_NOT_SHOWN;
    }

    if (candidates.size() == 1 && ImportClassFixBase.canAddUnambiguousImport(element.getContainingFile())) {
      CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
        @Override
        public void run() {
          action.execute();
        }
      });
      return ImportClassFixBase.Result.CLASS_AUTO_IMPORTED;
    }

    String hintText = ShowAutoImportPass.getMessage(candidates.size() > 1, getMethodPresentableText());
    if (!ApplicationManager.getApplication().isUnitTestMode() && !HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(true)) {
      final TextRange textRange = element.getTextRange();
      HintManager.getInstance().showQuestionHint(editor, hintText,
                                                 textRange.getStartOffset(),
                                                 textRange.getEndOffset(), action);
    }
    return ImportClassFixBase.Result.POPUP_SHOWN;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public boolean showHint(@NotNull Editor editor) {
    final PsiMethodCallExpression callExpression = myMethodCall.getElement();
    if (callExpression == null || callExpression.getMethodExpression().getQualifierExpression() != null) {
      return false;
    }
    ImportClassFixBase.Result result = doFix(editor);
    return result == ImportClassFixBase.Result.POPUP_SHOWN || result == ImportClassFixBase.Result.CLASS_AUTO_IMPORTED;
  }
}
