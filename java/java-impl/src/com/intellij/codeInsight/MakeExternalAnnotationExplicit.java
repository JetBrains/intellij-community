// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public final class MakeExternalAnnotationExplicit implements ModCommandAction {

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("intention.family.make.external.annotations.explicit");
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    if (!BaseIntentionAction.canModify(context.file())) return null;
    final PsiElement leaf = context.findLeaf();
    PsiFile file = context.file();
    final PsiModifierListOwner owner = NonCodeAnnotationsLineMarkerProvider.getAnnotationOwner(leaf);
    if (owner != null && owner.getLanguage().isKindOf(JavaLanguage.INSTANCE) && isWritable(owner) &&
        ModuleUtilCore.findModuleForPsiElement(file) != null &&
        PsiUtil.getLanguageLevel(file).isAtLeast(LanguageLevel.JDK_1_5)) {
      final PsiAnnotation[] annotations = getAnnotations(context.project(), owner);
      if (annotations.length > 0) {
        final String annos = StringUtil.join(annotations, annotation -> {
          final PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
          final String name = nameRef != null ? nameRef.getReferenceName() : annotation.getQualifiedName();
          return "@" + name + annotation.getParameterList().getText();
        }, " ");
        return Presentation.of(CommonQuickFixBundle.message("fix.insert.x", annos));
      }
    }
   
    return null;
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    final PsiElement leaf = context.findLeaf();
    final PsiFile file = context.file();
    final Project project = context.project();
    final PsiModifierListOwner owner = NonCodeAnnotationsLineMarkerProvider.getAnnotationOwner(leaf);
    assert owner != null;
    final PsiModifierList modifierList = owner.getModifierList();
    assert modifierList != null;
    final Module module = ModuleUtilCore.findModuleForPsiElement(file);
    assert module != null;

    ModCommandAwareExternalAnnotationsManager externalAnnotationsManager = ObjectUtils.tryCast(
      ExternalAnnotationsManager.getInstance(project), ModCommandAwareExternalAnnotationsManager.class);

    PsiAnnotation[] annotations = getAnnotations(project, owner);
    return ModCommand.psiUpdate(modifierList, writableList -> {
      for (PsiAnnotation anno : annotations) {
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(writableList.addAfter(anno, null));
      }
    }).andThen(externalAnnotationsManager == null ? ModCommand.nop() : 
               externalAnnotationsManager.deannotateModCommand(List.of(owner), ContainerUtil.map(annotations, PsiAnnotation::getQualifiedName)));
  }

  private static PsiAnnotation @NotNull [] getAnnotations(@NotNull Project project, PsiModifierListOwner owner) {
    PsiAnnotation[] annotations = ExternalAnnotationsManager.getInstance(project).findExternalAnnotations(owner);
    if (annotations == null) {
      return PsiAnnotation.EMPTY_ARRAY;
    }
    else {
      JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      return Arrays.stream(annotations).filter(anno -> {
        String qualifiedName = anno.getQualifiedName();
        return qualifiedName != null &&
               facade.findClass(qualifiedName, owner.getResolveScope()) != null &&
               !owner.hasAnnotation(qualifiedName);
      }).toArray(PsiAnnotation[]::new);
    }
  }

  private static boolean isWritable(PsiModifierListOwner owner) {
    if (owner instanceof PsiCompiledElement) return false;

    VirtualFile vFile = PsiUtilCore.getVirtualFile(owner);
    return vFile != null && vFile.isInLocalFileSystem();
  }
}
