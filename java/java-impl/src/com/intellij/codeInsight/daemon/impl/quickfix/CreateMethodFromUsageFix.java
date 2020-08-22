// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
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
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix.*;

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

  public static boolean hasErrorsInArgumentList(final PsiMethodCallExpression call) {
    Project project = call.getProject();
    PsiExpressionList argumentList = call.getArgumentList();
    for (PsiExpression expression : argumentList.getExpressions()) {
      PsiType type = expression.getType();
      if (type == null || PsiType.VOID.equals(type)) return true;
    }
    Document document = PsiDocumentManager.getInstance(project).getDocument(call.getContainingFile());
    if (document == null) return true;

    final TextRange argRange = argumentList.getTextRange();
    return !DaemonCodeAnalyzerEx.processHighlights(document, project, HighlightSeverity.ERROR,
                                                   //strictly inside arg list
                                                   argRange.getStartOffset() + 1,
                                                   argRange.getEndOffset() - 1,
                                                   info -> !(info.getActualStartOffset() > argRange.getStartOffset() &&
                                                             info.getActualEndOffset() < argRange.getEndOffset()));
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
    final Editor newEditor = positionCursor(project, targetFile, method);
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

    final PsiTypeVisitor<Boolean> visitor = new PsiTypeVisitor<Boolean>() {
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
