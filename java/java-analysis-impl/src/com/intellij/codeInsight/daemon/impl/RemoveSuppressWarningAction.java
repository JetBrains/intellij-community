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
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.JavaSuppressionUtil;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.SuppressionUtilCore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RemoveSuppressWarningAction implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.RemoveSuppressWarningAction");

  @NotNull
  private final String myID;

  public RemoveSuppressWarningAction(@NotNull String id) {
    final int idx = id.indexOf(";");
    if (idx > -1) {
      myID = id.substring(0, idx);
    }
    else {
      myID = id;
    }
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("remove.suppression.action.family");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    try {
      if (element != null) {
        if (element instanceof PsiComment) {
          removeFromComment((PsiComment)element);
        }
        else {
          final PsiModifierListOwner commentOwner = PsiTreeUtil.getParentOfType(element, PsiModifierListOwner.class, false);
          if (commentOwner != null) {
            final PsiElement psiElement = JavaSuppressionUtil.getElementMemberSuppressedIn(commentOwner, myID);
            if (psiElement instanceof PsiAnnotation) {
              removeFromAnnotation((PsiAnnotation)psiElement);
            }
            else if (psiElement instanceof PsiDocComment) {
              removeFromJavaDoc((PsiDocComment)psiElement);
            }
            else { //try to remove from all comments
              final Set<PsiComment> comments = new HashSet<>();
              commentOwner.accept(new PsiRecursiveElementWalkingVisitor() {
                @Override
                public void visitComment(final PsiComment comment) {
                  super.visitComment(comment);
                  if (comment.getText().contains(myID)) {
                    comments.add(comment);
                  }
                }
              });
              for (PsiComment comment : comments) {
                try {
                  removeFromComment(comment);
                }
                catch (IncorrectOperationException e) {
                  LOG.error(e);
                }
              }
            }
          }
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Override
  @NotNull
  public String getName() {
    return QuickFixBundle.message("remove.suppression.action.name", myID);
  }

  private void removeFromComment(final PsiComment comment) throws IncorrectOperationException {
    String commentText = comment.getText();
    int secondCommentIdx = commentText.indexOf("//", 2);
    String suffix = "";
    if (secondCommentIdx > 0) {
      suffix = commentText.substring(secondCommentIdx);
    }
    String newText = removeFromElementText(comment);
    if (newText != null) {
      if (newText.isEmpty()) {
        if (suffix.isEmpty()) {
          comment.delete();
        }
        else {
          comment.replace(JavaPsiFacade.getElementFactory(comment.getProject()).createCommentFromText(suffix, comment));
        }
      }
      else {
        PsiComment newComment = JavaPsiFacade.getElementFactory(comment.getProject())
          .createCommentFromText("// " + SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME +" "+newText + suffix, comment);
        comment.replace(newComment);
      }
    }
  }

  private void removeFromJavaDoc(PsiDocComment docComment) throws IncorrectOperationException {
    PsiDocTag tag = docComment.findTagByName(SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME);
    if (tag == null) return;
    String text = tag.getText();
    int i = text.indexOf(myID);
    if (i < 0) return;
    String noInspectionText = StringUtil.trimEnd(text.substring(0, i), " ");
    String nextText = StringUtil.trimStart(text.substring(i + myID.length()), " ");
    String nextTagText;

    if (noInspectionText.endsWith(",")) {
      nextTagText = noInspectionText.substring(0, noInspectionText.length() - 1) + nextText;
    }
    else if (nextText.startsWith(",")) {
      nextTagText = noInspectionText + nextText.substring(1);
    }
    else {
      nextTagText = null;
    }

    if (nextTagText != null) {
      tag.replace(JavaPsiFacade.getElementFactory(tag.getProject()).createDocTagFromText(nextTagText));
    }
    else {
      PsiElement[] descriptionElements =
        JavaPsiFacade.getElementFactory(tag.getProject()).createDocCommentFromText("/**" + nextText + "*/", tag).getDescriptionElements();
      if (descriptionElements.length > 0) {
        docComment.addRangeAfter(descriptionElements[0], descriptionElements[descriptionElements.length - 1], tag);
      }
      tag.delete();
    }
  }

  @Nullable
  private String removeFromElementText(final PsiElement... elements) {
    String text = "";
    for (PsiElement element : elements) {
      text += StringUtil.trimStart(element.getText(), "//").trim();
    }
    text = StringUtil.trimStart(text, "@").trim();
    int secondCommentIdx = text.indexOf("//");
    if (secondCommentIdx > 0) {
      text = text.substring(0, secondCommentIdx);
    }
    text = StringUtil.trimStart(text, SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME).trim();
    List<String> ids = StringUtil.split(text, ",");
    int i = ArrayUtil.find(ids.toArray(), myID);
    if (i==-1) return null;
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
        new CommentTracker().deleteAndRestoreComments(parent);
      }
      else {
        value.delete();
      }
      return true;
    }
    return false;
  }
}
