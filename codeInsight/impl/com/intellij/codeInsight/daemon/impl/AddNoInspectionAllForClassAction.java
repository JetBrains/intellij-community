package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: May 13, 2005
 */
public class AddNoInspectionAllForClassAction extends AddNoInspectionDocTagAction{
  @NonNls private static final String ID = "ALL";
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.AddNoInspectionAllForClassAction");

  public AddNoInspectionAllForClassAction(final PsiElement context) {
    super(ID, context);
  }

  @Nullable protected PsiDocCommentOwner getContainer() {
    PsiDocCommentOwner container = super.getContainer();
    if (container == null){
      return null;
    }
    while (container != null ) {
      final PsiClass parentClass = PsiTreeUtil.getParentOfType(container, PsiClass.class);
      if (parentClass == null && container instanceof PsiClass){
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

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiDocCommentOwner container = getContainer();
    LOG.assertTrue(container != null);
    final ReadonlyStatusHandler.OperationStatus status = ReadonlyStatusHandler.getInstance(project)
      .ensureFilesWritable(container.getContainingFile().getVirtualFile());
    if (status.hasReadonlyFiles()) return;
    PsiDocComment docComment = container.getDocComment();
    if (docComment != null){
      PsiDocTag noInspectionTag = docComment.findTagByName(GlobalInspectionContextImpl.SUPPRESS_INSPECTIONS_TAG_NAME);
      if (noInspectionTag != null) {
        String tagText = "@" + GlobalInspectionContextImpl.SUPPRESS_INSPECTIONS_TAG_NAME + " " + ID;
        noInspectionTag.replace(myContext.getManager().getElementFactory().createDocTagFromText(tagText, null));
        return;
      }
    }
    super.invoke(project, editor, file);
  }
}
