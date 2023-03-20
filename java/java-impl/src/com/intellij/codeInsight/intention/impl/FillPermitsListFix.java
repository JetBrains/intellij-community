// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.actions.IntentionActionWithFixAllOption;
import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.SealedUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

import static com.intellij.util.ObjectUtils.tryCast;

public class FillPermitsListFix extends LocalQuickFixAndIntentionActionOnPsiElement implements IntentionActionWithFixAllOption {

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
    SealedUtils.fillPermitsList(psiClass, missingInheritors);
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
      String errorTitle = SealedUtils.checkInheritor(psiJavaFile, module, inheritor);
      if (errorTitle != null) {
        reportError(project, JavaBundle.message(errorTitle));
        return null;
      }
      String qualifiedName = Objects.requireNonNull(inheritor.getQualifiedName());
      if (!ContainerUtil.exists(permittedClasses, cls -> cls.isEquivalentTo(inheritor))) missingInheritors.add(qualifiedName);
    }

    if (missingInheritors.isEmpty()) {
      String message = JavaBundle.message("inspection.fill.permits.list.no.missing.inheritors");
      reportError(project, message);
      return null;
    }
    return missingInheritors;
  }

  private static void reportError(@NotNull Project project, @NotNull @NlsContexts.HintText String message) {
    if (IntentionPreviewUtils.isIntentionPreviewActive()) return;
    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (editor == null) return;
    HintManager.getInstance().showErrorHint(editor, message);
  }
}
