package com.intellij.codeInspection.sameReturnValue;

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
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class SameReturnValueInspection extends DescriptorProviderInspection {
  public void runInspection(AnalysisScope scope, final InspectionManager manager) {
    getRefManager().iterate(new RefVisitor() {
      public void visitElement(RefEntity refEntity) {
        if (refEntity instanceof RefMethod) {
          RefMethod refMethod = (RefMethod) refEntity;
            if (!getContext().isToCheckMember(refMethod, SameReturnValueInspection.this)) return;
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
  private static ProblemDescriptor[] checkMethod(RefMethod refMethod, final InspectionManager manager) {
    if (refMethod.isConstructor()) return null;
    if (refMethod.hasSuperMethods()) return null;

    String returnValue = refMethod.getReturnValueIfSame();
    if (returnValue != null) {
      final String message;
      if (refMethod.getDerivedMethods().isEmpty()) {
        message = InspectionsBundle.message("inspection.same.return.value.problem.descriptor", "<code>" + returnValue + "</code>");
      } else if (refMethod.hasBody()) {
        message = InspectionsBundle.message("inspection.same.return.value.problem.descriptor1", "<code>" + returnValue + "</code>");
      } else {
        message = InspectionsBundle.message("inspection.same.return.value.problem.descriptor2", "<code>" + returnValue + "</code>");
      }

      return new ProblemDescriptor[] {manager.createProblemDescriptor(refMethod.getElement().getNavigationElement(), message, (LocalQuickFix [])null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)};
    }

    return null;
  }

  public boolean queryExternalUsagesRequests(final InspectionManager manager) {
    getRefManager().iterate(new RefVisitor() {
      public void visitElement(RefEntity refEntity) {
        if (refEntity instanceof RefElement && getDescriptions(refEntity) != null) {
          refEntity.accept(new RefVisitor() {
            public void visitMethod(final RefMethod refMethod) {
              getContext().enqueueDerivedMethodsProcessor(refMethod, new GlobalInspectionContextImpl.DerivedMethodsProcessor() {
                public boolean process(PsiMethod derivedMethod) {
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
    return InspectionsBundle.message("inspection.same.return.value.display.name");
  }

  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  public String getShortName() {
    return "SameReturnValue";
  }
}
