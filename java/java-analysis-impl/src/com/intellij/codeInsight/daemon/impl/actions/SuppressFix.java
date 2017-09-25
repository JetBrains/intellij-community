/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.JavaSuppressionUtil;
import com.intellij.codeInspection.SuppressionUtilCore;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoUtil;
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

/**
 * @author ven
 */
public class SuppressFix extends AbstractBatchSuppressByNoInspectionCommentFix {
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
    return StringUtil.isEmpty(myText) ? InspectionsBundle.message("suppress.inspection.member") : myText;
  }

  @Override
  @Nullable
  public PsiJavaDocumentedElement getContainer(final PsiElement context) {
    if (context == null || !context.getManager().isInProject(context)) {
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
    return container instanceof SyntheticElement ? null : (PsiJavaDocumentedElement)container;
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, @NotNull final PsiElement context) {
    PsiJavaDocumentedElement container = getContainer(context);
    boolean isValid = container != null && !(container instanceof PsiMethod && container instanceof SyntheticElement);
    if (!isValid) {
      return false;
    }
    if (container instanceof PsiJavaModule) {
      setText(InspectionsBundle.message("suppress.inspection.module"));
    }
    else if (container instanceof PsiClass) {
      setText(InspectionsBundle.message("suppress.inspection.class"));
    }
    else if (container instanceof PsiMethod) {
      setText(InspectionsBundle.message("suppress.inspection.method"));
    }
    else {
      setText(InspectionsBundle.message("suppress.inspection.field"));
    }
    return true;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void invoke(@NotNull final Project project, @NotNull final PsiElement element) throws IncorrectOperationException {
    if (doSuppress(project, getContainer(element))) return;
    // todo suppress
    //DaemonCodeAnalyzer.getInstance(project).restart();
    UndoUtil.markPsiFileForUndo(element.getContainingFile());
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return InspectionsBundle.message("suppress.inspection.member");
  }

  private boolean doSuppress(@NotNull Project project, PsiJavaDocumentedElement container) {
    assert container != null;
    if (container instanceof PsiModifierListOwner && use15Suppressions(container)) {
      final PsiModifierListOwner modifierOwner = (PsiModifierListOwner)container;
      final PsiModifierList modifierList = modifierOwner.getModifierList();
      if (modifierList != null) {
        JavaSuppressionUtil.addSuppressAnnotation(project, container, modifierOwner, getID(container));
      }
    }
    else {
      WriteCommandAction.runWriteCommandAction(project, null, null, () -> suppressByDocComment(project, container), container.getContainingFile());
    }
    return false;
  }

  private void suppressByDocComment(@NotNull Project project, PsiJavaDocumentedElement container) {
    PsiDocComment docComment = container.getDocComment();
    PsiManager manager = PsiManager.getInstance(project);
    if (docComment == null) {
      String commentText = "/** @" + SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME + " " + getID(container) + "*/";
      docComment = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createDocCommentFromText(commentText);
      PsiElement firstChild = container.getFirstChild();
      container.addBefore(docComment, firstChild);
    }
    else {
      PsiDocTag noInspectionTag = docComment.findTagByName(SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME);
      if (noInspectionTag != null) {
        String tagText = noInspectionTag.getText() + ", " + getID(container);
        noInspectionTag.replace(JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createDocTagFromText(tagText));
      }
      else {
        String tagText = "@" + SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME + " " + getID(container);
        docComment.add(JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createDocTagFromText(tagText));
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
}
