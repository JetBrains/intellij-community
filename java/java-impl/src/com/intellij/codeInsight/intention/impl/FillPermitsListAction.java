// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.intellij.util.SmartList;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.util.ObjectUtils.tryCast;

public class FillPermitsListAction extends BaseElementAtCaretIntentionAction {
  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!HighlightingFeature.SEALED_CLASSES.isAvailable(element)) return false;
    PsiIdentifier identifier = tryCast(element, PsiIdentifier.class);
    if (identifier == null) return false;
    PsiClass psiClass = tryCast(identifier.getParent(), PsiClass.class);
    if (psiClass == null || !(psiClass.getContainingFile() instanceof PsiJavaFile)) return false;
    PsiModifierList modifiers = psiClass.getModifierList();
    if (modifiers == null || !modifiers.hasExplicitModifier(PsiModifier.SEALED)) return false;
    PsiJavaCodeReferenceElement[] permittedRefs = getPermittedRefs(psiClass);
    Collection<PsiClass> sameFileInheritors = sameFileInheritors(psiClass).findAll();
    if (permittedRefs.length < sameFileInheritors.size()) return true;
    Set<PsiClass> permittedClasses = getPermittedClasses(permittedRefs);
    for (PsiClass inheritor : sameFileInheritors) {
      if (PsiUtil.isLocalOrAnonymousClass(inheritor)) return false;
      if (!permittedClasses.remove(inheritor)) return true;
    }
    for (PsiClass inheritor : outsideInheritors(psiClass)) {
      if (PsiUtil.isLocalOrAnonymousClass(inheritor)) return false;
      if (!permittedClasses.remove(inheritor)) return true;
    }
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (psiClass == null) return;
    PsiJavaFile psiJavaFile = tryCast(psiClass.getContainingFile(), PsiJavaFile.class);
    if (psiJavaFile == null) return;
    Set<PsiClass> permittedClasses = getPermittedClasses(getPermittedRefs(psiClass));
    Collection<String> missingInheritors = getMissingInheritors(project, editor, psiJavaFile, psiClass, permittedClasses);
    if (missingInheritors == null) return;
    fillPermitsList(psiClass, missingInheritors);
  }

  @Override
  public @IntentionName @NotNull String getText() {
    return getFamilyName();
  }

  @Override
  public @NotNull @IntentionFamilyName String getFamilyName() {
    return JavaBundle.message("intention.family.name.fill.permits.list");
  }

  private static PsiJavaCodeReferenceElement @NotNull [] getPermittedRefs(@NotNull PsiClass psiClass) {
    PsiReferenceList permitsList = psiClass.getPermitsList();
    return permitsList == null ? PsiJavaCodeReferenceElement.EMPTY_ARRAY : permitsList.getReferenceElements();
  }

  @Nullable
  private static Collection<String> getMissingInheritors(@NotNull Project project,
                                                         Editor editor,
                                                         @NotNull PsiJavaFile psiJavaFile,
                                                         @NotNull PsiClass psiClass,
                                                         @NotNull Set<PsiClass> permittedClasses) {
    Collection<String> missingInheritors = new SmartList<>();
    PsiJavaModule module = JavaModuleGraphUtil.findDescriptorByElement(psiClass);
    for (PsiClass inheritor : ClassInheritorsSearch.search(psiClass, false)) {
      String errorTitle = SealClassAction.checkInheritor(psiJavaFile, module, inheritor);
      if (errorTitle != null) {
        reportError(project, editor, JavaBundle.message(errorTitle));
        return null;
      }
      String qualifiedName = Objects.requireNonNull(inheritor.getQualifiedName());
      if (!permittedClasses.contains(inheritor)) missingInheritors.add(qualifiedName);
    }

    if (missingInheritors.isEmpty()) {
      String message = JavaBundle.message("intention.error.fill.permits.list.no.missing.inheritors");
      reportError(project, editor, message);
      return null;
    }
    return missingInheritors;
  }

  @NotNull
  private static Set<PsiClass> getPermittedClasses(PsiJavaCodeReferenceElement @NotNull [] permittedRefs) {
    return Arrays.stream(permittedRefs)
      .map(ref -> tryCast(ref.resolve(), PsiClass.class))
      .filter(Objects::nonNull).collect(Collectors.toSet());
  }

  private static @NotNull Query<PsiClass> outsideInheritors(@NotNull PsiClass parent) {
    GlobalSearchScope scope = GlobalSearchScope.notScope(GlobalSearchScope.fileScope(parent.getContainingFile()));
    return ClassInheritorsSearch.search(parent, scope, false);
  }

  private static @NotNull Query<PsiClass> sameFileInheritors(@NotNull PsiClass parent) {
    return ClassInheritorsSearch.search(parent, GlobalSearchScope.fileScope(parent.getContainingFile()), false);
  }

  private static void fillPermitsList(@NotNull PsiClass parent, @NotNull Collection<String> missingInheritors) {
    PsiReferenceList permitsList = parent.getPermitsList();
    PsiFileFactory factory = PsiFileFactory.getInstance(parent.getProject());
    if (permitsList == null) {
      PsiReferenceList implementsList = Objects.requireNonNull(parent.getImplementsList());
      String permitsClause = StreamEx.of(missingInheritors).sorted().joining(",", "permits ", "");
      parent.addAfter(createPermitsClause(factory, permitsClause), implementsList);
    }
    else {
      Stream<String> curClasses = Arrays.stream(permitsList.getReferenceElements()).map(ref -> ref.getQualifiedName());
      String permitsClause = StreamEx.of(missingInheritors).append(curClasses).sorted().joining(",", "permits ", "");
      permitsList.replace(createPermitsClause(factory, permitsClause));
    }
  }

  private static void reportError(@NotNull Project project, @NotNull Editor editor, @NotNull String message) {
    String title = JavaBundle.message("intention.fill.permits.list.hint.title");
    CommonRefactoringUtil.showErrorHint(project, editor, JavaBundle.message(message), title, null);
  }

  @NotNull
  private static PsiReferenceList createPermitsClause(@NotNull PsiFileFactory factory, @NotNull String permitsClause) {
    PsiJavaFile javaFile = (PsiJavaFile)factory.createFileFromText(JavaLanguage.INSTANCE, "class __Dummy " + permitsClause + "{}");
    PsiClass newClass = javaFile.getClasses()[0];
    return Objects.requireNonNull(newClass.getPermitsList());
  }
}
