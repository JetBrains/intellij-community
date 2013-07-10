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
package com.intellij.codeInspection.sameParameterValue;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author max
 */
public class SameParameterValueInspection extends GlobalJavaBatchInspectionTool {
  private static final Logger LOG = Logger.getInstance("#" + SameParameterValueInspection.class.getName());

  @Override
  @Nullable
  public CommonProblemDescriptor[] checkElement(@NotNull RefEntity refEntity,
                                                @NotNull AnalysisScope scope,
                                                @NotNull InspectionManager manager,
                                                @NotNull GlobalInspectionContext globalContext,
                                                @NotNull ProblemDescriptionsProcessor processor) {
    List<ProblemDescriptor> problems = null;
    if (refEntity instanceof RefMethod) {
      final RefMethod refMethod = (RefMethod)refEntity;

      if (refMethod.hasSuperMethods()) return null;

      if (refMethod.isEntry()) return null;

      RefParameter[] parameters = refMethod.getParameters();
      for (RefParameter refParameter : parameters) {
        String value = refParameter.getActualValueIfSame();
        if (value != null) {
          if (problems == null) problems = new ArrayList<ProblemDescriptor>(1);
          final String paramName = refParameter.getName();
          problems.add(manager.createProblemDescriptor(refParameter.getElement(), InspectionsBundle.message(
            "inspection.same.parameter.problem.descriptor", "<code>" + paramName + "</code>", "<code>" + value + "</code>"),
                                                       new InlineParameterValueFix(paramName, value),
                                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false));
        }
      }
    }

    return problems == null ? null : problems.toArray(new CommonProblemDescriptor[problems.size()]);
  }


  @Override
  protected boolean queryExternalUsagesRequests(@NotNull final RefManager manager, @NotNull final GlobalJavaInspectionContext globalContext,
                                                @NotNull final ProblemDescriptionsProcessor processor) {
    manager.iterate(new RefJavaVisitor() {
      @Override public void visitElement(@NotNull RefEntity refEntity) {
        if (refEntity instanceof RefElement && processor.getDescriptions(refEntity) != null) {
          refEntity.accept(new RefJavaVisitor() {
            @Override public void visitMethod(@NotNull final RefMethod refMethod) {
              globalContext.enqueueMethodUsagesProcessor(refMethod, new GlobalJavaInspectionContext.UsagesProcessor() {
                @Override
                public boolean process(PsiReference psiReference) {
                  processor.ignoreElement(refMethod);
                  return false;
                }
              });
            }
          });
        }
      }
    });

    return false;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.same.parameter.display.name");
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @Override
  @NotNull
  public String getShortName() {
    return "SameParameterValue";
  }

  @Override
  @Nullable
  public QuickFix getQuickFix(final String hint) {
    if (hint == null) return null;
    final int spaceIdx = hint.indexOf(' ');
    if (spaceIdx == -1 || spaceIdx >= hint.length() - 1) return null; //invalid hint
    final String paramName = hint.substring(0, spaceIdx);
    final String value = hint.substring(spaceIdx + 1);
    return new InlineParameterValueFix(paramName, value);
  }

  @Override
  @Nullable
  public String getHint(@NotNull final QuickFix fix) {
    final InlineParameterValueFix valueFix = (InlineParameterValueFix)fix;
    return valueFix.getParamName() + " " + valueFix.getValue();
  }

  public static class InlineParameterValueFix implements LocalQuickFix {
    private final String myValue;
    private final String myParameterName;

    public InlineParameterValueFix(final String parameterName, final String value) {
      myValue = value;
      myParameterName = parameterName;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.same.parameter.fix.name", myParameterName, myValue);
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      LOG.assertTrue(method != null);
      PsiParameter parameter = PsiTreeUtil.getParentOfType(element, PsiParameter.class, false);
      if (parameter == null) {
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        for (PsiParameter psiParameter : parameters) {
          if (Comparing.strEqual(psiParameter.getName(), myParameterName)) {
            parameter = psiParameter;
            break;
          }
        }
      }
      if (parameter == null) return;
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, parameter)) return;

      final PsiExpression defToInline;
      try {
        defToInline = JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText(myValue, parameter);
      }
      catch (IncorrectOperationException e) {
        return;
      }
      final PsiParameter parameterToInline = parameter;
      inlineSameParameterValue(method, parameterToInline, defToInline);
    }

    public static void inlineSameParameterValue(final PsiMethod method, final PsiParameter parameter, final PsiExpression defToInline) {
      final Collection<PsiReference> refsToInline = ReferencesSearch.search(parameter).findAll();

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          try {
            PsiExpression[] exprs = new PsiExpression[refsToInline.size()];
            int idx = 0;
            for (PsiReference reference : refsToInline) {
              if (reference instanceof PsiJavaCodeReferenceElement) {
                exprs[idx++] = InlineUtil.inlineVariable(parameter, defToInline, (PsiJavaCodeReferenceElement)reference);
              }
            }

            for (final PsiExpression expr : exprs) {
              if (expr != null) InlineUtil.tryToInlineArrayCreationForVarargs(expr);
            }
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      });

      removeParameter(method, parameter);
    }

    public static void removeParameter(final PsiMethod method, final PsiParameter parameter) {
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      final List<ParameterInfoImpl> psiParameters = new ArrayList<ParameterInfoImpl>();
      int paramIdx = 0;
      final String paramName = parameter.getName();
      for (PsiParameter param : parameters) {
        if (!Comparing.strEqual(paramName, param.getName())) {
          psiParameters.add(new ParameterInfoImpl(paramIdx, param.getName(), param.getType()));
        }
        paramIdx++;
      }

      new ChangeSignatureProcessor(method.getProject(), method, false, null, method.getName(), method.getReturnType(),
                                   psiParameters.toArray(new ParameterInfoImpl[psiParameters.size()])).run();
    }

    public String getValue() {
      return myValue;
    }

    public String getParamName() {
      return myParameterName;
    }
  }
}
