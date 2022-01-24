// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.javaDoc;

import com.intellij.codeInsight.intention.impl.AddJavadocIntention;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaDocFixes {

  public static class AddJavadocFix extends LocalQuickFixAndIntentionActionOnPsiElement {
    private final AddJavadocIntention myIntention;

    AddJavadocFix(PsiElement nameIdentifier) {
      super(nameIdentifier);
      myIntention = new AddJavadocIntention();
    }

    @Override
    public void invoke(@NotNull Project project,
                       @NotNull PsiFile file,
                       @Nullable Editor editor,
                       @NotNull PsiElement startElement,
                       @NotNull PsiElement endElement) {
      myIntention.invoke(project, editor, startElement);
    }

    @NotNull
    @Override
    public String getText() {
      //noinspection DialogTitleCapitalization
      return myIntention.getText();
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return myIntention.getFamilyName();
    }
  }

  public static class AddMissingTagFix implements LocalQuickFix {
    private final String myTag;
    private final String myValue;

    AddMissingTagFix(@NotNull String tag, @NotNull String value) {
      myTag = tag;
      myValue = value;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiDocComment docComment = PsiTreeUtil.getParentOfType(descriptor.getEndElement(), PsiDocComment.class);
      if (docComment != null) {
        PsiDocTag tag = JavaPsiFacade.getElementFactory(project).createDocTagFromText("@" + myTag + " " + myValue);

        PsiElement addedTag;
        PsiElement anchor = getAnchor(descriptor);
        if (anchor != null) {
          addedTag = docComment.addBefore(tag, anchor);
        }
        else {
          addedTag = docComment.add(tag);
        }
        moveCaretAfter(addedTag);
      }
    }

    @Nullable
    protected PsiElement getAnchor(ProblemDescriptor descriptor) {
      return null;
    }

    private static void moveCaretAfter(PsiElement newCaretPosition) {
      PsiElement sibling = newCaretPosition.getNextSibling();
      if (sibling != null) {
        ((Navigatable)sibling).navigate(true);
      }
    }

    @Override
    @NotNull
    public String getName() {
      return JavaBundle.message("inspection.javadoc.problem.add.tag", myTag, myValue);
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return JavaBundle.message("inspection.javadoc.problem.add.tag.family");
    }
  }

  public static class AddMissingParamTagFix extends AddMissingTagFix {
    private final String myName;

    AddMissingParamTagFix(String name) {
      super("param", name);
      myName = name;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.javadoc.problem.add.param.tag.family");
    }

    @Override
    @Nullable
    protected PsiElement getAnchor(ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      PsiElement parent = element == null ? null : element.getParent();
      if (!(parent instanceof PsiDocComment)) return null;
      final PsiDocComment docComment = (PsiDocComment)parent;
      final PsiJavaDocumentedElement owner = docComment.getOwner();
      if (!(owner instanceof PsiMethod)) return null;
      PsiParameter[] parameters = ((PsiMethod)owner).getParameterList().getParameters();
      PsiParameter myParam = ContainerUtil.find(parameters, psiParameter -> myName.equals(psiParameter.getName()));
      if (myParam == null) return null;

      PsiDocTag[] tags = docComment.findTagsByName("param");
      if (tags.length == 0) { //insert as first tag or append to description
        tags = docComment.getTags();
        if (tags.length == 0) return null;
        return tags[0];
      }

      PsiParameter nextParam = PsiTreeUtil.getNextSiblingOfType(myParam, PsiParameter.class);
      while (nextParam != null) {
        for (PsiDocTag tag : tags) {
          if (matches(nextParam, tag)) {
            return tag;
          }
        }
        nextParam = PsiTreeUtil.getNextSiblingOfType(nextParam, PsiParameter.class);
      }

      PsiParameter prevParam = PsiTreeUtil.getPrevSiblingOfType(myParam, PsiParameter.class);
      while (prevParam != null) {
        for (PsiDocTag tag : tags) {
          if (matches(prevParam, tag)) {
            return PsiTreeUtil.getNextSiblingOfType(tag, PsiDocTag.class);
          }
        }
        prevParam = PsiTreeUtil.getPrevSiblingOfType(prevParam, PsiParameter.class);
      }

      return null;
    }

    private static boolean matches(PsiParameter param, PsiDocTag tag) {
      PsiDocTagValue valueElement = tag.getValueElement();
      String name = param.getName();
      return valueElement != null && valueElement.getText().trim().startsWith(name);
    }

    @Override
    @NotNull
    public String getName() {
      return JavaBundle.message("inspection.javadoc.problem.add.param.tag", myName);
    }
  }
}
