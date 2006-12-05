package com.intellij.codeInspection.unneededThrows;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.ProblemDescriptorImpl;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefMethod;
import com.intellij.codeInspection.reference.RefVisitor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AllOverridingMethodsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class RedundantThrows extends GlobalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.unneededThrows.RedundantThrows");
  private static final String DISPLAY_NAME = InspectionsBundle.message("inspection.redundant.throws.display.name");
  private MyQuickFix myQuickFix;
  @NonNls private static final String SHORT_NAME = "RedundantThrows";

  public boolean isGraphNeeded() {
    return true;
  }

  @Nullable
  public CommonProblemDescriptor[] checkElement(RefEntity refEntity,
                                                AnalysisScope scope,
                                                InspectionManager manager,
                                                GlobalInspectionContext globalContext,
                                                ProblemDescriptionsProcessor processor) {
    if (refEntity instanceof RefMethod) {
      final RefMethod refMethod = (RefMethod)refEntity;
      if (refMethod.isSyntheticJSP()) return null;

      if (refMethod.hasSuperMethods()) return null;

      if (refMethod.isEntry()) return null;

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
        if (declaredInRemotableMethod(psiMethod, throwsType)) continue;

        for (PsiClass s : unThrown) {
          final PsiClass throwsResolvedType = throwsType.resolve();
          if (Comparing.equal(s, throwsResolvedType)) {
            if (problems == null) problems = new ArrayList<ProblemDescriptor>(1);

            if (refMethod.isAbstract() || refMethod.getOwnerClass().isInterface()) {
              problems.add(manager.createProblemDescriptor(throwsRef, InspectionsBundle.message(
                "inspection.redundant.throws.problem.descriptor", "<code>#ref</code>"), getFix(processor), ProblemHighlightType.LIKE_UNUSED_SYMBOL));
            }
            else if (!refMethod.getDerivedMethods().isEmpty()) {
              problems.add(manager.createProblemDescriptor(throwsRef, InspectionsBundle.message(
                "inspection.redundant.throws.problem.descriptor1", "<code>#ref</code>"), getFix(processor), ProblemHighlightType.LIKE_UNUSED_SYMBOL));
            }
            else {
              problems.add(manager.createProblemDescriptor(throwsRef, InspectionsBundle.message(
                "inspection.redundant.throws.problem.descriptor2", "<code>#ref</code>"), getFix(processor), ProblemHighlightType.LIKE_UNUSED_SYMBOL));
            }
          }
        }
      }

      if (problems != null) {
        return problems.toArray(new ProblemDescriptorImpl[problems.size()]);
      }
    }

    return null;
  }

  private static boolean declaredInRemotableMethod(final PsiMethod psiMethod, final PsiClassType throwsType) {
    if (!throwsType.equalsToText("java.rmi.RemoteException")) return false;
    PsiClass aClass = psiMethod.getContainingClass();
    if (aClass == null) return false;
    PsiClass remote = aClass.getManager().findClass("java.rmi.Remote", GlobalSearchScope.allScope(aClass.getProject()));
    return remote != null && aClass.isInheritor(remote, true);
  }


  public boolean queryExternalUsagesRequests(final InspectionManager manager,
                                             final GlobalInspectionContext globalContext,
                                             final ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    globalContext.getRefManager().iterate(new RefVisitor() {
      public void visitElement(RefEntity refEntity) {
        if (problemDescriptionsProcessor.getDescriptions(refEntity) != null) {
          refEntity.accept(new RefVisitor() {
            public void visitMethod(final RefMethod refMethod) {
              globalContext.enqueueDerivedMethodsProcessor(refMethod, new GlobalInspectionContextImpl.DerivedMethodsProcessor() {
                public boolean process(PsiMethod derivedMethod) {
                  problemDescriptionsProcessor.ignoreElement(refMethod);
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
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  private LocalQuickFix getFix(final ProblemDescriptionsProcessor processor) {
    if (myQuickFix == null) {
      myQuickFix = new MyQuickFix(processor);
    }
    return myQuickFix;
  }


  @Nullable
  public QuickFix getQuickFix(String hint) {
    return getFix(null);
  }

  private static class MyQuickFix implements LocalQuickFix {
    private ProblemDescriptionsProcessor myProcessor;

    public MyQuickFix(final ProblemDescriptionsProcessor processor) {
      myProcessor = processor;
    }

    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.redundant.throws.remove.quickfix");
    }

    public void applyFix(@NotNull Project project, ProblemDescriptor descriptor) {
      if (myProcessor != null) {
        RefElement refElement = (RefElement)myProcessor.getElement(descriptor);
        if (refElement.isValid() && refElement instanceof RefMethod) {
          RefMethod refMethod = (RefMethod)refElement;
          final ProblemDescriptor[] problems = (ProblemDescriptor[])myProcessor.getDescriptions(refMethod);
          if (problems != null) {
            removeExcessiveThrows(refMethod, null, problems);
          }
        }
      } else {
        final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class);
        if (psiMethod != null) {
          removeExcessiveThrows(null, psiMethod, new ProblemDescriptor[]{descriptor});
        }
      }
    }

    @NotNull
    public String getFamilyName() {
      return getName();
    }

    private static void removeExcessiveThrows(RefMethod refMethod, final PsiModifierListOwner element, final ProblemDescriptor[] problems) {
      try {
        Project project = element.getProject();
        PsiManager psiManager = PsiManager.getInstance(project);
        List<PsiJavaCodeReferenceElement> refsToDelete = new ArrayList<PsiJavaCodeReferenceElement>();
        for (ProblemDescriptor problem : problems) {
          PsiJavaCodeReferenceElement classRef = (PsiJavaCodeReferenceElement)problem.getPsiElement();
          if (classRef == null) continue;
          PsiType psiType = psiManager.getElementFactory().createType(classRef);
          removeException(refMethod, psiType, refsToDelete, (PsiMethod)problem.getPsiElement());
        }

        for (final PsiJavaCodeReferenceElement aRefsToDelete : refsToDelete) {
          aRefsToDelete.delete();
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    private static void removeException(RefMethod refMethod,
                                        final PsiType exceptionType,
                                        final List<PsiJavaCodeReferenceElement> refsToDelete,
                                        final PsiMethod psiMethod) {
      PsiManager psiManager = psiMethod.getManager();

      PsiJavaCodeReferenceElement[] refs = psiMethod.getThrowsList().getReferenceElements();
      for (PsiJavaCodeReferenceElement ref : refs) {
        PsiType refType = psiManager.getElementFactory().createType(ref);
        if (exceptionType.isAssignableFrom(refType)) {
          refsToDelete.add(ref);
        }
      }

      if (refMethod != null) {
        for (RefMethod refDerived : refMethod.getDerivedMethods()) {
          removeException(refDerived, exceptionType, refsToDelete, (PsiMethod)refDerived.getElement());
        }
      } else {
        final Query<Pair<PsiMethod,PsiMethod>> query = AllOverridingMethodsSearch.search(psiMethod.getContainingClass());
        query.forEach(new Processor<Pair<PsiMethod, PsiMethod>>(){
          public boolean process(final Pair<PsiMethod, PsiMethod> pair) {
            if (pair.first == psiMethod) {
              removeException(null, exceptionType, refsToDelete, pair.second);
            }
            return true;
          }
        });
      }
    }
  }
}
