// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.unneededThrows;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.impl.quickfix.MethodThrowsFix;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class RedundantThrowsDeclarationInspection extends GlobalJavaBatchInspectionTool {
  private static final Logger LOG = Logger.getInstance(RedundantThrowsDeclarationInspection.class);

  public boolean IGNORE_ENTRY_POINTS = false;

  private final RedundantThrowsDeclarationLocalInspection myLocalInspection = new RedundantThrowsDeclarationLocalInspection(this);

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(JavaAnalysisBundle.message("ignore.exceptions.thrown.by.entry.points.methods"), this, "IGNORE_ENTRY_POINTS");
  }

  @Override
  public CommonProblemDescriptor @Nullable [] checkElement(@NotNull RefEntity refEntity,
                                                           @NotNull AnalysisScope scope,
                                                           @NotNull InspectionManager manager,
                                                           @NotNull GlobalInspectionContext globalContext,
                                                           @NotNull ProblemDescriptionsProcessor processor) {
    if (refEntity instanceof RefMethod) {
      final RefMethod refMethod = (RefMethod)refEntity;
      if (refMethod.isSyntheticJSP()) return null;

    //  if (refMethod.hasSuperMethods()) return null;

      if (IGNORE_ENTRY_POINTS && refMethod.isEntry()) return null;

      PsiClass[] unThrown = refMethod.getUnThrownExceptions();
      if (unThrown == null) return null;

      PsiElement psiMethod = refMethod.getPsiElement();
      if (!(psiMethod instanceof PsiMethod)) return null;

      if (((PsiMethod)psiMethod).hasModifier(JvmModifier.NATIVE)) return null;

      PsiReferenceList list = ((PsiMethod)psiMethod).getThrowsList();
      PsiClassType[] throwsList = list.getReferencedTypes();
      PsiJavaCodeReferenceElement[] throwsRefs = list.getReferenceElements();
      List<ProblemDescriptor> problems = null;

      final PsiManager psiManager = psiMethod.getManager();
      for (int i = 0; i < throwsList.length; i++) {
        final PsiClassType throwsType = throwsList[i];
        final String throwsClassName = throwsType.getClassName();
        final PsiJavaCodeReferenceElement throwsRef = throwsRefs[i];
        if (ExceptionUtil.isUncheckedException(throwsType)) continue;
        if (declaredInRemotableMethod((PsiMethod)psiMethod, throwsType)) continue;

        for (PsiClass s : unThrown) {
          final PsiClass throwsResolvedType = throwsType.resolve();
          if (psiManager.areElementsEquivalent(s, throwsResolvedType)) {
            if (problems == null) problems = new ArrayList<>(1);

            RefClass ownerClass = refMethod.getOwnerClass();
            if (refMethod.isAbstract() || ownerClass != null && ownerClass.isInterface()) {
              problems.add(manager.createProblemDescriptor(throwsRef, JavaAnalysisBundle.message(
                "inspection.redundant.throws.problem.descriptor", "<code>#ref</code>"), new MyQuickFix(processor, throwsClassName), ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                                           false));
            }
            else if (!refMethod.getDerivedMethods().isEmpty()) {
              problems.add(manager.createProblemDescriptor(throwsRef, JavaAnalysisBundle.message(
                "inspection.redundant.throws.problem.descriptor1", "<code>#ref</code>"), new MyQuickFix(processor, throwsClassName), ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                                           false));
            }
            else {
              problems.add(manager.createProblemDescriptor(throwsRef, JavaAnalysisBundle.message(
                "inspection.redundant.throws.problem.descriptor2", "<code>#ref</code>"), new MyQuickFix(processor, throwsClassName), ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                                           false));
            }
          }
        }
      }

      if (problems != null) {
        return problems.toArray(CommonProblemDescriptor.EMPTY_ARRAY);
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
              globalContext.enqueueDerivedMethodsProcessor(refMethod, derivedMethod -> {
                processor.ignoreElement(refMethod);
                return true;
              });
            }
          });
        }
      }
    });

    return false;
  }

  @Override
  @Nullable
  public QuickFix getQuickFix(String hint) {
    return new MyQuickFix(null, hint);
  }

  @Override
  @Nullable
  public String getHint(@NotNull final QuickFix fix) {
    return fix instanceof MyQuickFix ? ((MyQuickFix)fix).myHint : null;
  }

  @Nullable
  @Override
  public RefGraphAnnotator getAnnotator(@NotNull RefManager refManager) {
    return new RedundantThrowsGraphAnnotator(refManager);
  }

  private static class MyQuickFix implements LocalQuickFix {
    private final ProblemDescriptionsProcessor myProcessor;
    private final String myHint;

    MyQuickFix(final ProblemDescriptionsProcessor processor, final String hint) {
      myProcessor = processor;
      myHint = hint;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return JavaAnalysisBundle.message("inspection.redundant.throws.remove.quickfix");
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

    private void removeExcessiveThrows(@Nullable RefMethod refMethod, @Nullable final PsiModifierListOwner element, final CommonProblemDescriptor[] problems) {
      try {
        @Nullable final PsiMethod psiMethod;
        if (element == null) {
          LOG.assertTrue(refMethod != null);
          psiMethod = ObjectUtils.tryCast(refMethod.getPsiElement(), PsiMethod.class);
        }
        else {
          psiMethod = (PsiMethod)element;
        }
        if (psiMethod == null) return; //invalid refMethod
        final Project project = psiMethod.getProject();
        final PsiManager psiManager = PsiManager.getInstance(project);
        final List<PsiElement> refsToDelete = new ArrayList<>();
        for (CommonProblemDescriptor problem : problems) {
          final PsiElement psiElement = ((ProblemDescriptor)problem).getPsiElement();
          if (psiElement instanceof PsiJavaCodeReferenceElement) {
            final PsiJavaCodeReferenceElement classRef = (PsiJavaCodeReferenceElement)psiElement;
            final PsiType psiType = JavaPsiFacade.getElementFactory(psiManager.getProject()).createType(classRef);
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

        WriteAction.run(() -> {
          for (final PsiElement aRefsToDelete : refsToDelete) {
            if (aRefsToDelete.isValid()) {
              aRefsToDelete.delete();
            }
          }
        });
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    private void removeException(RefMethod refMethod,
                                 PsiType exceptionType,
                                 List<? super PsiElement> refsToDelete,
                                 PsiMethod psiMethod) {
      ContainerUtil.addAll(refsToDelete, MethodThrowsFix.Remove.extractRefsToRemove(psiMethod, exceptionType));

      if (refMethod != null) {
        assert myProcessor != null;

        for (RefMethod refDerived : refMethod.getDerivedMethods()) {
          PsiElement method = refDerived.getPsiElement();
          if (method instanceof PsiMethod) {
            removeException(refDerived, exceptionType, refsToDelete, (PsiMethod)method);
          }
        }
        ProblemDescriptionsProcessor.resolveAllProblemsInElement(myProcessor, refMethod);
      } else {
        final Query<PsiMethod> query = OverridingMethodsSearch.search(psiMethod);
        query.forEach(m -> {
          removeException(null, exceptionType, refsToDelete, m);
          return true;
        });
      }
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }
  }

  @Nullable
  @Override
  public LocalInspectionTool getSharedLocalInspectionTool() {
    return myLocalInspection;
  }
}
