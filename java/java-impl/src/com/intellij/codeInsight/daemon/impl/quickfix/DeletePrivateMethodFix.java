// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.openapi.util.Predicates;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class DeletePrivateMethodFix extends PsiBasedModCommandAction<PsiMethod> {
  private final ThreeState myDeleteCalled;
  
  public DeletePrivateMethodFix() {
    super(PsiMethod.class);
    myDeleteCalled = ThreeState.UNSURE;
  }

  public DeletePrivateMethodFix(@NotNull PsiMethod method) {
    this(method, ThreeState.UNSURE);
  }

  private DeletePrivateMethodFix(@NotNull PsiMethod method, ThreeState deleteCalled) {
    super(method);
    myDeleteCalled = deleteCalled;
  }

  @Override
  protected @NotNull Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiMethod method) {
    return switch (myDeleteCalled) {
      case UNSURE -> Presentation.of(JavaBundle.message("intention.name.delete.method", method.getName()));
      case YES -> Presentation.of(JavaBundle.message("intention.name.delete.method.with.callees"));
      case NO -> Presentation.of(JavaBundle.message("intention.name.delete.method.only"));
    };
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiMethod method) {
    if (!method.hasModifierProperty(PsiModifier.PRIVATE)) return ModCommand.nop();
    List<PsiElement> elements;
    if (myDeleteCalled == ThreeState.NO) {
      elements = List.of();
    }
    else {
      Set<PsiElement> finalElements = new HashSet<>();
      Deque<PsiMethod> toProcess = new ArrayDeque<>();
      toProcess.add(method);
      finalElements.add(method);
      while (!toProcess.isEmpty()) {
        PsiMethod next = toProcess.poll();
        List<PsiMethod> newMethods = ContainerUtil.filterIsInstance(computeReferencedCodeSafeToDelete(
          next, t -> t instanceof PsiMethod m && m.hasModifierProperty(PsiModifier.PRIVATE)), PsiMethod.class);
        for (PsiMethod newMethod : newMethods) {
          if (finalElements.add(newMethod)) {
            toProcess.add(newMethod);
          }
        }
      }
      elements = List.copyOf(finalElements);
    }
    if (elements.isEmpty()) {
      return ModCommand.psiUpdate(method, m -> m.delete());
    }
    if (myDeleteCalled == ThreeState.UNSURE) {
      return ModCommand.chooseAction(JavaBundle.message("intention.name.delete.method.title", method.getName()),
                                     new DeletePrivateMethodFix(method, ThreeState.YES),
                                     new DeletePrivateMethodFix(method, ThreeState.NO));
    }
    return ModCommand.psiUpdate(method, (m, updater) -> {
      List<PsiElement> writable = ContainerUtil.map(elements, updater::getWritable);
      writable.forEach(PsiElement::delete);
      m.delete();
    });
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("intention.family.name.delete.private.method");
  }

  public static @NotNull List<PsiElement> computeReferencedCodeSafeToDelete(@Nullable PsiMember psiMember) {
    return computeReferencedCodeSafeToDelete(psiMember, Predicates.alwaysTrue());
  }

  public static @NotNull List<PsiElement> computeReferencedCodeSafeToDelete(@Nullable PsiMember psiMember,
                                                                            @NotNull Predicate<? super PsiElement> additionalFilter) {
    if (psiMember instanceof PsiCompiledElement) return Collections.emptyList();
    final PsiElement body;
    if (psiMember instanceof PsiMethod method) {
      body = method.getBody();
    }
    else if (psiMember instanceof PsiField field) {
      body = field.getInitializer();
    }
    else if (psiMember instanceof PsiClass) {
      body = psiMember;
    }
    else {
      body = null;
    }
    if (body == null) {
      // Do not use List.of(), as call sites like to delete from the resulting list
      return Collections.emptyList();
    }
    final PsiClass containingClass = psiMember.getContainingClass();
    final Set<PsiElement> elementsToCheck = new HashSet<>();
    body.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        PsiElement resolved = expression.resolve();
        if (resolved instanceof PsiMethod || resolved instanceof PsiField) {
          ContainerUtil.addAllNotNull(elementsToCheck, resolved);
        }
      }

      @Override
      public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
        super.visitLiteralExpression(expression);
        PsiReference @NotNull [] references = expression.getReferences();
        for (PsiReference reference : references) {
          if (reference instanceof PsiPolyVariantReference ref) {
            PsiElement[] nonMembers = Arrays.stream(ref.multiResolve(false))
              .map(result -> result.getElement())
              .filter(e -> !(e instanceof PsiMember))
              .toArray(PsiElement[]::new);
            if (nonMembers.length < 10) {
              ContainerUtil.addAllNotNull(elementsToCheck, nonMembers);
            }
          }
          else {
            PsiElement resolve = reference.resolve();
            if (resolve != null && !(resolve instanceof PsiMember)) {
              elementsToCheck.add(resolve);
            }
          }
        }
      }
    });

    PsiFile containingFile = body.getContainingFile();
    PsiManager manager = containingFile.getManager();
    return elementsToCheck
      .stream()
      .filter(additionalFilter)
      .filter(manager::isInProject)
      .filter(m -> m != containingFile)
      .filter(m -> !PsiTreeUtil.isAncestor(psiMember, m, true))
      .filter(m -> !(m instanceof PsiMember member) ||
                   containingClass != null && containingClass.equals(member.getContainingClass()) && !psiMember.equals(m))
      .filter(m -> !(m instanceof PsiMethod method) ||
                   method.findDeepestSuperMethods().length == 0)
      .filter(m -> m.isPhysical())
      .filter(m -> usedOnlyIn(m, psiMember))
      .collect(Collectors.toList());
  }

  private static boolean usedOnlyIn(@NotNull PsiElement explored, @NotNull PsiMember place) {
    if (explored instanceof PsiNamedElement namedElement) {
      final String name = namedElement.getName();
      if (name != null &&
          PsiSearchHelper.getInstance(explored.getProject())
            .isCheapEnoughToSearch(name, GlobalSearchScope.projectScope(explored.getProject()), null) ==
          PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES) {
        return false;
      }
    }
    if (explored instanceof PsiClassOwner classOwner) {
      for (PsiClass aClass : classOwner.getClasses()) {
        if (!usedOnlyIn(aClass, place)) return false;
      }
      return true;
    }
    if (UnusedDeclarationInspectionBase.isDeclaredAsEntryPoint(explored)) return false;
    CommonProcessors.FindProcessor<PsiReference> findProcessor = new CommonProcessors.FindProcessor<>() {
      @Override
      protected boolean accept(PsiReference reference) {
        final PsiElement element = reference.getElement();
        return !PsiTreeUtil.isAncestor(place, element, true) &&
               !PsiTreeUtil.isAncestor(explored, element, true);
      }
    };
    return ReferencesSearch.search(explored).forEach(findProcessor);
  }
}
