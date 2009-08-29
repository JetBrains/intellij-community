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

  public abstract SuppressIntentionAction[] createSuppressActions(HighlightDisplayKey key);

  public abstract boolean isSuppressedFor(final PsiElement element, final String toolId);

  public abstract PsiElement getElementMemberSuppressedIn(final PsiDocCommentOwner owner, final String inspectionToolID);

  @Nullable
  public abstract PsiElement getAnnotationMemberSuppressedIn(PsiModifierListOwner owner, String inspectionToolID);

  @Nullable
  public abstract PsiElement getDocCommentToolSuppressedIn(PsiDocCommentOwner owner, String inspectionToolID);

  @NotNull
  public abstract Collection<String> getInspectionIdsSuppressedInAnnotation(PsiModifierListOwner owner);

  @Nullable
  public abstract String getSuppressedInspectionIdsIn(PsiElement element);

  @Nullable
  public abstract PsiElement getElementToolSuppressedIn(PsiElement place, String toolId);

  public abstract boolean canHave15Suppressions(PsiElement file);

  public abstract boolean alreadyHas14Suppressions(PsiDocCommentOwner commentOwner);
}