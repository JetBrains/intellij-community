// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.ModCommandAwareExternalAnnotationsManager;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class RemoveSuppressWarningAction extends ModCommandQuickFix {
  private static final Logger LOG = Logger.getInstance(RemoveSuppressWarningAction.class);

  private final @NotNull String myID;

  RemoveSuppressWarningAction(@NotNull String id) {
    int idx = id.indexOf(';');
    if (idx > -1) {
      myID = id.substring(0, idx);
    }
    else {
      myID = id;
    }
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("remove.suppression.action.family");
  }

  @Override
  public final @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getStartElement();
    return ModCommand.psiUpdate(element, (e, updater) -> removeFromCode(e)).andThen(removeExternal(element));
  }
  
  private void removeFromCode(@NotNull PsiElement element) {
    if (element instanceof PsiComment comment) {
      removeFromComment(comment);
    }
    else {
      PsiModifierListOwner commentOwner = PsiTreeUtil.getParentOfType(element, PsiModifierListOwner.class, false);
      if (commentOwner != null) {
        PsiElement psiElement = JavaSuppressionUtil.getElementMemberSuppressedIn(commentOwner, myID);
        if (psiElement instanceof PsiAnnotation annotation) {
          if (!ExternalAnnotationsManager.getInstance(annotation.getProject()).isExternalAnnotation(annotation)) {
            removeFromAnnotation(annotation);
          }
        }
        else if (psiElement instanceof PsiDocComment docComment) {
          removeFromJavaDoc(docComment);
        }
        else { //try to remove from all comments
          Set<PsiComment> comments = new HashSet<>();
          commentOwner.accept(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitComment(@NotNull PsiComment comment) {
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

  @Override
  public @NotNull String getName() {
    return QuickFixBundle.message("remove.suppression.action.name", myID);
  }

  private void removeFromComment(@NotNull PsiComment comment) throws IncorrectOperationException {
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

  private void removeFromJavaDoc(@NotNull PsiDocComment docComment) throws IncorrectOperationException {
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

  private @Nullable String removeFromElementText(PsiElement @NotNull ... elements) {
    StringBuilder textBuilder = new StringBuilder();
    for (PsiElement element : elements) {
      textBuilder.append(StringUtil.trimStart(element.getText(), "//").trim());
    }
    String text = textBuilder.toString();
    text = StringUtil.trimStart(text, "@").trim();
    int secondCommentIdx = text.indexOf("//");
    if (secondCommentIdx > 0) {
      text = text.substring(0, secondCommentIdx);
    }
    text = StringUtil.trimStart(text, SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME).trim();
    List<String> ids = new ArrayList<>(StringUtil.split(text, ","));
    int i = ArrayUtil.find(ids.toArray(), myID);
    if (i==-1) return null;
    ids.remove(i);
    return StringUtil.join(ids, ",");
  }

  private void removeFromAnnotation(@NotNull PsiAnnotation annotation) throws IncorrectOperationException {
    PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
    PsiNameValuePair attribute =
      ContainerUtil.find(attributes, attr -> PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(attr.getAttributeName()));
    if (attribute == null) return;
    PsiAnnotationMemberValue value = attribute.getValue();
    if (value instanceof PsiArrayInitializerMemberValue) {
      PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue)value).getInitializers();
      for (PsiAnnotationMemberValue initializer : initializers) {
        if (removeFromValue(annotation, initializer, initializers.length == 1)) return;
      }
    }
    assert value != null;
    removeFromValue(annotation, value, attributes.length == 1);
  }

  private boolean removeFromValue(@NotNull PsiAnnotation annotation, @NotNull PsiAnnotationMemberValue value, boolean removeParent) throws IncorrectOperationException {
    String text = StringUtil.unquoteString(value.getText());
    if (!myID.equals(text)) return false;
    new CommentTracker().deleteAndRestoreComments(removeParent ? annotation : value);
    return true;
  }

  private ModCommand removeExternal(@NotNull PsiElement element) {
    PsiModifierListOwner owner = PsiTreeUtil.getParentOfType(element, PsiModifierListOwner.class, false);
    if (owner == null) return ModCommand.nop();
    if (JavaSuppressionUtil.getElementMemberSuppressedIn(owner, myID) instanceof PsiAnnotation annotation &&
        ExternalAnnotationsManager.getInstance(annotation.getProject()).isExternalAnnotation(annotation)) {
      return removeFromAnnotationExternal(annotation, owner);
    }
    return ModCommand.nop();
  }

  private @NotNull ModCommand removeFromAnnotationExternal(@NotNull PsiAnnotation annotation, @NotNull PsiModifierListOwner owner) throws IncorrectOperationException {
    PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
    PsiNameValuePair attribute =
      ContainerUtil.find(attributes, attr -> PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(attr.getAttributeName()));
    if (attribute == null) return ModCommand.nop();
    PsiAnnotationMemberValue value = attribute.getValue();
    if (value instanceof PsiArrayInitializerMemberValue) {
      PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue)value).getInitializers();
      for (PsiAnnotationMemberValue initializer : initializers) {
        ModCommand command = removeFromValueExternal(annotation, initializer, initializers.length == 1, owner);
        if (!command.isEmpty()) {
          return command;
        }
      }
    }
    assert value != null;
    return removeFromValueExternal(annotation, value, attributes.length == 1, owner);
  }

  private @NotNull ModCommand removeFromValueExternal(@NotNull PsiAnnotation annotation, @NotNull PsiAnnotationMemberValue value, boolean removeParent, @NotNull PsiModifierListOwner owner) throws IncorrectOperationException {
    String text = StringUtil.unquoteString(value.getText());
    if (!myID.equals(text)) return ModCommand.nop();
    ModCommandAwareExternalAnnotationsManager manager =
      (ModCommandAwareExternalAnnotationsManager)ExternalAnnotationsManager.getInstance(annotation.getProject());
    if (removeParent) {
      String qualifiedName = annotation.getQualifiedName(); //SuppressWarnings
      assert qualifiedName != null;
      return manager.deannotateModCommand(List.of(owner), List.of(qualifiedName));
    }
    else {
      PsiAnnotation annotationCopy = (PsiAnnotation)annotation.copy();
      PsiTreeUtil.processElements(annotationCopy, e -> {
        if (e instanceof PsiAnnotationMemberValue && e.getText().equals(value.getText())) {
          e.delete();
          return false;
        }
        return true;
      });
      PsiNameValuePair[] nameValuePairs = annotationCopy.getParameterList().getAttributes();
      String qualifiedName = annotation.getQualifiedName(); //SuppressWarnings
      assert qualifiedName != null;
      return manager.editExternalAnnotationModCommand(owner, qualifiedName, nameValuePairs);
    }
  }
}
