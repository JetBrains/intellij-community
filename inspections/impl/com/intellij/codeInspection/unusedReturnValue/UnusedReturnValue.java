package com.intellij.codeInspection.unusedReturnValue;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.DescriptorProviderInspection;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.JobDescriptor;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefMethod;
import com.intellij.codeInspection.reference.RefVisitor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class UnusedReturnValue extends DescriptorProviderInspection {
  private QuickFix myQuickFix;

  public void runInspection(AnalysisScope scope, final InspectionManager manager) {
    getRefManager().iterate(new RefVisitor() {
      public void visitElement(RefEntity refEntity) {
        if (refEntity instanceof RefMethod) {
          RefMethod refMethod = (RefMethod) refEntity;
          if (!getContext().isToCheckMember((PsiDocCommentOwner) refMethod.getElement(), UnusedReturnValue.this)) return;
          ProblemDescriptor[] descriptors = checkMethod(refMethod, manager);
          if (descriptors != null) {
            addProblemElement(refMethod, descriptors);
          }
        }
      }
    });
  }

  public boolean isGraphNeeded() {
    return true;
  }

  @Nullable
  private ProblemDescriptor[] checkMethod(RefMethod refMethod, final InspectionManager manager) {
    if (refMethod.isConstructor()) return null;
    if (refMethod.hasSuperMethods()) return null;
    if (refMethod.getInReferences().size() == 0) return null;

    if (!refMethod.isReturnValueUsed()) {
      return new ProblemDescriptor[]{
        manager.createProblemDescriptor(refMethod.getElement().getNavigationElement(), InspectionsBundle.message("inspection.unused.return.value.problem.descriptor"),
                                        getFix(), ProblemHighlightType.GENERIC_ERROR_OR_WARNING)};
    }

    return null;
  }

  public boolean queryExternalUsagesRequests(final InspectionManager manager) {
    getRefManager().iterate(new RefVisitor() {
      public void visitElement(RefEntity refEntity) {
        if (refEntity instanceof RefElement && getDescriptions(refEntity) != null) {
          refEntity.accept(new RefVisitor() {
            public void visitMethod(final RefMethod refMethod) {
              getContext().enqueueMethodUsagesProcessor(refMethod, new GlobalInspectionContextImpl.UsagesProcessor() {
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

  @NotNull
  public JobDescriptor[] getJobDescriptors() {
    return new JobDescriptor[] {GlobalInspectionContextImpl.BUILD_GRAPH, GlobalInspectionContextImpl.FIND_EXTERNAL_USAGES};
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
      RefElement refElement = (RefElement)getElement(descriptor);
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

      ChangeSignatureProcessor csp = new ChangeSignatureProcessor(getContext().getProject(),
                                                                  psiMethod,
                                                                  false, null, psiMethod.getName(),
                                                                  PsiType.VOID,
                                                                  infos);

      csp.run();
    }
  }
}
