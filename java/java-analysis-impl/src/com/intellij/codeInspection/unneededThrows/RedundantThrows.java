/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.unneededThrows;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.*;
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
import com.intellij.util.containers.BidirectionalMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class RedundantThrows extends GlobalJavaBatchInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.unneededThrows.RedundantThrows");
  private static final String DISPLAY_NAME = InspectionsBundle.message("inspection.redundant.throws.display.name");
  private final BidirectionalMap<String, QuickFix> myQuickFixes = new BidirectionalMap<>();
  @NonNls private static final String SHORT_NAME = "RedundantThrows";

  @Override
  @Nullable
  public CommonProblemDescriptor[] checkElement(@NotNull RefEntity refEntity,
                                                @NotNull AnalysisScope scope,
                                                @NotNull InspectionManager manager,
                                                @NotNull GlobalInspectionContext globalContext,
                                                @NotNull ProblemDescriptionsProcessor processor) {
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
      List<ProblemDescriptor> problems = null;

      final PsiManager psiManager = psiMethod.getManager();
      for (int i = 0; i < throwsList.length; i++) {
        final PsiClassType throwsType = throwsList[i];
        final String throwsClassName = throwsType.getClassName();
        final PsiJavaCodeReferenceElement throwsRef = throwsRefs[i];
        if (ExceptionUtil.isUncheckedException(throwsType)) continue;
        if (declaredInRemotableMethod(psiMethod, throwsType)) continue;

        for (PsiClass s : unThrown) {
          final PsiClass throwsResolvedType = throwsType.resolve();
          if (psiManager.areElementsEquivalent(s, throwsResolvedType)) {
            if (problems == null) problems = new ArrayList<>(1);

            if (refMethod.isAbstract() || refMethod.getOwnerClass().isInterface()) {
              problems.add(manager.createProblemDescriptor(throwsRef, InspectionsBundle.message(
                "inspection.redundant.throws.problem.descriptor", "<code>#ref</code>"), getFix(processor, throwsClassName), ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                                           false));
            }
            else if (!refMethod.getDerivedMethods().isEmpty()) {
              problems.add(manager.createProblemDescriptor(throwsRef, InspectionsBundle.message(
                "inspection.redundant.throws.problem.descriptor1", "<code>#ref</code>"), getFix(processor, throwsClassName), ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                                           false));
            }
            else {
              problems.add(manager.createProblemDescriptor(throwsRef, InspectionsBundle.message(
                "inspection.redundant.throws.problem.descriptor2", "<code>#ref</code>"), getFix(processor, throwsClassName), ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                                           false));
            }
          }
        }
      }

      if (problems != null) {
        return problems.toArray(new CommonProblemDescriptor[problems.size()]);
      }
    }

    return null;
  }

  private static boolean declaredInRemotableMethod(final PsiMethod psiMethod, final PsiClassType throwsType) {
    if (!throwsType.equalsToText("java.rmi.RemoteException")) return false;
    PsiClass aClass = psiMethod.getContainingClass();
    if (aClass == null) return false;
    PsiClass remote =
      JavaPsiFacade.getInstance(aClass.getProject()).findClass("java.rmi.Remote", GlobalSearchScope.allScope(aClass.getProject()));
    return remote != null && aClass.isInheritor(remote, true);
  }


  @Override
  protected boolean queryExternalUsagesRequests(@NotNull final RefManager manager, @NotNull final GlobalJavaInspectionContext globalContext,
                                                @NotNull final ProblemDescriptionsProcessor processor) {
    manager.iterate(new RefJavaVisitor() {
      @Override public void visitElement(@NotNull RefEntity refEntity) {
        if (processor.getDescriptions(refEntity) != null) {
          refEntity.accept(new RefJavaVisitor() {
            @Override public void visitMethod(@NotNull final RefMethod refMethod) {
              globalContext.enqueueDerivedMethodsProcessor(refMethod, new GlobalJavaInspectionContext.DerivedMethodsProcessor() {
                @Override
                public boolean process(PsiMethod derivedMethod) {
                  processor.ignoreElement(refMethod);
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

  @Override
  @NotNull
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @Override
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  private LocalQuickFix getFix(final ProblemDescriptionsProcessor processor, final String hint) {
    QuickFix fix = myQuickFixes.get(hint);
    if (fix == null) {
      fix = new MyQuickFix(processor, hint);
      if (hint != null) {
        myQuickFixes.put(hint, fix);
      }
    }
    return (LocalQuickFix)fix;
  }


  @Override
  @Nullable
  public QuickFix getQuickFix(String hint) {
    return getFix(null, hint);
  }

  @Override
  @Nullable
  public String getHint(@NotNull final QuickFix fix) {
    final List<String> hints = myQuickFixes.getKeysByValue(fix);
    LOG.assertTrue(hints != null && hints.size() == 1);
    return hints.get(0);
  }

  private static class MyQuickFix implements LocalQuickFix {
    private final ProblemDescriptionsProcessor myProcessor;
    private final String myHint;

    public MyQuickFix(final ProblemDescriptionsProcessor processor, final String hint) {
      myProcessor = processor;
      myHint = hint;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.redundant.throws.remove.quickfix");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      if (myProcessor != null) {
        RefElement refElement = (RefElement)myProcessor.getElement(descriptor);
        if (refElement instanceof RefMethod && refElement.isValid()) {
          RefMethod refMethod = (RefMethod)refElement;
          final CommonProblemDescriptor[] problems = myProcessor.getDescriptions(refMethod);
          if (problems != null) {
            removeExcessiveThrows(refMethod, null, problems);
          }
        }
      }
      else {
        final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class);
        if (psiMethod != null) {
          removeExcessiveThrows(null, psiMethod, new CommonProblemDescriptor[]{descriptor});
        }
      }
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    private void removeExcessiveThrows(@Nullable RefMethod refMethod, @Nullable final PsiModifierListOwner element, final CommonProblemDescriptor[] problems) {
      try {
        @Nullable final PsiMethod psiMethod;
        if (element == null) {
          LOG.assertTrue(refMethod != null);
          psiMethod = (PsiMethod)refMethod.getElement();
        }
        else {
          psiMethod = (PsiMethod)element;
        }
        if (psiMethod == null) return; //invalid refMethod
        final Project project = psiMethod.getProject();
        final PsiManager psiManager = PsiManager.getInstance(project);
        final List<PsiJavaCodeReferenceElement> refsToDelete = new ArrayList<>();
        for (CommonProblemDescriptor problem : problems) {
          final PsiElement psiElement = ((ProblemDescriptor)problem).getPsiElement();
          if (psiElement instanceof PsiJavaCodeReferenceElement) {
            final PsiJavaCodeReferenceElement classRef = (PsiJavaCodeReferenceElement)psiElement;
            final PsiType psiType = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory().createType(classRef);
            removeException(refMethod, psiType, refsToDelete, psiMethod);
          } else {
            final PsiReferenceList throwsList = psiMethod.getThrowsList();
            final PsiClassType[] classTypes = throwsList.getReferencedTypes();
            for (PsiClassType classType : classTypes) {
              final String text = classType.getClassName();
              if (Comparing.strEqual(myHint, text)) {
                removeException(refMethod, classType, refsToDelete, psiMethod);
                break;
              }
            }
          }
        }

        //check read-only status for derived methods
        if (!FileModificationService.getInstance().preparePsiElementsForWrite(refsToDelete)) return;

        for (final PsiJavaCodeReferenceElement aRefsToDelete : refsToDelete) {
          if (aRefsToDelete.isValid()) {
            aRefsToDelete.delete();
          }
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    private static void removeException(final RefMethod refMethod,
                                        final PsiType exceptionType,
                                        final List<PsiJavaCodeReferenceElement> refsToDelete,
                                        final PsiMethod psiMethod) {
      PsiManager psiManager = psiMethod.getManager();

      PsiJavaCodeReferenceElement[] refs = psiMethod.getThrowsList().getReferenceElements();
      for (PsiJavaCodeReferenceElement ref : refs) {
        PsiType refType = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory().createType(ref);
        if (exceptionType.isAssignableFrom(refType)) {
          refsToDelete.add(ref);
        }
      }

      if (refMethod != null) {
        for (RefMethod refDerived : refMethod.getDerivedMethods()) {
          PsiModifierListOwner method = refDerived.getElement();
          if (method != null) {
            removeException(refDerived, exceptionType, refsToDelete, (PsiMethod)method);
          }
        }
      } else {
        final Query<Pair<PsiMethod,PsiMethod>> query = AllOverridingMethodsSearch.search(psiMethod.getContainingClass());
        query.forEach(pair -> {
          if (pair.first == psiMethod) {
            removeException(null, exceptionType, refsToDelete, pair.second);
          }
          return true;
        });
      }
    }
  }
}
