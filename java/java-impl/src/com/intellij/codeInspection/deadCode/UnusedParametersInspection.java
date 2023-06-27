// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.deadCode;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.EntryPointsManager;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspectionBase;
import com.intellij.icons.AllIcons;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UElementKt;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

@SuppressWarnings("InspectionDescriptionNotFoundInspection") // via UnusedDeclarationInspection
class UnusedParametersInspection extends GlobalJavaBatchInspectionTool {
  @Override
  public CommonProblemDescriptor @Nullable [] checkElement(@NotNull final RefEntity refEntity,
                                                           @NotNull final AnalysisScope scope,
                                                           @NotNull final InspectionManager manager,
                                                           @NotNull final GlobalInspectionContext globalContext,
                                                           @NotNull final ProblemDescriptionsProcessor processor) {
    if (!(refEntity instanceof RefMethod refMethod)) return null;
    if (refMethod.isSyntheticJSP()) return null;
    if (refMethod.isExternalOverride()) return null;
    if (!(refMethod.isStatic() || refMethod.isConstructor()) && !refMethod.getSuperMethods().isEmpty()) return null;
    RefClass aClass = refMethod.getOwnerClass();
    if (aClass != null && ((refMethod.isAbstract() || aClass.isInterface()) && refMethod.getDerivedReferences().isEmpty())) {
      return null;
    }
    List<RefParameter> unusedParameters = getUnusedParameters(refMethod);
    if (unusedParameters.isEmpty()) return null;
    UMethod uMethod = refMethod.getUastElement();
    if (uMethod == null) return null;
    PsiElement element = uMethod.getJavaPsi();
    if (refMethod.isAppMain()) {
      if (element == null || !element.getLanguage().isKindOf("kotlin")) return null;
    }
    else if (refMethod.isEntry()) return null;
    if (element != null && EntryPointsManager.getInstance(manager.getProject()).isEntryPoint(element)) return null;

    List<ProblemDescriptor> result = new ArrayList<>();
    for (RefParameter refParameter : unusedParameters) {
      UParameter parameter = refParameter.getUastElement();
      PsiElement anchor = UElementKt.getSourcePsiElement(parameter.getUastAnchor());
      if (anchor != null) {
        final QuickFixFactory quickFixFactory = QuickFixFactory.getInstance();
        final List<LocalQuickFix> fixes = new ArrayList<>(2);
        fixes.add(new AcceptSuggested(refParameter.getName()));
        PsiElement parent = anchor.getParent();
        if (parent instanceof PsiNamedElement) {
          fixes.add(quickFixFactory.createRenameToIgnoredFix((PsiNamedElement)parent, true));
        }
        String message;
        if (refMethod.isAbstract()) {
          message = JavaBundle.message("inspection.unused.parameter.composer");
        }
        else if (refMethod.getDerivedMethods().isEmpty()) {
          message = JavaBundle.message("inspection.unused.parameter.problem.descriptor");
        }
        else {
          message = JavaBundle.message("inspection.unused.parameter.composer1");
        }
        result.add(manager.createProblemDescriptor(anchor,
                                                   message,
                                                   fixes.toArray(LocalQuickFix.EMPTY_ARRAY),
                                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false));
      }
    }
    return result.toArray(CommonProblemDescriptor.EMPTY_ARRAY);
  }

  @Override
  protected boolean queryExternalUsagesRequests(@NotNull final RefManager manager, @NotNull final GlobalJavaInspectionContext globalContext,
                                                @NotNull final ProblemDescriptionsProcessor processor) {
    for (RefElement entryPoint : globalContext.getEntryPointsManager(manager).getEntryPoints(manager)) {
      processor.ignoreElement(entryPoint);
    }

    PsiSearchHelper helper = PsiSearchHelper.getInstance(manager.getProject());
    AnalysisScope scope = manager.getScope();
    manager.iterate(new RefJavaVisitor() {
      @Override
      public void visitElement(@NotNull RefEntity refEntity) {
        if (!(refEntity instanceof RefMethod refMethod)) return;
        if (refMethod.isStatic() || refMethod.isConstructor() ||
            PsiModifier.PRIVATE.equals(refMethod.getAccessModifier())) {
          return;
        }

        List<RefParameter> unusedParameters = getUnusedParameters(refMethod);
        if (unusedParameters.isEmpty()) return;
        if (scope != null && scope.isTotalScope()) return;

        UMethod uastElement = refMethod.getUastElement();
        if (uastElement == null) return;
        PsiMethod element = uastElement.getJavaPsi();
        if (element == null) {
          return;
        }
        PsiMethod[] derived = OverridingMethodsSearch.search(element).toArray(PsiMethod.EMPTY_ARRAY);
        for (RefParameter refParameter : unusedParameters) {
          if (refMethod.isAbstract() && derived.length == 0) {
            refParameter.parameterReferenced(false);
            processor.ignoreElement(refParameter);
            continue;
          }
          int idx = refParameter.getIndex();
          for (PsiMethod method : derived) {
            if (scope != null && scope.contains(method)) continue;
            PsiParameter[] parameters = method.getParameterList().getParameters();
            if (idx >= parameters.length) continue;
            PsiParameter psiParameter = parameters[idx];
            if (ReferencesSearch.search(psiParameter, helper.getUseScope(psiParameter), false).findFirst() != null) {
              refParameter.parameterReferenced(false);
              processor.ignoreElement(refParameter);
              break;
            }
          }
        }
      }
    });
    return false;
  }

  @Override
  public String getHint(@NotNull final QuickFix fix) {
    if (fix instanceof AcceptSuggested) {
      return ((AcceptSuggested)fix).getHint();
    }
    return null;
  }

  @Override
  @Nullable
  public LocalQuickFix getQuickFix(final String hint) {
    return new AcceptSuggested(hint);
  }

  @NotNull
  private static List<RefParameter> getUnusedParameters(@NotNull RefMethod refMethod) {
    RefParameter[] methodParameters = refMethod.getParameters();
    if (methodParameters.length == 0) return Collections.emptyList();
    boolean checkDeep = !refMethod.isStatic() && !refMethod.isConstructor();
    ArrayList<RefParameter> res = new ArrayList<>();
    RefParameter[] result = methodParameters.clone();

    clearUsedParameters(refMethod, result, checkDeep);

    for (RefParameter parameter : result) {
      if (parameter != null && !PsiUtil.isIgnoredName(parameter.getName()) &&
          !((RefElementImpl)parameter).isSuppressed(UnusedSymbolLocalInspectionBase.UNUSED_PARAMETERS_SHORT_NAME,
                                                    UnusedSymbolLocalInspectionBase.UNUSED_ID)) {
        res.add(parameter);
      }
    }

    return res;
  }

  private static void clearUsedParameters(@NotNull RefOverridable refOverridable, RefParameter[] params, boolean checkDeep) {
    RefParameter[] methodParams;
    if (refOverridable instanceof RefMethod) {
      methodParams = ((RefMethod)refOverridable).getParameters();
    }
    else if (refOverridable instanceof RefFunctionalExpression) {
      methodParams = ((RefFunctionalExpression)refOverridable).getParameters().toArray(RefParameter[]::new);
    }
    else {
      return;
    }

    for (int i = 0; i < Math.min(methodParams.length, params.length); i++) {
      if (methodParams[i].isUsedForReading()) params[i] = null;
    }

    if (checkDeep) {
      for (RefOverridable reference : refOverridable.getDerivedReferences()) {
        clearUsedParameters(reference, params, true);
      }
    }
  }

  private static class AcceptSuggested implements LocalQuickFix, BatchQuickFix, Iconable {
    private final String myHint;

    AcceptSuggested(@NotNull String hint) {
      myHint = hint;
    }

    @NotNull
    String getHint() {
      return myHint;
    }

    @Override
    public @NotNull String getName() {
      return JavaBundle.message("inspection.unused.parameter.delete.quickfix", myHint);
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return JavaBundle.message("inspection.unused.parameter.delete.family");
    }

    @Override
    public Icon getIcon(int flags) {
      return AllIcons.Actions.Cancel;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement().getParent();
      if (!(element instanceof PsiNamedElement)) return;
      SafeDeleteHandler.invoke(project, new PsiElement[]{element}, false);
    }

    @Override
    public void applyFix(@NotNull Project project,
                         CommonProblemDescriptor @NotNull [] descriptors,
                         @NotNull List<PsiElement> psiElementsToIgnore,
                         @Nullable Runnable refreshViews) {
      final PsiElement[] elements = Stream.of(descriptors)
        .map(d -> ((ProblemDescriptor)d).getPsiElement().getParent())
        .filter(e -> e instanceof PsiNamedElement)
        .toArray(PsiElement[]::new);
      SafeDeleteHandler.invoke(project, elements, false);
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }
  }
}
