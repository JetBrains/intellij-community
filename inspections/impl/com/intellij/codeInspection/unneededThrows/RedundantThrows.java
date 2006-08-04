package com.intellij.codeInspection.unneededThrows;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.DescriptorProviderInspection;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.JobDescriptor;
import com.intellij.codeInspection.ex.ProblemDescriptorImpl;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefMethod;
import com.intellij.codeInspection.reference.RefVisitor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class RedundantThrows extends DescriptorProviderInspection {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.unneededThrows.RedundantThrows");
  public static final String DISPLAY_NAME = InspectionsBundle.message("inspection.redundant.throws.display.name");
  private MyQuickFix myQuickFix;
  @NonNls public static final String SHORT_NAME = "RedundantThrows";

  public void runInspection(AnalysisScope scope, final InspectionManager manager) {
    getRefManager().iterate(new RefVisitor() {
      public void visitElement(RefEntity refEntity) {
        if (refEntity instanceof RefMethod && !((RefMethod)refEntity).isSyntheticJSP()) {
          RefMethod refMethod = (RefMethod)refEntity;
          if (!getContext().isToCheckMember((PsiDocCommentOwner) refMethod.getElement(), RedundantThrows.this)) return;
          ProblemDescriptorImpl[] descriptors = checkMethod(refMethod, manager);
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
  private ProblemDescriptorImpl[] checkMethod(RefMethod refMethod, InspectionManager manager) {
    if (refMethod.hasSuperMethods()) return null;

    PsiClass[] unThrown = refMethod.getUnThrownExceptions();
    if (unThrown == null) return null;

    PsiMethod psiMethod = (PsiMethod)refMethod.getElement();
    PsiClassType[] throwsList = psiMethod.getThrowsList().getReferencedTypes();
    PsiJavaCodeReferenceElement[] throwsRefs = psiMethod.getThrowsList().getReferenceElements();
    ArrayList<ProblemDescriptor> problems = null;

    for (int i = 0; i < throwsList.length; i++) {
      PsiClassType throwsType = throwsList[i];
      PsiJavaCodeReferenceElement throwsRef = throwsRefs[i];
      if (ExceptionUtil.isUncheckedException(throwsType)) continue;

      for (PsiClass s : unThrown) {
        final PsiClass throwsResolvedType = throwsType.resolve();
        if (Comparing.equal(s, throwsResolvedType)) {
          if (problems == null) problems = new ArrayList<ProblemDescriptor>(1);

          if (refMethod.isAbstract() || refMethod.getOwnerClass().isInterface()) {
            problems.add(manager.createProblemDescriptor(throwsRef, InspectionsBundle.message(
              "inspection.redundant.throws.problem.descriptor", "<code>#ref</code>"), getFix(), ProblemHighlightType.LIKE_UNUSED_SYMBOL));
          }
          else if (refMethod.getDerivedMethods().size() > 0) {
            problems.add(manager.createProblemDescriptor(throwsRef, InspectionsBundle.message(
              "inspection.redundant.throws.problem.descriptor1", "<code>#ref</code>"), getFix(), ProblemHighlightType.LIKE_UNUSED_SYMBOL));
          }
          else {
            problems.add(manager.createProblemDescriptor(throwsRef, InspectionsBundle.message(
              "inspection.redundant.throws.problem.descriptor2", "<code>#ref</code>"), getFix(), ProblemHighlightType.LIKE_UNUSED_SYMBOL));
          }


        }
      }
    }

    if (problems != null) {
      return problems.toArray(new ProblemDescriptorImpl[problems.size()]);
    }

    return null;
  }

  public boolean queryExternalUsagesRequests(final InspectionManager manager) {
    getRefManager().iterate(new RefVisitor() {
      public void visitElement(RefEntity refEntity) {
        if (getDescriptions(refEntity) != null) {
          refEntity.accept(new RefVisitor() {
            public void visitMethod(final RefMethod refMethod) {
              getContext().enqueueDerivedMethodsProcessor(refMethod, new GlobalInspectionContextImpl.DerivedMethodsProcessor() {
                public boolean process(PsiMethod derivedMethod) {
                  ignoreElement(refMethod);
                  return true;
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
    return new JobDescriptor[]{GlobalInspectionContextImpl.BUILD_GRAPH, GlobalInspectionContextImpl.FIND_EXTERNAL_USAGES};
  }

  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  public String getShortName() {
    return SHORT_NAME;
  }

  private LocalQuickFix getFix() {
    if (myQuickFix == null) {
      myQuickFix = new MyQuickFix();
    }
    return myQuickFix;
  }

  private class MyQuickFix implements LocalQuickFix {
    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.redundant.throws.remove.quickfix");
    }

    public void applyFix(@NotNull Project project, ProblemDescriptor descriptor) {
      RefElement refElement = (RefElement)getElement(descriptor);
      if (refElement.isValid() && refElement instanceof RefMethod) {
        RefMethod refMethod = (RefMethod)refElement;
        removeExcessiveThrows(refMethod);
      }
    }

    @NotNull
    public String getFamilyName() {
      return getName();
    }

    private void removeExcessiveThrows(RefMethod refMethod) {
      try {
        Project project = getContext().getProject();
        ProblemDescriptor[] problems = (ProblemDescriptor[])getDescriptions(refMethod);
        if (problems == null) return;
        PsiManager psiManager = PsiManager.getInstance(project);
        List<PsiJavaCodeReferenceElement> refsToDelete = new ArrayList<PsiJavaCodeReferenceElement>();
        for (ProblemDescriptor problem : problems) {
          PsiJavaCodeReferenceElement classRef = (PsiJavaCodeReferenceElement)problem.getPsiElement();
          if (classRef == null) continue;
          PsiType psiType = psiManager.getElementFactory().createType(classRef);
          removeException(refMethod, psiType, refsToDelete);
        }

        for (final PsiJavaCodeReferenceElement aRefsToDelete : refsToDelete) {
          aRefsToDelete.delete();
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    private void removeException(RefMethod refMethod, PsiType exceptionType,
                                 List<PsiJavaCodeReferenceElement> refsToDelete) {
      PsiMethod psiMethod = (PsiMethod)refMethod.getElement();
      PsiManager psiManager = psiMethod.getManager();

      PsiJavaCodeReferenceElement[] refs = psiMethod.getThrowsList().getReferenceElements();
      for (PsiJavaCodeReferenceElement ref : refs) {
        PsiType refType = psiManager.getElementFactory().createType(ref);
        if (exceptionType.isAssignableFrom(refType)) {
          refsToDelete.add(ref);
        }
      }

      for (RefMethod refDerived : refMethod.getDerivedMethods()) {
        removeException(refDerived, exceptionType, refsToDelete);
      }
    }
  }
}
