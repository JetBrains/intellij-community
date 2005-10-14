package com.intellij.codeInspection.sameParameterValue;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ex.DescriptorProviderInspection;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.JobDescriptor;
import com.intellij.codeInspection.ex.ProblemDescriptorImpl;
import com.intellij.codeInspection.reference.*;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiReference;

import java.util.ArrayList;

/**
 * @author max
 */
public class SameParameterValueInspection extends DescriptorProviderInspection {
  public SameParameterValueInspection() {
  }

  public void runInspection(AnalysisScope scope) {
    getRefManager().findAllDeclarations();

    getRefManager().iterate(new RefManager.RefIterator() {
      public void accept(RefElement refElement) {
        if (refElement instanceof RefMethod) {
          RefMethod refMethod = (RefMethod) refElement;
          if (!InspectionManagerEx.isToCheckMember((PsiDocCommentOwner) refMethod.getElement(), SameParameterValueInspection.this.getShortName())) return;
          ProblemDescriptor[] descriptors = checkMethod(refMethod);
          if (descriptors != null) {
            addProblemElement(refElement, descriptors);
          }
        }
      }
    });
  }

  private ProblemDescriptor[] checkMethod(RefMethod refMethod) {
    if (refMethod.isLibraryOverride()) return null;
    if (!refMethod.getSuperMethods().isEmpty()) return null;

    ArrayList problems = null;
    RefParameter[] parameters = refMethod.getParameters();
    for (int i = 0; i < parameters.length; i++) {
      RefParameter refParameter = parameters[i];
      String value = refParameter.getActualValueIfSame();
      if (value != null) {
        if (problems == null) problems = new ArrayList(1);
        problems.add(getManager().createProblemDescriptor(refMethod.getElement(),
                                                          InspectionsBundle.message("inspection.same.parameter.problem.descriptor",
                                                                                    "<code>" + refParameter.getName() + "</code>",
                                                                                    "<code>" + value + "</code>"),
                                                          (LocalQuickFix [])null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
      }
    }

    return problems == null ? null : (ProblemDescriptor[]) problems.toArray(new ProblemDescriptorImpl[problems.size()]);
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
    return InspectionsBundle.message("inspection.same.parameter.display.name");
  }

  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  public String getShortName() {
    return "SameParameterValue";
  }
}
