package com.intellij.codeInspection.unusedReturnValue;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ex.DescriptorProviderInspection;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.JobDescriptor;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefMethod;
import com.intellij.codeInspection.reference.RefVisitor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfo;

/**
 * @author max
 */
public class UnusedReturnValue extends DescriptorProviderInspection {
  private QuickFix myQuickFix;

  public void runInspection(AnalysisScope scope) {
    getRefManager().findAllDeclarations();

    getRefManager().iterate(new RefManager.RefIterator() {
      public void accept(RefElement refElement) {
        if (refElement instanceof RefMethod) {
          RefMethod refMethod = (RefMethod) refElement;
          if (!InspectionManagerEx.isToCheckMember((PsiDocCommentOwner) refMethod.getElement(), UnusedReturnValue.this.getShortName())) return;
          ProblemDescriptor[] descriptors = checkMethod(refMethod);
          if (descriptors != null) {
            addProblemElement(refElement, descriptors);
          }
        }
      }
    });
  }

  private ProblemDescriptor[] checkMethod(RefMethod refMethod) {
    if (refMethod.isConstructor()) return null;
    if (refMethod.isLibraryOverride()) return null;
    if (refMethod.getInReferences().size() == 0) return null;
    if (refMethod.getSuperMethods().size() > 0) return null;

    if (!refMethod.isReturnValueUsed()) {
      return new ProblemDescriptor[]{
        getManager().createProblemDescriptor(refMethod.getElement(), InspectionsBundle.message("inspection.unused.return.value.problem.descriptor"),
                                             getFix(), ProblemHighlightType.GENERIC_ERROR_OR_WARNING)};
    }

    return null;
  }

  public boolean queryExternalUsagesRequests() {
    getRefManager().iterate(new RefManager.RefIterator() {
      public void accept(RefElement refElement) {
        if (getDescriptions(refElement) != null) {
          refElement.accept(new RefVisitor() {
            public void visitMethod(final RefMethod refMethod) {
              getManager().enqueueMethodUsagesProcessor(refMethod, new InspectionManagerEx.UsagesProcessor() {
                public boolean process(PsiReference psiReference) {
                  ignoreElement(refMethod);
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

  public JobDescriptor[] getJobDescriptors() {
    return new JobDescriptor[] {InspectionManagerEx.BUILD_GRAPH, InspectionManagerEx.FIND_EXTERNAL_USAGES};
  }

  public String getDisplayName() {
    return InspectionsBundle.message("inspection.unused.return.value.display.name");
  }

  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  public String getShortName() {
    return "UnusedReturnValue";
  }

  public UnusedReturnValue() {
  }

  private LocalQuickFix getFix() {
    if (myQuickFix == null) {
      myQuickFix = new QuickFix();
    }
    return myQuickFix;
  }

  private class QuickFix implements LocalQuickFix {
    public String getName() {
      return InspectionsBundle.message("inspection.unused.return.value.make.void.quickfix");
    }

    public void applyFix(Project project, ProblemDescriptor descriptor) {
      RefElement refElement = getElement(descriptor);
      if (refElement.isValid() && refElement instanceof RefMethod) {
        RefMethod refMethod = (RefMethod)refElement;
        makeMethodVoid(refMethod);
      }
    }

    public String getFamilyName() {
      return getName();
    }

    private void makeMethodVoid(RefMethod refMethod) {
      PsiMethod psiMethod = (PsiMethod) refMethod.getElement();
      if (psiMethod == null) return;
      PsiParameter[] params = psiMethod.getParameterList().getParameters();
      ParameterInfo[] infos = new ParameterInfo[params.length];
      for (int i = 0; i < params.length; i++) {
        PsiParameter param = params[i];
        infos[i] = new ParameterInfo(i, param.getName(), param.getType());
      }

      ChangeSignatureProcessor csp = new ChangeSignatureProcessor(getManager().getProject(),
                                                                  psiMethod,
                                                                  false, null, psiMethod.getName(),
                                                                  PsiType.VOID,
                                                                  infos);

      csp.run();
    }
  }
}
