/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Mike
 */
public class CreateMethodFromUsageFix extends CreateFromUsageBaseFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CreateMethodFromUsageFix");

  private final SmartPsiElementPointer myMethodCall;

  public CreateMethodFromUsageFix(@NotNull PsiMethodCallExpression methodCall) {
    myMethodCall = SmartPointerManager.getInstance(methodCall.getProject()).createSmartPsiElementPointer(methodCall);
  }

  @Override
  protected boolean isAvailableImpl(int offset) {
    final PsiMethodCallExpression call = getMethodCall();
    if (call == null || !call.isValid()) return false;
    PsiReferenceExpression ref = call.getMethodExpression();
    String name = ref.getReferenceName();

    if (name == null || !PsiNameHelper.getInstance(ref.getProject()).isIdentifier(name)) return false;
    if (hasErrorsInArgumentList(call)) return false;
    setText(getDisplayString(name));
    return true;
  }

  protected String getDisplayString(String name) {
    return QuickFixBundle.message("create.method.from.usage.text", name);
  }

  private static boolean isMethodSignatureExists(PsiMethodCallExpression call, PsiClass target) {
    String name = call.getMethodExpression().getReferenceName();
    final JavaResolveResult resolveResult = call.getMethodExpression().advancedResolve(false);
    PsiExpressionList list = call.getArgumentList();
    PsiMethod[] methods = target.findMethodsByName(name, false);
    for (PsiMethod method : methods) {
      if (PsiUtil.isApplicable(method, resolveResult.getSubstitutor(), list)) return true;
    }
    return false;
  }

  static boolean hasErrorsInArgumentList(final PsiMethodCallExpression call) {
    Project project = call.getProject();
    Document document = PsiDocumentManager.getInstance(project).getDocument(call.getContainingFile());
    if (document == null) return true;

    PsiExpressionList argumentList = call.getArgumentList();
    final TextRange argRange = argumentList.getTextRange();
    return !DaemonCodeAnalyzerEx.processHighlights(document, project, HighlightSeverity.ERROR,
                                                   //strictly inside arg list
                                                   argRange.getStartOffset() + 1,
                                                   argRange.getEndOffset() - 1,
                                                   info -> !(info.getActualStartOffset() > argRange.getStartOffset() && info.getActualEndOffset() < argRange.getEndOffset()));
  }

  @Override
  protected PsiElement getElement() {
    final PsiMethodCallExpression call = getMethodCall();
    if (call == null || !call.getManager().isInProject(call)) return null;
    return call;
  }

  @Override
  @NotNull
  protected List<PsiClass> getTargetClasses(PsiElement element) {
    List<PsiClass> targets = super.getTargetClasses(element);
    ArrayList<PsiClass> result = new ArrayList<>();
    PsiMethodCallExpression call = getMethodCall();
    if (call == null) return Collections.emptyList();
    for (PsiClass target : targets) {
      if (shouldCreateStaticMember(call.getMethodExpression(), target)){
        if (target.isInterface() && !PsiUtil.isLanguageLevel8OrHigher(target)) continue;
        if (target.getContainingClass() != null && !target.hasModifierProperty(PsiModifier.STATIC)) continue;
      }
      if (!isMethodSignatureExists(call, target)) {
        result.add(target);
      }
    }
    return result;
  }

  @Override
  protected void invokeImpl(final PsiClass targetClass) {
    if (targetClass == null) return;
    PsiMethodCallExpression expression = getMethodCall();
    if (expression == null) return;
    PsiReferenceExpression ref = expression.getMethodExpression();

    if (isValidElement(expression)) return;

    PsiClass parentClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
    PsiMember enclosingContext = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, PsiField.class, PsiClassInitializer.class);

    String methodName = ref.getReferenceName();
    LOG.assertTrue(methodName != null);

    PsiMethod method = createMethod(targetClass, parentClass, enclosingContext, methodName);
    if (method == null) {
      return;
    }

    if (enclosingContext instanceof PsiMethod && methodName.equals(enclosingContext.getName()) &&
        PsiTreeUtil.isAncestor(targetClass, parentClass, true) && !ref.isQualified()) {
      RefactoringChangeUtil.qualifyReference(ref, method, null);
    }

    PsiCodeBlock body = method.getBody();
    assert body != null;
    final boolean shouldBeAbstract = shouldBeAbstract(expression.getMethodExpression(), targetClass);
    if (shouldBeAbstract) {
      body.delete();
      if (!targetClass.isInterface()) {
        method.getModifierList().setModifierProperty(PsiModifier.ABSTRACT, true);
      }
    }

    setupVisibility(parentClass, targetClass, method.getModifierList());

    expression = getMethodCall();
    LOG.assertTrue(expression.isValid());

    if ((!targetClass.isInterface() || PsiUtil.isLanguageLevel8OrHigher(targetClass)) && shouldCreateStaticMember(expression.getMethodExpression(), targetClass) && !shouldBeAbstract) {
      PsiUtil.setModifierProperty(method, PsiModifier.STATIC, true);
    }

    final PsiElement context = PsiTreeUtil.getParentOfType(expression, PsiClass.class, PsiMethod.class);

    PsiExpression[] arguments = expression.getArgumentList().getExpressions();
    doCreate(targetClass, method, shouldBeAbstract,
             ContainerUtil.map2List(arguments, Pair.<PsiExpression, PsiType>createFunction(null)),
             getTargetSubstitutor(expression),
             CreateFromUsageUtils.guessExpectedTypes(expression, true),
             context);
  }

  public static PsiMethod createMethod(PsiClass targetClass,
                                          PsiClass parentClass,
                                          PsiMember enclosingContext,
                                          String methodName) {
    JVMElementFactory factory = JVMElementFactories.getFactory(targetClass.getLanguage(), targetClass.getProject());
    if (factory == null) {
      return null;
    }

    PsiMethod method = factory.createMethod(methodName, PsiType.VOID);

    if (targetClass.equals(parentClass)) {
      method = (PsiMethod)targetClass.addAfter(method, enclosingContext);
    }
    else {
      PsiElement anchor = enclosingContext;
      while (anchor != null && anchor.getParent() != null && !anchor.getParent().equals(targetClass)) {
        anchor = anchor.getParent();
      }
      if (anchor != null && anchor.getParent() == null) anchor = null;
      if (anchor != null) {
        method = (PsiMethod)targetClass.addAfter(method, anchor);
      }
      else {
        method = (PsiMethod)targetClass.add(method);
      }
    }
    return method;
  }

  public static void doCreate(PsiClass targetClass, PsiMethod method, List<Pair<PsiExpression, PsiType>> arguments, PsiSubstitutor substitutor,
                              ExpectedTypeInfo[] expectedTypes, @Nullable PsiElement context) {
    doCreate(targetClass, method, shouldBeAbstractImpl(null, targetClass), arguments, substitutor, expectedTypes, context);
  }

  public static void doCreate(PsiClass targetClass,
                               PsiMethod method,
                               boolean shouldBeAbstract,
                               List<Pair<PsiExpression, PsiType>> arguments,
                               PsiSubstitutor substitutor,
                               ExpectedTypeInfo[] expectedTypes,
                               @Nullable final PsiElement context) {

    method = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(method);

    if (method == null) {
      return;
    }
    final Project project = targetClass.getProject();
    final PsiFile targetFile = targetClass.getContainingFile();
    Document document = PsiDocumentManager.getInstance(project).getDocument(targetFile);
    if (document == null) return;

    TemplateBuilderImpl builder = new TemplateBuilderImpl(method);

    CreateFromUsageUtils.setupMethodParameters(method, builder, context, substitutor, arguments);
    final PsiTypeElement returnTypeElement = method.getReturnTypeElement();
    if (returnTypeElement != null) {
      new GuessTypeParameters(JavaPsiFacade.getInstance(project).getElementFactory())
        .setupTypeElement(returnTypeElement, expectedTypes, substitutor, builder, context, targetClass);
    }
    PsiCodeBlock body = method.getBody();
    builder.setEndVariableAfter(shouldBeAbstract || body == null ? method : body.getLBrace());
    method = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(method);
    if (method == null) return;

    RangeMarker rangeMarker = document.createRangeMarker(method.getTextRange());
    final Editor newEditor = positionCursor(project, targetFile, method);
    if (newEditor == null) return;
    Template template = builder.buildTemplate();
    newEditor.getCaretModel().moveToOffset(rangeMarker.getStartOffset());
    newEditor.getDocument().deleteString(rangeMarker.getStartOffset(), rangeMarker.getEndOffset());
    rangeMarker.dispose();

    if (!shouldBeAbstract) {
      startTemplate(newEditor, template, project, new TemplateEditingAdapter() {
        @Override
        public void templateFinished(Template template, boolean brokenOff) {
          if (brokenOff) return;
          WriteCommandAction.runWriteCommandAction(project, () -> {
            PsiDocumentManager.getInstance(project).commitDocument(newEditor.getDocument());
            final int offset = newEditor.getCaretModel().getOffset();
            PsiMethod method1 = PsiTreeUtil.findElementOfClassAtOffset(targetFile, offset - 1, PsiMethod.class, false);
            if (method1 != null) {
              try {
                CreateFromUsageUtils.setupMethodBody(method1);
              }
              catch (IncorrectOperationException e) {
                LOG.error(e);
              }

              CreateFromUsageUtils.setupEditor(method1, newEditor);
            }
          });
        }
      });
    }
    else {
      startTemplate(newEditor, template, project);
    }
  }

  public static boolean checkTypeParam(final PsiMethod method, final PsiTypeParameter typeParameter) {
    final String typeParameterName = typeParameter.getName();

    final PsiTypeVisitor<Boolean> visitor = new PsiTypeVisitor<Boolean>() {
      @Override
      public Boolean visitClassType(PsiClassType classType) {
        final PsiClass psiClass = classType.resolve();
        if (psiClass instanceof PsiTypeParameter &&
            PsiTreeUtil.isAncestor(((PsiTypeParameter)psiClass).getOwner(), method, true)) {
          return false;
        }
        if (Comparing.strEqual(typeParameterName, classType.getCanonicalText())) {
          return true;
        }
        for (PsiType p : classType.getParameters()) {
          if (p.accept(this)) return true;
        }
        return false;
      }

      @Override
      public Boolean visitPrimitiveType(PsiPrimitiveType primitiveType) {
        return false;
      }

      @Override
      public Boolean visitArrayType(PsiArrayType arrayType) {
        return arrayType.getComponentType().accept(this);
      }

      @Override
      public Boolean visitWildcardType(PsiWildcardType wildcardType) {
        final PsiType bound = wildcardType.getBound();
        if (bound != null) {
          return bound.accept(this);
        }
        return false;
      }
    };

    final PsiTypeElement rElement = method.getReturnTypeElement();
    if (rElement != null) {
      if (rElement.getType().accept(visitor)) return true;
    }


    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      final PsiTypeElement element = parameter.getTypeElement();
      if (element != null) {
        if (element.getType().accept(visitor)) return true;
      }
    }
    return false;
  }

  protected boolean shouldBeAbstract(PsiReferenceExpression expression, PsiClass targetClass) {
    return shouldBeAbstractImpl(expression, targetClass);
  }

  private static boolean shouldBeAbstractImpl(PsiReferenceExpression expression, PsiClass targetClass) {
    return targetClass.isInterface() && (expression == null || !shouldCreateStaticMember(expression, targetClass));
  }

  @Override
  protected boolean isValidElement(PsiElement element) {
    PsiMethodCallExpression callExpression = (PsiMethodCallExpression) element;
    PsiReferenceExpression referenceExpression = callExpression.getMethodExpression();

    return CreateFromUsageUtils.isValidMethodReference(referenceExpression, callExpression);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.method.from.usage.family");
  }

  @Nullable
  protected PsiMethodCallExpression getMethodCall() {
    return (PsiMethodCallExpression)myMethodCall.getElement();
  }
}
