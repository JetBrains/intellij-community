/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.ModCommandAwareExternalAnnotationsManager;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.*;
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
  @NlsSafe private final String myAnnotationName;
  
  public DeannotateIntentionAction() {
    myAnnotationName = null;
  }

  public DeannotateIntentionAction(@NotNull String annotationName) {
    myAnnotationName = annotationName;
  }

  @Override
  @NotNull
  public String getFamilyName() {
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
      if (annotations != null && annotations.length > 0) {
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
    if (externalAnnotations == null) return ModCommand.nop();
    return ModCommand.chooseAction(JavaBundle.message("deannotate.intention.chooser.title"),
                                   ContainerUtil.map(externalAnnotations, anno -> new DeannotateIntentionAction(
                                     Objects.requireNonNull(anno.getQualifiedName()))));
  }
}