// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.preview;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.java.JavaBundle;
import com.intellij.java.codeserver.core.JavaPreviewFeatureUtil;
import com.intellij.openapi.module.JdkApiCompatibilityService;
import com.intellij.openapi.module.JdkApiCompatibilityService.LevelInfo;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class PreviewFeatureInspection extends LocalInspectionTool {

  @Override
  public @NotNull String getID() {
    return "preview";
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    LanguageLevel level = PsiUtil.getLanguageLevel(holder.getFile());
    boolean preview = level.isPreview();
    return new PsiElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        JavaPreviewFeatureUtil.PreviewFeatureUsage usage = JavaPreviewFeatureUtil.getPreviewFeatureUsage(element);
        if (usage != null) {
          if (!preview && !usage.isReflective()) {
            // reported as a compilation error
            return;
          } 
          // Do not report warnings in imports, because they cannot be suppressed and javac doesn't report them
          if (element.getParent() instanceof PsiImportStatementBase) return;
          if (element instanceof PsiReferenceExpression ref) {
            PsiElement nameElement = ref.getReferenceNameElement();
            if (nameElement != null) {
              element = nameElement;
            }
          }
          holder.registerProblem(element,
                                 usage.isReflective()
                                 ? JavaBundle.message("preview.api.usage.reflective", usage.targetName())
                                 : JavaBundle.message("preview.api.usage", usage.targetName()));
        }
        else if (element instanceof PsiJavaCodeReferenceElement refElement) {
          PsiElement resolved = refElement.resolve();
          if (resolved instanceof PsiMember owner) {
            LevelInfo info = JdkApiCompatibilityService.getInstance().firstCompatibleLanguageLevelInfo(owner, level);
            if (info != null &&
                info.outOfPreviewLevel() != null &&
                level.isAtLeast(info.firstAppearLevel()) &&
                level.isLessThan(info.outOfPreviewLevel())) {
              // firstCompatibleLanguageLevelInfo would return null if containing class, its qualified name, or member name is null
              String name = owner instanceof PsiClass cls ?
                            cls.getQualifiedName() : 
                            Objects.requireNonNull(owner.getContainingClass()).getQualifiedName() + "#" + owner.getName();
              holder.registerProblem(element, JavaBundle.message("preview.api.usage", name));
            }
          }
        }
      }
    };
  }
}
