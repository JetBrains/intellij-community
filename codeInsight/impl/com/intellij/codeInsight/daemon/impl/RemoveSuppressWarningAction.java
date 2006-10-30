package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RemoveSuppressWarningAction implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.RemoveSuppressWarningAction");

  private final String myID;

  public RemoveSuppressWarningAction(String ID) {
    myID = ID;
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("remove.suppression.action.family");
  }

  public void applyFix(@NotNull Project project, ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    try {
      if (!CodeInsightUtil.prepareFileForWrite(element.getContainingFile())) return;
      if (element instanceof PsiAnnotation) {
        removeFromAnnotation((PsiAnnotation)element);
      }
      else if (element instanceof PsiDocComment) {
        removeFromJavaDoc((PsiDocComment)element);
      }
      else if (element instanceof PsiComment) {
        removeFromComment((PsiComment)element);
      }
      else {
        LOG.error("invalid element type: " + element);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @NotNull
  public String getName() {
    return QuickFixBundle.message("remove.suppression.action.name", myID);
  }

  private void removeFromComment(final PsiComment comment) throws IncorrectOperationException {
    String newText = removeFromElementText(comment);
    if (newText == null) {
      comment.delete();
    }
    else {
      PsiComment newComment = comment.getManager().getElementFactory().createCommentFromText("// " + GlobalInspectionContextImpl.SUPPRESS_INSPECTIONS_TAG_NAME+" "+newText, comment);
      comment.replace(newComment);
    }
  }

  private void removeFromJavaDoc(PsiDocComment docComment) throws IncorrectOperationException {
    PsiDocTag tag = docComment.findTagByName(GlobalInspectionContextImpl.SUPPRESS_INSPECTIONS_TAG_NAME);
    if (tag == null) return;
    String newText = removeFromElementText(tag.getValueElement());
    if (newText == null) {
      tag.delete();
    }
    else {
      newText = "@" + GlobalInspectionContextImpl.SUPPRESS_INSPECTIONS_TAG_NAME + newText;
      PsiDocTag newTag = tag.getManager().getElementFactory().createDocTagFromText(newText, tag);
      tag.replace(newTag);
    }
  }

  @Nullable
  private String removeFromElementText(final PsiElement element) {
    String text = StringUtil.trimStart(element.getText(), "//").trim();
    text = StringUtil.trimStart(text, "@").trim();
    text = StringUtil.trimStart(text, GlobalInspectionContextImpl.SUPPRESS_INSPECTIONS_TAG_NAME).trim();
    List<String> ids = StringUtil.split(text, ",");
    int i = ArrayUtil.find(ids.toArray(), myID);
    if (i==-1 || ids.size()==1) return null;
    ids.remove(i);
    return StringUtil.join(ids, ",");
  }

  private void removeFromAnnotation(final PsiAnnotation annotation) throws IncorrectOperationException {
    PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
    for (PsiNameValuePair attribute : attributes) {
      PsiAnnotationMemberValue value = attribute.getValue();
      if (value instanceof PsiArrayInitializerMemberValue) {
        PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue)value).getInitializers();
        for (PsiAnnotationMemberValue initializer : initializers) {
          if (removeFromValue(annotation, initializer, initializers.length==1)) return;
        }
      }
      if (removeFromValue(annotation, value, attributes.length==1)) return;
    }
  }

  private boolean removeFromValue(final PsiAnnotationMemberValue parent, final PsiAnnotationMemberValue value, final boolean removeParent) throws IncorrectOperationException {
    String text = value.getText();
    text = StringUtil.trimStart(text, "\"");
    text = StringUtil.trimEnd(text, "\"");
    if (myID.equals(text)) {
      if (removeParent) {
        parent.delete();
      }
      else {
        value.delete();
      }
      return true;
    }
    return false;
  }
}
