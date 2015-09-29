/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class SuppressManagerImpl extends SuppressManager {
  private static final Logger LOG = Logger.getInstance(SuppressManager.class);

  @Override
  @NotNull
  public SuppressIntentionAction[] createSuppressActions(@NotNull final HighlightDisplayKey displayKey) {
    SuppressQuickFix[] batchSuppressActions = createBatchSuppressActions(displayKey);
    return SuppressIntentionActionFromFix.convertBatchToSuppressIntentionActions(batchSuppressActions);
  }

  @NotNull
  @Override
  public SuppressQuickFix[] getSuppressActions(@Nullable PsiElement element, @NotNull String toolId) {
    final HighlightDisplayKey displayKey = HighlightDisplayKey.findById(toolId);
    LOG.assertTrue(displayKey != null, "Display key is null for `" + toolId + "` tool");
    return createBatchSuppressActions(displayKey);
  }

  @Override
  public boolean isSuppressedFor(@NotNull final PsiElement element, @NotNull final String toolId) {
    return JavaSuppressionUtil.getElementToolSuppressedIn(element, toolId) != null;
  }

  @Override
  @Nullable
  public PsiElement getElementMemberSuppressedIn(@NotNull final PsiDocCommentOwner owner, @NotNull final String inspectionToolID) {
    return JavaSuppressionUtil.getElementMemberSuppressedIn(owner, inspectionToolID);
  }

  @Override
  @Nullable
  public PsiElement getAnnotationMemberSuppressedIn(@NotNull final PsiModifierListOwner owner, @NotNull final String inspectionToolID) {
    return JavaSuppressionUtil.getAnnotationMemberSuppressedIn(owner, inspectionToolID);
  }

  @Override
  @Nullable
  public PsiElement getDocCommentToolSuppressedIn(@NotNull final PsiDocCommentOwner owner, @NotNull final String inspectionToolID) {
    return JavaSuppressionUtil.getDocCommentToolSuppressedIn(owner, inspectionToolID);
  }

  @Override
  @NotNull
  public Collection<String> getInspectionIdsSuppressedInAnnotation(@NotNull final PsiModifierListOwner owner) {
    return JavaSuppressionUtil.getInspectionIdsSuppressedInAnnotation(owner);
  }

  @Override
  @Nullable
  public String getSuppressedInspectionIdsIn(@NotNull PsiElement element) {
    return JavaSuppressionUtil.getSuppressedInspectionIdsIn(element);
  }

  @Override
  @Nullable
  public PsiElement getElementToolSuppressedIn(@NotNull final PsiElement place, @NotNull final String toolId) {
    return JavaSuppressionUtil.getElementToolSuppressedIn(place, toolId);
  }

  @Override
  public boolean canHave15Suppressions(@NotNull final PsiElement file) {
    return JavaSuppressionUtil.canHave15Suppressions(file);
  }

  @Override
  public boolean alreadyHas14Suppressions(@NotNull final PsiDocCommentOwner commentOwner) {
    return JavaSuppressionUtil.alreadyHas14Suppressions(commentOwner);
  }
}
