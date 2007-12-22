package com.intellij.codeInspection.unusedReturnValue;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
