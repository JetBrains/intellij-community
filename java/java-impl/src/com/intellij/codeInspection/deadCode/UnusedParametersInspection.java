/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.codeInspection.deadCode;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.impl.quickfix.RemoveUnusedParameterFix;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.EntryPointsManager;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspectionBase;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiReferenceProcessor;
import com.intellij.psi.search.PsiReferenceProcessorAdapter;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class UnusedParametersInspection extends GlobalJavaBatchInspectionTool {
  @Override
  @Nullable
  public CommonProblemDescriptor[] checkElement(@NotNull final RefEntity refEntity,
                                                @NotNull final AnalysisScope scope,
                                                @NotNull final InspectionManager manager,
                                                @NotNull final GlobalInspectionContext globalContext,
                                                @NotNull final ProblemDescriptionsProcessor processor) {
    if (refEntity instanceof RefMethod) {
      final RefMethod refMethod = (RefMethod)refEntity;

      if (refMethod.isSyntheticJSP()) return null;

      if (refMethod.isExternalOverride()) return null;

      if (!(refMethod.isStatic() || refMethod.isConstructor()) && !refMethod.getSuperMethods().isEmpty()) return null;

      if ((refMethod.isAbstract() || refMethod.getOwnerClass().isInterface()) && refMethod.getDerivedMethods().isEmpty()) return null;

      if (refMethod.isAppMain()) return null;

      final List<RefParameter> unusedParameters = getUnusedParameters(refMethod);

      if (unusedParameters.isEmpty()) return null;

      if (refMethod.isEntry()) return null;

      final PsiModifierListOwner element = refMethod.getElement();
      if (element != null && EntryPointsManager.getInstance(manager.getProject()).isEntryPoint(element)) return null;

      final List<ProblemDescriptor> result = new ArrayList<>();
      for (RefParameter refParameter : unusedParameters) {
        final PsiParameter parameter = refParameter.getElement();
        final PsiIdentifier psiIdentifier = parameter != null ? parameter.getNameIdentifier() : null;
        if (psiIdentifier != null) {
          result.add(manager.createProblemDescriptor(psiIdentifier,
                                                     InspectionsBundle.message(refMethod.isAbstract() ? "inspection.unused.parameter.composer" : "inspection.unused.parameter.composer1"),
                                                     new AcceptSuggested(globalContext.getRefManager(), processor, refParameter.getName()),
                                                     ProblemHighlightType.LIKE_UNUSED_SYMBOL, false));
        }
      }
      return result.toArray(new CommonProblemDescriptor[result.size()]);
    }
    return null;
  }

  protected boolean queryExternalUsagesRequests(@NotNull final RefManager manager, @NotNull final GlobalJavaInspectionContext globalContext,
                                                @NotNull final ProblemDescriptionsProcessor processor) {
    final Project project = manager.getProject();
    for (RefElement entryPoint : globalContext.getEntryPointsManager(manager).getEntryPoints()) {
      processor.ignoreElement(entryPoint);
    }

    final PsiSearchHelper helper = PsiSearchHelper.SERVICE.getInstance(project);
    final AnalysisScope scope = manager.getScope();
    manager.iterate(new RefJavaVisitor() {
      @Override
      public void visitElement(@NotNull RefEntity refEntity) {
        if (refEntity instanceof RefMethod) {
          RefMethod refMethod = (RefMethod)refEntity;
          final PsiModifierListOwner element = refMethod.getElement();
          if (!refMethod.isStatic() && !refMethod.isConstructor() && !PsiModifier.PRIVATE.equals(refMethod.getAccessModifier())) {
            final ArrayList<RefParameter> unusedParameters = getUnusedParameters(refMethod);
            if (unusedParameters.isEmpty()) return;
            if (element instanceof PsiMethod) {
              PsiMethod psiMethod = (PsiMethod)element;
              PsiMethod[] derived = OverridingMethodsSearch.search(psiMethod).toArray(PsiMethod.EMPTY_ARRAY);
              for (final RefParameter refParameter : unusedParameters) {
                if (refMethod.isAbstract() && derived.length == 0) {
                  refParameter.parameterReferenced(false);
                  processor.ignoreElement(refParameter);
                }
                else {
                  int idx = refParameter.getIndex();
                  final boolean[] found = {false};
                  for (int i = 0; i < derived.length && !found[0]; i++) {
                    if (scope == null || !scope.contains(derived[i])) {
                      final PsiParameter[] parameters = derived[i].getParameterList().getParameters();
                      if (parameters.length < idx) continue;
                      PsiParameter psiParameter = parameters[idx];
                      ReferencesSearch.search(psiParameter, helper.getUseScope(psiParameter), false)
                        .forEach(new PsiReferenceProcessorAdapter(
                          new PsiReferenceProcessor() {
                            @Override
                            public boolean execute(PsiReference element) {
                              refParameter.parameterReferenced(false);
                              processor.ignoreElement(refMethod);
                              found[0] = true;
                              return false;
                            }
                          }));
                    }
                  }
                }
              }
            }
          }
        }
      }
    });
    return false;
  }

  public String getHint(@NotNull final QuickFix fix) {
    if (fix instanceof AcceptSuggested) {
      return ((AcceptSuggested)fix).getHint();
    }
    return null;
  }


  @Nullable
  public QuickFix getQuickFix(final String hint) {
    return new AcceptSuggested(null, null, hint);
  }

  public static ArrayList<RefParameter> getUnusedParameters(RefMethod refMethod) {
    boolean checkDeep = !refMethod.isStatic() && !refMethod.isConstructor();
    ArrayList<RefParameter> res = new ArrayList<>();
    RefParameter[] methodParameters = refMethod.getParameters();
    RefParameter[] result = new RefParameter[methodParameters.length];
    System.arraycopy(methodParameters, 0, result, 0, methodParameters.length);

    clearUsedParameters(refMethod, result, checkDeep);

    for (RefParameter parameter : result) {
      if (parameter != null && !((RefElementImpl)parameter).isSuppressed(UnusedSymbolLocalInspectionBase.UNUSED_PARAMETERS_SHORT_NAME, UnusedSymbolLocalInspectionBase.UNUSED_ID)) {
        res.add(parameter);
      }
    }

    return res;
  }

  private static void clearUsedParameters(@NotNull RefMethod refMethod, RefParameter[] params, boolean checkDeep) {
    RefParameter[] methodParams = refMethod.getParameters();

    for (int i = 0; i < methodParams.length; i++) {
      if (methodParams[i].isUsedForReading()) params[i] = null;
    }

    if (checkDeep) {
      for (RefMethod refOverride : refMethod.getDerivedMethods()) {
        clearUsedParameters(refOverride, params, true);
      }
    }
  }

  private static class AcceptSuggested implements LocalQuickFix {
    private final RefManager myManager;
    private final String myHint;
    private final ProblemDescriptionsProcessor myProcessor;

    public AcceptSuggested(@Nullable RefManager manager,
                           @Nullable ProblemDescriptionsProcessor processor,
                           final String hint) {
      myManager = manager;
      myProcessor = processor;
      myHint = hint;
    }

    public String getHint() {
      return myHint;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.unused.parameter.delete.quickfix");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement psiElement = descriptor.getPsiElement();
      if (!FileModificationService.getInstance().preparePsiElementForWrite(psiElement)) return;
      final PsiParameter psiParameter = PsiTreeUtil.getParentOfType(psiElement, PsiParameter.class);
      final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(psiElement, PsiMethod.class);
      if (psiMethod != null && psiParameter != null) {
        final RefElement refMethod = myManager != null ? myManager.getReference(psiMethod) : null;
        final PsiModificationTracker tracker = psiMethod.getManager().getModificationTracker();
        final long startModificationCount = tracker.getModificationCount();

        RemoveUnusedParameterFix.removeReferences(psiParameter);
        if (refMethod != null && startModificationCount != tracker.getModificationCount()) {
          Objects.requireNonNull(myProcessor).ignoreElement(refMethod);
        }
      }
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }
  }
}
