package com.intellij.codeInspection.sameParameterValue;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.DescriptorProviderInspection;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.JobDescriptor;
import com.intellij.codeInspection.ex.ProblemDescriptorImpl;
import com.intellij.codeInspection.reference.*;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * @author max
 */
public class SameParameterValueInspection extends DescriptorProviderInspection {
 
  public void runInspection(AnalysisScope scope, final InspectionManager manager) {
    getRefManager().iterate(new RefVisitor() {
      public void visitElement(RefEntity refEntity) {
        if (refEntity instanceof RefMethod) {
          RefMethod refMethod = (RefMethod) refEntity;
          if (!GlobalInspectionContextImpl.isToCheckMember((PsiDocCommentOwner) refMethod.getElement(), SameParameterValueInspection.this.getShortName())) return;
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

  private static ProblemDescriptor[] checkMethod(RefMethod refMethod, InspectionManager manager) {
    if (refMethod.hasSuperMethods()) return null;

    ArrayList<ProblemDescriptor> problems = null;
    RefParameter[] parameters = refMethod.getParameters();
    for (RefParameter refParameter : parameters) {
      String value = refParameter.getActualValueIfSame();
      if (value != null) {
        if (problems == null) problems = new ArrayList<ProblemDescriptor>(1);
        problems.add(manager.createProblemDescriptor(refMethod.getElement().getNavigationElement(), InspectionsBundle.message(
          "inspection.same.parameter.problem.descriptor", "<code>" + refParameter.getName() + "</code>", "<code>" + value + "</code>"),
                                                     (LocalQuickFix [])null,
                                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
      }
    }

    return problems == null ? null : problems.toArray(new ProblemDescriptorImpl[problems.size()]);
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
    return InspectionsBundle.message("inspection.same.parameter.display.name");
  }

  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  public String getShortName() {
    return "SameParameterValue";
  }
}
