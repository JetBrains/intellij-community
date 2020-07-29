// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static com.intellij.util.ObjectUtils.tryCast;

public class FillPermitsListFix extends LocalQuickFixAndIntentionActionOnPsiElement {

  public FillPermitsListFix(PsiIdentifier classIdentifier) {
    super(classIdentifier);
  }

  @Override
  public @IntentionName @NotNull String getText() {
    return JavaBundle.message("inspection.fill.permits.list.fix.name");
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiClass psiClass = PsiTreeUtil.getParentOfType(startElement, PsiClass.class);
    if (psiClass == null) return;
    PsiJavaFile psiJavaFile = tryCast(psiClass.getContainingFile(), PsiJavaFile.class);
    if (psiJavaFile == null) return;
    Set<PsiClass> permittedClasses = ContainerUtil.map2Set(psiClass.getPermitsListTypes(), PsiClassType::resolve);
    Collection<String> missingInheritors = getMissingInheritors(project, psiJavaFile, psiClass, permittedClasses);
    if (missingInheritors == null) return;
    fillPermitsList(psiClass, missingInheritors);
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return getText();
  }

  @Nullable
  private static Collection<String> getMissingInheritors(@NotNull Project project,
                                                         @NotNull PsiJavaFile psiJavaFile,
                                                         @NotNull PsiClass psiClass,
                                                         @NotNull Set<PsiClass> permittedClasses) {
    Collection<String> missingInheritors = new SmartList<>();
    PsiJavaModule module = JavaModuleGraphUtil.findDescriptorByElement(psiClass);
    for (PsiClass inheritor : DirectClassInheritorsSearch.search(psiClass)) {
      String errorTitle = SealClassAction.checkInheritor(psiJavaFile, module, inheritor);
      if (errorTitle != null) {
        reportError(project, JavaBundle.message(errorTitle));
        return null;
      }
      String qualifiedName = Objects.requireNonNull(inheritor.getQualifiedName());
      if (!permittedClasses.contains(inheritor)) missingInheritors.add(qualifiedName);
    }

    if (missingInheritors.isEmpty()) {
      String message = JavaBundle.message("inspection.fill.permits.list.no.missing.inheritors");
      reportError(project, message);
      return null;
    }
    return missingInheritors;
  }

  public static void fillPermitsList(@NotNull PsiClass parent, @NotNull Collection<String> missingInheritors) {
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

  private static void reportError(@NotNull Project project, @NotNull String message) {
    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (editor == null) return;
    HintManager.getInstance().showErrorHint(editor, message);
  }

  @NotNull
  private static PsiReferenceList createPermitsClause(@NotNull PsiFileFactory factory, @NotNull String permitsClause) {
    PsiJavaFile javaFile = (PsiJavaFile)factory.createFileFromText(JavaLanguage.INSTANCE, "class __Dummy " + permitsClause + "{}");
    PsiClass newClass = javaFile.getClasses()[0];
    return Objects.requireNonNull(newClass.getPermitsList());
  }
}
