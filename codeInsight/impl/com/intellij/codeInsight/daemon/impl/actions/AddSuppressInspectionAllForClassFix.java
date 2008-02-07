package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.SuppressManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: May 13, 2005
 */
public class AddSuppressInspectionAllForClassFix extends AddSuppressInspectionFix {
  @NonNls private static final String ID = "ALL";
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.actions.AddNoInspectionAllForClassFix");

  public AddSuppressInspectionAllForClassFix() {
    super(ID);
  }

  public AddSuppressInspectionAllForClassFix(final String id) {
   super(id);
  }

  @Nullable
  protected PsiDocCommentOwner getContainer(final PsiElement element) {
    PsiDocCommentOwner container = super.getContainer(element);
    if (container == null) {
      return null;
    }
    while (container != null) {
      final PsiClass parentClass = PsiTreeUtil.getParentOfType(container, PsiClass.class);
      if (parentClass == null && container instanceof PsiClass) {
        return container;
      }
      container = parentClass;
    }
    return container;
  }

  @NotNull
  public String getText() {
    return InspectionsBundle.message("suppress.all.for.class");
  }

  public void invoke(final Project project, final Editor editor, final PsiElement element) throws IncorrectOperationException {
    final PsiDocCommentOwner container = getContainer(element);
    LOG.assertTrue(container != null);
    final ReadonlyStatusHandler.OperationStatus status = ReadonlyStatusHandler.getInstance(project)
      .ensureFilesWritable(container.getContainingFile().getVirtualFile());
    if (status.hasReadonlyFiles()) return;
    if (use15Suppressions(container)) {
      final PsiModifierList modifierList = container.getModifierList();
      if (modifierList != null) {
        final PsiAnnotation annotation = modifierList.findAnnotation(SuppressManagerImpl.SUPPRESS_INSPECTIONS_ANNOTATION_NAME);
        if (annotation != null) {
          annotation.replace(JavaPsiFacade.getInstance(project).getElementFactory().createAnnotationFromText("@" + SuppressManagerImpl.SUPPRESS_INSPECTIONS_ANNOTATION_NAME + "({\"" + ID + "\"})", container));
          return;
        }
      }
    }
    else {
      PsiDocComment docComment = container.getDocComment();
      if (docComment != null) {
        PsiDocTag noInspectionTag = docComment.findTagByName(SuppressManagerImpl.SUPPRESS_INSPECTIONS_TAG_NAME);
        if (noInspectionTag != null) {
          String tagText = "@" + SuppressManagerImpl.SUPPRESS_INSPECTIONS_TAG_NAME + " " + ID;
          noInspectionTag.replace(JavaPsiFacade.getInstance(project).getElementFactory().createDocTagFromText(tagText, null));
          DaemonCodeAnalyzer.getInstance(project).restart();
          return;
        }
      }
    }

    super.invoke(project, editor, element);
  }
}
