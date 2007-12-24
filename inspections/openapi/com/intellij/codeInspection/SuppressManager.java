/*
 * User: anna
 * Date: 24-Dec-2007
 */
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.regex.Pattern;

public abstract class SuppressManager {
  @NonNls public static final String SUPPRESS_INSPECTIONS_TAG_NAME = "noinspection";
  public static final String SUPPRESS_INSPECTIONS_ANNOTATION_NAME = "java.lang.SuppressWarnings";
  @NonNls public static final Pattern SUPPRESS_IN_LINE_COMMENT_PATTERN =
    Pattern.compile("//\\s*" + SUPPRESS_INSPECTIONS_TAG_NAME + "\\s+(\\w+(s*,\\w+)*)");

  public static SuppressManager getInstance() {
    return ServiceManager.getService(SuppressManager.class);
  }

  public abstract IntentionAction[] createSuppressActions(HighlightDisplayKey key, PsiElement psiElement);

  public abstract boolean isSuppressedFor(final PsiElement element, final String toolId);

  public abstract PsiElement getElementMemberSuppressedIn(final PsiDocCommentOwner owner, final String inspectionToolID);

  @Nullable
  public abstract PsiElement getAnnotationMemberSuppressedIn(PsiModifierListOwner owner, String inspectionToolID);

  @Nullable
  public abstract PsiElement getDocCommentToolSuppressedIn(PsiDocCommentOwner owner, String inspectionToolID);

  public abstract boolean isInspectionToolIdMentioned(String inspectionsList, String inspectionToolID);

  @NotNull
  public abstract Collection<String> getInspectionIdsSuppressedInAnnotation(PsiModifierListOwner owner);

  @Nullable
  public abstract String getSuppressedInspectionIdsIn(PsiElement element);

  @Nullable
  public abstract PsiElement getElementToolSuppressedIn(PsiElement place, String toolId);

  public abstract boolean canHave15Suppressions(PsiFile file);

  public abstract boolean alreadyHas14Suppressions(PsiDocCommentOwner commentOwner);
}