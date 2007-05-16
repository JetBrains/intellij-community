package com.intellij.codeInspection.sameParameterValue;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.ProblemDescriptorImpl;
import com.intellij.codeInspection.reference.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfo;
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
public class SameParameterValueInspection extends GlobalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#" + SameParameterValueInspection.class.getName());

  @Nullable
  public CommonProblemDescriptor[] checkElement(RefEntity refEntity, AnalysisScope scope, InspectionManager manager, GlobalInspectionContext globalContext,
                                                ProblemDescriptionsProcessor processor) {
    ArrayList<ProblemDescriptor> problems = null;
    if (refEntity instanceof RefMethod) {
      final RefMethod refMethod = (RefMethod)refEntity;

      if (refMethod.hasSuperMethods()) return null;

      if (refMethod.isEntry()) return null;

      RefParameter[] parameters = refMethod.getParameters();
      for (RefParameter refParameter : parameters) {
        String value = refParameter.getActualValueIfSame();
        if (value != null) {
          if (problems == null) problems = new ArrayList<ProblemDescriptor>(1);
          problems.add(manager.createProblemDescriptor(refParameter.getElement(), InspectionsBundle.message(
            "inspection.same.parameter.problem.descriptor", "<code>" + refParameter.getName() + "</code>", "<code>" + value + "</code>"),
                                                       new InlineParameterValueFix(value),
                                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
        }
      }
    }

    return problems == null ? null : problems.toArray(new ProblemDescriptorImpl[problems.size()]);
  }


  public boolean queryExternalUsagesRequests(final InspectionManager manager,
                                             final GlobalInspectionContext globalContext,
                                             final ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    globalContext.getRefManager().iterate(new RefVisitor() {
      public void visitElement(RefEntity refEntity) {
        if (refEntity instanceof RefElement && problemDescriptionsProcessor.getDescriptions(refEntity) != null) {
          refEntity.accept(new RefVisitor() {
            public void visitMethod(final RefMethod refMethod) {
              globalContext.enqueueMethodUsagesProcessor(refMethod, new GlobalInspectionContextImpl.UsagesProcessor() {
                public boolean process(PsiReference psiReference) {
                  problemDescriptionsProcessor.ignoreElement(refMethod);
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
    return InspectionsBundle.message("inspection.same.parameter.display.name");
  }

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @NotNull
  public String getShortName() {
    return "SameParameterValue";
  }

  @Nullable
  public QuickFix getQuickFix(final String hint) {
    return new InlineParameterValueFix(hint);
  }

  @Nullable
  public String getHint(final QuickFix fix) {
    return ((InlineParameterValueFix)fix).getValue();
  }

  private class InlineParameterValueFix implements LocalQuickFix {
    private String myValue;

    public InlineParameterValueFix(final String value) {
      myValue = value;
    }

    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.same.parameter.fix.name", myValue);
    }

    @NotNull
    public String getFamilyName() {
      return getName();
    }

    public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiParameter parameter = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiParameter.class, false);
      LOG.assertTrue(parameter != null);
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, parameter)) return;

      final PsiExpression defToInline;
      try {
        defToInline = PsiManager.getInstance(project).getElementFactory().createExpressionFromText(myValue, parameter);
      }
      catch (IncorrectOperationException e) {
        return;
      }

      final PsiMethod method = PsiTreeUtil.getParentOfType(parameter, PsiMethod.class);

      LOG.assertTrue(method != null);
      final Collection<PsiReference> refsToInline = ReferencesSearch.search(parameter).findAll();
      final Runnable runnable = new Runnable() {
        public void run() {
          try {
            PsiExpression[] exprs = new PsiExpression[refsToInline.size()];
            int idx = 0;
            for (PsiReference reference : refsToInline) {
              PsiJavaCodeReferenceElement refElement = (PsiJavaCodeReferenceElement)reference;
              exprs[idx++] = InlineUtil.inlineVariable(parameter, defToInline, refElement);
            }

            for (final PsiExpression expr : exprs) {
              InlineUtil.tryToInlineArrayCreationForVarargs(expr);
            }

            final List<ParameterInfo> psiParameters = new ArrayList<ParameterInfo>();
            final PsiParameter[] parameters = method.getParameterList().getParameters();
            int paramIdx = 0;
            final String paramName = parameter.getName();
            for (PsiParameter param : parameters) {
              if (!Comparing.strEqual(paramName, param.getName())) {
                psiParameters.add(new ParameterInfo(paramIdx, param.getName(), param.getType()));
              }
              paramIdx++;
            }

            new ChangeSignatureProcessor(project, method, false, null, method.getName(), method.getReturnType(),
                                         psiParameters.toArray(new ParameterInfo[psiParameters.size()])).run();
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      };

      ApplicationManager.getApplication().runWriteAction(runnable);
    }

    public String getValue() {
      return myValue;
    }
  }
}
