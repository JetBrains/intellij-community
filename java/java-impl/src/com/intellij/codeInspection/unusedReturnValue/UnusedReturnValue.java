/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.util.IncorrectOperationException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class UnusedReturnValue extends GlobalJavaBatchInspectionTool{
  private MakeVoidQuickFix myQuickFix;

  public boolean IGNORE_BUILDER_PATTERN;

  @Override
  @Nullable
  public CommonProblemDescriptor[] checkElement(@NotNull RefEntity refEntity,
                                                @NotNull AnalysisScope scope,
                                                @NotNull InspectionManager manager,
                                                @NotNull GlobalInspectionContext globalContext,
                                                @NotNull ProblemDescriptionsProcessor processor) {
    if (refEntity instanceof RefMethod) {
      final RefMethod refMethod = (RefMethod)refEntity;

      if (refMethod.isConstructor()) return null;
      if (!refMethod.getSuperMethods().isEmpty()) return null;
      if (refMethod.getInReferences().size() == 0) return null;

      if (!refMethod.isReturnValueUsed()) {
        final PsiMethod psiMethod = (PsiMethod)refMethod.getElement();
        if (IGNORE_BUILDER_PATTERN && PropertyUtil.isSimplePropertySetter(psiMethod)) return null;

        final boolean isNative = psiMethod.hasModifierProperty(PsiModifier.NATIVE);
        if (refMethod.isExternalOverride() && !isNative) return null;
        return new ProblemDescriptor[]{manager.createProblemDescriptor(psiMethod.getNavigationElement(),
                                                                       InspectionsBundle
                                                                         .message("inspection.unused.return.value.problem.descriptor"),
                                                                       !isNative ? getFix(processor) : null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                                       false)};
      }
    }

    return null;
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    if (IGNORE_BUILDER_PATTERN) {
      super.writeSettings(node);
    }
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel("Ignore simple setters", this, "IGNORE_BUILDER_PATTERN");
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
    return InspectionsBundle.message("inspection.unused.return.value.display.name");
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @Override
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

  @Override
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

    @Override
    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.unused.return.value.make.void.quickfix");
    }

    @Override
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

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
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
        final List<PsiReturnStatement> returnStatements = new ArrayList<>();
        body.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitReturnStatement(final PsiReturnStatement statement) {
            super.visitReturnStatement(statement);
            returnStatements.add(statement);
          }

          @Override
          public void visitClass(PsiClass aClass) {}

          @Override
          public void visitLambdaExpression(PsiLambdaExpression expression) {}
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
