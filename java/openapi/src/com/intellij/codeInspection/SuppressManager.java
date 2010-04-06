/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 24-Dec-2007
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

public abstract class SuppressManager {
  public static final String SUPPRESS_INSPECTIONS_ANNOTATION_NAME = "java.lang.SuppressWarnings";

  public static SuppressManager getInstance() {
    return ServiceManager.getService(SuppressManager.class);
  }

  public abstract SuppressIntentionAction[] createSuppressActions(@NotNull HighlightDisplayKey key);

  public abstract boolean isSuppressedFor(@NotNull PsiElement element, final String toolId);

  public abstract PsiElement getElementMemberSuppressedIn(@NotNull PsiDocCommentOwner owner, final String inspectionToolID);

  @Nullable
  public abstract PsiElement getAnnotationMemberSuppressedIn(@NotNull PsiModifierListOwner owner, String inspectionToolID);

  @Nullable
  public abstract PsiElement getDocCommentToolSuppressedIn(@NotNull PsiDocCommentOwner owner, String inspectionToolID);

  @NotNull
  public abstract Collection<String> getInspectionIdsSuppressedInAnnotation(@NotNull PsiModifierListOwner owner);

  @Nullable
  public abstract String getSuppressedInspectionIdsIn(@NotNull PsiElement element);

  @Nullable
  public abstract PsiElement getElementToolSuppressedIn(@NotNull PsiElement place, String toolId);

  public abstract boolean canHave15Suppressions(@NotNull PsiElement file);

  public abstract boolean alreadyHas14Suppressions(@NotNull PsiDocCommentOwner commentOwner);
}