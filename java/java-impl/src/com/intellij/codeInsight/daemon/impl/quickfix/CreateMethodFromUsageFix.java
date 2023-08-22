// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix.shouldCreateStaticMember;
import static com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix.startTemplate;

public final class CreateMethodFromUsageFix {
  private static final Logger LOG = Logger.getInstance(CreateMethodFromUsageFix.class);

  public static boolean isMethodSignatureExists(PsiMethodCallExpression call, PsiClass target) {
    String name = call.getMethodExpression().getReferenceName();
    final JavaResolveResult resolveResult = call.getMethodExpression().advancedResolve(false);
    PsiExpressionList list = call.getArgumentList();
    PsiMethod[] methods = target.findMethodsByName(name, false);
    for (PsiMethod method : methods) {
      if (PsiUtil.isApplicable(method, resolveResult.getSubstitutor(), list)) return true;
    }
    return false;
  }

  public static boolean hasVoidInArgumentList(final PsiMethodCallExpression call) {
    PsiExpressionList argumentList = call.getArgumentList();
    for (PsiExpression expression : argumentList.getExpressions()) {
      PsiType type = expression.getType();
      if (type == null || PsiTypes.voidType().equals(type)) return true;
    }
    return false;
  }

  /**
   * Creates a new void no-arg method, adds it into class and returns it
   * 
   * @param targetClass class to add method to
   * @param parentClass context class from where creation was invoked
   * @param enclosingContext context from where creation was invoked
   * @param methodName name of the new method
   * @return newly created method
   */
  public static PsiMethod createMethod(@NotNull PsiClass targetClass,
                                       @Nullable PsiClass parentClass,
                                       @Nullable PsiMember enclosingContext,
                                       @NotNull String methodName) {
    JVMElementFactory factory = JVMElementFactories.getFactory(targetClass.getLanguage(), targetClass.getProject());
    if (factory == null) {
      return null;
    }

    PsiMethod method = factory.createMethod(methodName, PsiTypes.voidType());

    return addMethod(targetClass, parentClass, enclosingContext, method);
  }

  public static @NotNull PsiMethod addMethod(@NotNull PsiClass targetClass, @Nullable PsiClass parentClass,
                                    @Nullable PsiMember enclosingContext, @NotNull PsiMethod method) {
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

  public static void doCreate(PsiClass targetClass, PsiMethod method, List<? extends Pair<PsiExpression, PsiType>> arguments, PsiSubstitutor substitutor,
                              ExpectedTypeInfo[] expectedTypes, @Nullable PsiElement context) {
    doCreate(targetClass, method, shouldBeAbstractImpl(null, targetClass), arguments, substitutor, expectedTypes, context);
  }

  public static void doCreate(PsiClass targetClass,
                              PsiMethod method,
                              boolean shouldBeAbstract,
                              List<? extends Pair<PsiExpression, PsiType>> arguments,
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
      new GuessTypeParameters(project, JavaPsiFacade.getElementFactory(project), builder, substitutor)
        .setupTypeElement(returnTypeElement, expectedTypes, context, targetClass);
    }
    PsiCodeBlock body = method.getBody();
    builder.setEndVariableAfter(shouldBeAbstract || body == null ? method : body.getLBrace());
    method = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(method);
    if (method == null) return;

    RangeMarker rangeMarker = document.createRangeMarker(method.getTextRange());
    final Editor newEditor = CodeInsightUtil.positionCursor(project, targetFile, method);
    if (newEditor == null) return;
    Template template = builder.buildTemplate();
    newEditor.getCaretModel().moveToOffset(rangeMarker.getStartOffset());
    newEditor.getDocument().deleteString(rangeMarker.getStartOffset(), rangeMarker.getEndOffset());
    rangeMarker.dispose();

    if (!shouldBeAbstract) {
      startTemplate(newEditor, template, project, new TemplateEditingAdapter() {
        @Override
        public void templateFinished(@NotNull Template template, boolean brokenOff) {
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

    final PsiTypeVisitor<Boolean> visitor = new PsiTypeVisitor<>() {
      @Override
      public Boolean visitClassType(@NotNull PsiClassType classType) {
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
      public Boolean visitPrimitiveType(@NotNull PsiPrimitiveType primitiveType) {
        return false;
      }

      @Override
      public Boolean visitArrayType(@NotNull PsiArrayType arrayType) {
        return arrayType.getComponentType().accept(this);
      }

      @Override
      public Boolean visitWildcardType(@NotNull PsiWildcardType wildcardType) {
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

  private static boolean shouldBeAbstractImpl(PsiReferenceExpression expression, PsiClass targetClass) {
    return targetClass.isInterface() && (expression == null || !shouldCreateStaticMember(expression, targetClass));
  }
}
