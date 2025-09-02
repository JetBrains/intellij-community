// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Predicates;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.refactoring.safeDelete.SafeDeleteProcessor;
import com.intellij.util.CommonProcessors;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SafeDeleteFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  public SafeDeleteFix(@NotNull PsiElement element) {
    super(element);
  }

  @Override
  public @NotNull String getText() {
    PsiElement startElement = getStartElement();
    String text = startElement == null
               ? ""
               : HighlightMessageUtil.getSymbolName(startElement, PsiSubstitutor.EMPTY,
                                                    PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.USE_INTERNAL_CANONICAL_TEXT);
    return QuickFixBundle.message("safe.delete.text", ObjectUtils.notNull(text, ""));
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("safe.delete.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile psiFile,
                             @Nullable Editor editor,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    // Should not be available for injected file, otherwise preview won't work
    return startElement.getContainingFile() == psiFile;
  }
  
  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile psiFile,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    if (!FileModificationService.getInstance().prepareFileForWrite(psiFile)) return;
    final PsiElement[] elements = {startElement};
    if (startElement instanceof PsiParameter) {
      SafeDeleteProcessor.createInstance(project, null, elements, false, false, true).run();
    } else {
      SafeDeleteHandler.invoke(project, elements, true);
    }
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    PsiElement element = PsiTreeUtil.findSameElementInCopy(getStartElement(), psiFile);
    PsiElement parent = element.getParent();
    if (parent instanceof PsiClassOwner classOwner && classOwner.getClasses().length == 1 && classOwner.getClasses()[0] == element) {
      var doc = psiFile.getViewProvider().getDocument();
      doc.deleteString(0, doc.getTextLength());
    }
    else {
      element.delete();
    }
    return IntentionPreviewInfo.DIFF;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  public static @NotNull List<PsiElement> computeReferencedCodeSafeToDelete(@Nullable PsiMember psiMember) {
    return computeReferencedCodeSafeToDelete(psiMember, Predicates.alwaysTrue());
  }

  public static @NotNull List<PsiElement> computeReferencedCodeSafeToDelete(@Nullable PsiMember psiMember,
                                                                            @NotNull Predicate<? super PsiElement> additionalFilter) {
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
