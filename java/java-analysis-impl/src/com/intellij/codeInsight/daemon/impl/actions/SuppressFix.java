// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.JavaSuppressionUtil;
import com.intellij.codeInspection.SuppressionUtilCore;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.storage.ClassPathStorageUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SuppressFix extends AbstractBatchSuppressByNoInspectionCommentModCommandFix {
  private String myAlternativeID;

  public SuppressFix(@NotNull HighlightDisplayKey key) {
    this(key.getID());
    myAlternativeID = HighlightDisplayKey.getAlternativeID(key);
  }

  public SuppressFix(@NotNull String ID) {
    super(ID, false);
  }

  @Override
  @NotNull
  public String getText() {
    String myText = super.getText();
    return StringUtil.isEmpty(myText) ? JavaAnalysisBundle.message("suppress.inspection.member") : myText;
  }

  @Override
  @Nullable
  public PsiJavaDocumentedElement getContainer(final PsiElement context) {
    if (context == null || !BaseIntentionAction.canModify(context)) {
      return null;
    }
    final PsiFile containingFile = context.getContainingFile();
    if (containingFile == null) {
      // for PsiDirectory
      return null;
    }
    if (!containingFile.getLanguage().isKindOf(JavaLanguage.INSTANCE) || context instanceof PsiFile) {
      return null;
    }
    PsiElement container = context;
    while (container instanceof PsiAnonymousClass || !(container instanceof PsiJavaDocumentedElement) || container instanceof PsiTypeParameter) {
      container = PsiTreeUtil.getParentOfType(container, PsiJavaDocumentedElement.class);
      if (container == null) return null;
    }
    return container instanceof SyntheticElement || container instanceof PsiImplicitClass ? null : (PsiJavaDocumentedElement)container;
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, @NotNull final PsiElement context) {
    PsiJavaDocumentedElement container = getContainer(context);
    boolean isValid = container != null && !(container instanceof PsiMethod && container instanceof SyntheticElement);
    if (!isValid) {
      return false;
    }
    if (container instanceof PsiJavaModule) {
      setText(JavaAnalysisBundle.message("suppress.inspection.module"));
    }
    else if (container instanceof PsiClass) {
      setText(JavaAnalysisBundle.message("suppress.inspection.class"));
    }
    else if (container instanceof PsiMethod) {
      setText(JavaAnalysisBundle.message("suppress.inspection.method"));
    }
    else {
      setText(JavaAnalysisBundle.message("suppress.inspection.field"));
    }
    return true;
  }

  @Override
  public void invoke(@NotNull final Project project, @NotNull final PsiElement element) throws IncorrectOperationException {
    PsiJavaDocumentedElement container = getContainer(element);
    if (container == null) return;
    doSuppress(project, container);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return JavaAnalysisBundle.message("suppress.inspection.member");
  }

  private void doSuppress(@NotNull Project project, @NotNull PsiJavaDocumentedElement container) {
    if (container instanceof PsiModifierListOwner modifierOwner && use15Suppressions(container)) {
      final PsiModifierList modifierList = modifierOwner.getModifierList();
      if (modifierList != null) {
        JavaSuppressionUtil.addSuppressAnnotation(project, container, modifierOwner, getID(container));
      }
    }
    else {
      suppressByDocComment(project, container);
    }
  }

  private void suppressByDocComment(@NotNull Project project, PsiJavaDocumentedElement container) {
    PsiDocComment docComment = container.getDocComment();
    if (docComment == null) {
      String commentText = "/** @" + SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME + " " + getID(container) + "*/";
      docComment = JavaPsiFacade.getElementFactory(project).createDocCommentFromText(commentText);
      PsiElement firstChild = container.getFirstChild();
      container.addBefore(docComment, firstChild);
    }
    else {
      PsiDocTag noInspectionTag = docComment.findTagByName(SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME);
      if (noInspectionTag != null) {
        String tagText = noInspectionTag.getText() + ", " + getID(container);
        noInspectionTag.replace(JavaPsiFacade.getElementFactory(project).createDocTagFromText(tagText));
      }
      else {
        String tagText = "@" + SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME + " " + getID(container);
        docComment.add(JavaPsiFacade.getElementFactory(project).createDocTagFromText(tagText));
      }
    }
  }

  protected boolean use15Suppressions(@NotNull PsiJavaDocumentedElement container) {
    return JavaSuppressionUtil.canHave15Suppressions(container) &&
           !JavaSuppressionUtil.alreadyHas14Suppressions(container) &&
           !isInjectedToStringLiteral(container); // quotes will be imbalanced when insert annotation value in quotes into literal expression
  }

  private static boolean isInjectedToStringLiteral(@NotNull PsiJavaDocumentedElement container) {
    return JavaResolveUtil.findParentContextOfClass(container, PsiLiteralExpression.class, true) != null;
  }

  private String getID(@NotNull PsiElement place) {
    String id = getID(place, myAlternativeID);
    return id != null ? id : myID;
  }

  @Nullable
  static String getID(@NotNull PsiElement place, String alternativeID) {
    if (alternativeID != null) {
      final Module module = ModuleUtilCore.findModuleForPsiElement(place);
      if (module != null) {
        if (ClassPathStorageUtil.isClasspathStorage(module)) {
          return alternativeID;
        }
      }
    }

    return null;
  }

  @Override
  public int getPriority() {
    return 40;
  }
}
