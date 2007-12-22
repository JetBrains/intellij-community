package com.intellij.codeInspection.sameReturnValue;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class SameReturnValueInspection extends GlobalJavaInspectionTool {
  @Nullable
  public CommonProblemDescriptor[] checkElement(RefEntity refEntity, AnalysisScope scope, InspectionManager manager, GlobalInspectionContext globalContext,
                                                ProblemDescriptionsProcessor processor) {
    if (refEntity instanceof RefMethod) {
      final RefMethod refMethod = (RefMethod)refEntity;

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
              globalContext.enqueueDerivedMethodsProcessor(refMethod, new GlobalJavaInspectionContext.DerivedMethodsProcessor() {
                public boolean process(PsiMethod derivedMethod) {
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
    return InspectionsBundle.message("inspection.same.return.value.display.name");
  }

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @NotNull
  public String getShortName() {
    return "SameReturnValue";
  }
}
