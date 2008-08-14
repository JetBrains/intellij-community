package com.intellij.codeInspection.unusedReturnValue;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfo;
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
                                                                       getFix(processor), ProblemHighlightType.GENERIC_ERROR_OR_WARNING)};
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
    private ProblemDescriptionsProcessor myProcessor;
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
      makeMethodVoid(project, psiMethod);
    }

    @NotNull
    public String getFamilyName() {
      return getName();
    }

    private static void makeMethodVoid(Project project, PsiMethod psiMethod) {
      final PsiCodeBlock body = psiMethod.getBody();
      assert body != null;
      final List<PsiReturnStatement> returnStatements = new ArrayList<PsiReturnStatement>();
      body.accept(new JavaRecursiveElementVisitor(){
        @Override
        public void visitReturnStatement(final PsiReturnStatement statement) {
          super.visitReturnStatement(statement);
          returnStatements.add(statement);
        }
      });
      final PsiStatement[] psiStatements = body.getStatements();
      final PsiStatement lastStatement =  psiStatements[psiStatements.length - 1];
      for (PsiReturnStatement returnStatement : returnStatements) {
        try {
          if (returnStatement == lastStatement) {
            returnStatement.delete();
          }
          else {
            returnStatement.replace(JavaPsiFacade.getInstance(project).getElementFactory().createStatementFromText("return;", returnStatement));
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
      PsiParameter[] params = psiMethod.getParameterList().getParameters();
      ParameterInfo[] infos = new ParameterInfo[params.length];
      for (int i = 0; i < params.length; i++) {
        PsiParameter param = params[i];
        infos[i] = new ParameterInfo(i, param.getName(), param.getType());
      }

      ChangeSignatureProcessor csp = new ChangeSignatureProcessor(project,
                                                                  psiMethod,
                                                                  false, null, psiMethod.getName(),
                                                                  PsiType.VOID,
                                                                  infos);

      csp.run();
    }
  }
}
