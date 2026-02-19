// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MakeAnnotationExternal extends PsiBasedModCommandAction<PsiAnnotation> {
  
  public MakeAnnotationExternal() {
    super(PsiAnnotation.class);
  }

  @Override
  public @Nls @NotNull String getFamilyName() {
    return JavaBundle.message("intention.text.annotate.externally");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiAnnotation annotation) {
    if (annotation.getQualifiedName() != null && annotation.getManager().isInProject(annotation)) {
      PsiModifierListOwner modifierListOwner = PsiTreeUtil.getParentOfType(annotation, PsiModifierListOwner.class);
      if (modifierListOwner != null && ExternalAnnotationsManagerImpl.areExternalAnnotationsApplicable(modifierListOwner)) {
        VirtualFile virtualFile = PsiUtilCore.getVirtualFile(modifierListOwner);
        if (JavaCodeStyleSettings.getInstance(context.file()).USE_EXTERNAL_ANNOTATIONS ||
            virtualFile != null && ExternalAnnotationsManager.getInstance(context.project()).hasAnnotationRootsForFile(virtualFile)) {
          return Presentation.of(JavaBundle.message("intention.text.annotate.externally"));
        }
      }
    }
    return null;
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiAnnotation annotation) {
    final PsiModifierListOwner owner = PsiTreeUtil.getParentOfType(annotation, PsiModifierListOwner.class);
    if (owner == null) return ModCommand.nop();
    String qualifiedName = annotation.getQualifiedName();
    if (qualifiedName == null) return ModCommand.nop();

    ModCommandAwareExternalAnnotationsManager manager =
      (ModCommandAwareExternalAnnotationsManager)ExternalAnnotationsManager.getInstance(context.project());

    return manager.annotateExternallyModCommand(owner, qualifiedName, annotation.getParameterList().getAttributes())
      .andThen(ModCommand.psiUpdate(annotation, PsiAnnotation::delete));
  }
}
