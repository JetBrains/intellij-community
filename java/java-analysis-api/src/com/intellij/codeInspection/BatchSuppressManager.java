/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface BatchSuppressManager {
  String SUPPRESS_INSPECTIONS_ANNOTATION_NAME = "java.lang.SuppressWarnings";

  class SERVICE {
    public static BatchSuppressManager getInstance() {
      return ServiceManager.getService(BatchSuppressManager.class);
    }
  }
  @NotNull
  SuppressQuickFix[] createBatchSuppressActions(@NotNull HighlightDisplayKey key);

  boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String toolId);

  PsiElement getElementMemberSuppressedIn(@NotNull PsiDocCommentOwner owner, @NotNull String inspectionToolID);

  @Nullable
  PsiElement getAnnotationMemberSuppressedIn(@NotNull PsiModifierListOwner owner, @NotNull String inspectionToolID);

  @Nullable
  PsiElement getDocCommentToolSuppressedIn(@NotNull PsiDocCommentOwner owner, @NotNull String inspectionToolID);

  @NotNull
  Collection<String> getInspectionIdsSuppressedInAnnotation(@NotNull PsiModifierListOwner owner);

  @Nullable
  String getSuppressedInspectionIdsIn(@NotNull PsiElement element);

  @Nullable
  PsiElement getElementToolSuppressedIn(@NotNull PsiElement place, @NotNull String toolId);

  boolean canHave15Suppressions(@NotNull PsiElement file);

  boolean alreadyHas14Suppressions(@NotNull PsiDocCommentOwner commentOwner);
}
