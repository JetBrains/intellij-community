// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.ModCommandAwareExternalAnnotationsManager;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class DeannotateIntentionAction implements ModCommandAction {
  private final @NlsSafe String myAnnotationName;
  
  public DeannotateIntentionAction() {
    myAnnotationName = null;
  }

  public DeannotateIntentionAction(@NotNull String annotationName) {
    myAnnotationName = annotationName;
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("deannotate.intention.action.family.name");
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    if (myAnnotationName != null) {
      return Presentation.of("@" + myAnnotationName);
    }
    PsiModifierListOwner listOwner = AddAnnotationPsiFix.getContainer(context.file(), context.offset(), true);
    if (listOwner != null) {
      final ExternalAnnotationsManager externalAnnotationsManager = ExternalAnnotationsManager.getInstance(context.project());
      final PsiAnnotation[] annotations = externalAnnotationsManager.findExternalAnnotations(listOwner);
      if (annotations.length > 0) {
        String message;
        if (annotations.length == 1) {
          message = JavaBundle.message("deannotate.intention.action.text", "@" + annotations[0].getQualifiedName());
        } else {
          message = JavaBundle.message("deannotate.intention.action.several.text");
        }
        final List<PsiFile> files = externalAnnotationsManager.findExternalAnnotationsFiles(listOwner);
        if (files == null || files.isEmpty()) return null;
        final VirtualFile virtualFile = files.get(0).getVirtualFile();
        if (virtualFile != null && (virtualFile.isWritable() || virtualFile.isInLocalFileSystem())) {
          return Presentation.of(message).withPriority(PriorityAction.Priority.LOW);
        }
      }
    }
    return null;
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    PsiModifierListOwner listOwner = AddAnnotationPsiFix.getContainer(context.file(), context.offset(), true);
    if (listOwner == null) return ModCommand.nop();
    var annotationsManager =
      ObjectUtils.tryCast(ExternalAnnotationsManager.getInstance(context.project()), ModCommandAwareExternalAnnotationsManager.class);
    if (annotationsManager == null) return ModCommand.nop();
    if (myAnnotationName != null) {
      return annotationsManager.deannotateModCommand(List.of(listOwner), List.of(myAnnotationName));
    }
    final PsiAnnotation[] externalAnnotations = annotationsManager.findExternalAnnotations(listOwner);
    if (externalAnnotations.length == 0) return ModCommand.nop();
    return ModCommand.chooseAction(JavaBundle.message("deannotate.intention.chooser.title"),
                                   ContainerUtil.map(externalAnnotations, anno -> new DeannotateIntentionAction(
                                     Objects.requireNonNull(anno.getQualifiedName()))));
  }
}