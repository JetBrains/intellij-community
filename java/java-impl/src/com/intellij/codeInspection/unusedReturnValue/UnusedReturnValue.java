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
package com.intellij.codeInspection.unusedReturnValue;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class UnusedReturnValue extends GlobalJavaInspectionTool{
  private MakeVoidQuickFix myQuickFix;

  @Nullable
  public CommonProblemDescriptor[] checkElement(RefEntity refEntity,
                                                AnalysisScope scope,
                                                InspectionManager manager,
                                                GlobalInspectionContext globalContext,
                                                ProblemDescriptionsProcessor processor) {
    if (refEntity instanceof RefMethod) {
      final RefMethod refMethod = (RefMethod)refEntity;

      if (refMethod.isConstructor()) return null;
      if (refMethod.hasSuperMethods()) return null;
      if (refMethod.getInReferences().size() == 0) return null;

      if (!refMethod.isReturnValueUsed()) {
        return new ProblemDescriptor[]{manager.createProblemDescriptor(refMethod.getElement().getNavigationElement(),
                                                                       InspectionsBundle.message("inspection.unused.return.value.problem.descriptor"),
                                                                       getFix(processor), ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                                       false)};
      }
    }

    return null;
  }


  protected boolean queryExternalUsagesRequests(final RefManager manager, final GlobalJavaInspectionContext globalContext,
                                                final ProblemDescriptionsProcessor processor) {
    manager.iterate(new RefJavaVisitor() {
      @Override public void visitElement(RefEntity refEntity) {
        if (refEntity instanceof RefElement && processor.getDescriptions(refEntity) != null) {
          refEntity.accept(new RefJavaVisitor() {
            @Override public void visitMethod(final RefMethod refMethod) {
              globalContext.enqueueMethodUsagesProcessor(refMethod, new GlobalJavaInspectionContext.UsagesProcessor() {
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

  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.unused.return.value.display.name");
  }

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @NotNull
  public String getShortName() {
    return "UnusedReturnValue";
  }

  private LocalQuickFix getFix(final ProblemDescriptionsProcessor processor) {
    if (myQuickFix == null) {
      myQuickFix = new MakeVoidQuickFix(processor);
    }
    return myQuickFix;
  }

  @Nullable
  public QuickFix getQuickFix(String hint) {
    return getFix(null);
  }

  private static class MakeVoidQuickFix implements LocalQuickFix {
    private final ProblemDescriptionsProcessor myProcessor;
    private static final Logger LOG = Logger.getInstance("#" + MakeVoidQuickFix.class.getName());

    public MakeVoidQuickFix(final ProblemDescriptionsProcessor processor) {
      myProcessor = processor;
    }

    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.unused.return.value.make.void.quickfix");
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiMethod psiMethod = null;
      if (myProcessor != null) {
        RefElement refElement = (RefElement)myProcessor.getElement(descriptor);
        if (refElement.isValid() && refElement instanceof RefMethod) {
          RefMethod refMethod = (RefMethod)refElement;
          psiMethod = (PsiMethod) refMethod.getElement();
        }
      } else {
        psiMethod = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class);
      }
      if (psiMethod == null) return;
      makeMethodHierarchyVoid(project, psiMethod);
    }

    @NotNull
    public String getFamilyName() {
      return getName();
    }

    private static void makeMethodHierarchyVoid(Project project, @NotNull PsiMethod psiMethod) {
      replaceReturnStatements(psiMethod);
      for (final PsiMethod oMethod : OverridingMethodsSearch.search(psiMethod)) {
        replaceReturnStatements(oMethod);
      }
      final PsiParameter[] params = psiMethod.getParameterList().getParameters();
      final ParameterInfoImpl[] infos = new ParameterInfoImpl[params.length];
      for (int i = 0; i < params.length; i++) {
        PsiParameter param = params[i];
        infos[i] = new ParameterInfoImpl(i, param.getName(), param.getType());
      }

      final ChangeSignatureProcessor csp = new ChangeSignatureProcessor(project,
                                                                  psiMethod,
                                                                  false, null, psiMethod.getName(),
                                                                  PsiType.VOID,
                                                                  infos);

      csp.run();
    }

    private static void replaceReturnStatements(@NotNull final PsiMethod method) {
      final PsiCodeBlock body = method.getBody();
      if (body != null) {
        final List<PsiReturnStatement> returnStatements = new ArrayList<PsiReturnStatement>();
        body.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitReturnStatement(final PsiReturnStatement statement) {
            super.visitReturnStatement(statement);
            returnStatements.add(statement);
          }
        });
        final PsiStatement[] psiStatements = body.getStatements();
        final PsiStatement lastStatement = psiStatements[psiStatements.length - 1];
        for (PsiReturnStatement returnStatement : returnStatements) {
          try {
            final PsiExpression expression = returnStatement.getReturnValue();
            if (expression instanceof PsiLiteralExpression || expression instanceof PsiThisExpression) {    //avoid side effects
              if (returnStatement == lastStatement) {
                returnStatement.delete();
              }
              else {
                returnStatement
                  .replace(JavaPsiFacade.getInstance(method.getProject()).getElementFactory().createStatementFromText("return;", returnStatement));
              }
            }
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
    }
  }
}
