// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.javaDoc;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.javadoc.PsiInlineDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavaDocFixes {
  private JavaDocFixes(){
  }

  public static class AddMissingTagFix extends PsiUpdateModCommandQuickFix {
    private final String myTag;
    private final String myValue;

    AddMissingTagFix(@NotNull String tag, @NotNull String value) {
      myTag = tag;
      myValue = value;
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiDocComment docComment = PsiTreeUtil.getParentOfType(element, PsiDocComment.class);
      if (docComment != null) {
        PsiDocTag tag = JavaPsiFacade.getElementFactory(project).createDocTagFromText("@" + myTag + " " + myValue);

        PsiElement addedTag;
        PsiElement anchor = getAnchor(element);
        if (anchor != null) {
          addedTag = docComment.addBefore(tag, anchor);
        }
        else {
          addedTag = docComment.add(tag);
        }
        PsiElement sibling = addedTag.getNextSibling();
        if (sibling != null) {
          updater.moveCaretTo(sibling);
        }
      }
    }

    protected @Nullable PsiElement getAnchor(PsiElement element) {
      return null;
    }

    @Override
    public @NotNull String getName() {
      return JavaBundle.message("inspection.javadoc.problem.add.tag", myTag, myValue);
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("inspection.javadoc.problem.add.tag.family");
    }
  }

  public static class AddMissingParamTagFix extends AddMissingTagFix {
    private final String myName;

    AddMissingParamTagFix(String name) {
      super("param", name);
      myName = name;
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("inspection.javadoc.problem.add.param.tag.family");
    }

    @Override
    protected @Nullable PsiElement getAnchor(PsiElement element) {
      PsiElement parent = element == null ? null : element.getParent();
      if (!(parent instanceof PsiDocComment docComment)) return null;
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
    public @NotNull String getName() {
      return JavaBundle.message("inspection.javadoc.problem.add.param.tag", myName);
    }
  }

  public static class AddUnknownTagToCustoms implements LocalQuickFix {
    private final JavadocDeclarationInspection myInspection;
    private final String myTag;

    AddUnknownTagToCustoms(@NotNull JavadocDeclarationInspection inspection, @NotNull String tag) {
      myInspection = inspection;
      myTag = tag;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      myInspection.registerAdditionalTag(myTag);
      ProjectInspectionProfileManager.getInstance(project).fireProfileChanged();
    }

    @Override
    public @NotNull String getName() {
      return QuickFixBundle.message("add.docTag.to.custom.tags", myTag);
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      return new IntentionPreviewInfo.Html(QuickFixBundle.message("add.docTag.to.custom.tags.preview"));
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public @NotNull String getFamilyName() {
      //noinspection DialogTitleCapitalization
      return QuickFixBundle.message("fix.javadoc.family");
    }
  }

  public static class RemoveTagFix extends PsiUpdateModCommandQuickFix {
    private final String myTagName;

    RemoveTagFix(String tagName) {
      myTagName = tagName;
    }

    @Override
    public @NotNull String getName() {
      return JavaBundle.message("quickfix.text.remove.javadoc.0", myTagName);
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("quickfix.family.remove.javadoc.tag");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiDocTag tag = PsiTreeUtil.getParentOfType(element, PsiDocTag.class);
      if (tag != null) {
        tag.delete();
      }
    }
  }

  private abstract static class AbstractUnknownTagFix extends PsiUpdateModCommandQuickFix {
    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiElement enclosingTag = element.getParent();
      if (enclosingTag == null) return;

      final PsiElement javadoc = enclosingTag.getParent();
      if (javadoc == null) return;

      final PsiDocComment donorJavadoc = createDonorJavadoc(element);
      final PsiElement codeTag = extractElement(donorJavadoc);
      if (codeTag == null) return;

      for (var e = enclosingTag.getFirstChild(); e != element && e != null; e = e.getNextSibling()) {
        javadoc.addBefore(e, enclosingTag);
      }
      javadoc.addBefore(codeTag, enclosingTag);
      for (var e = element.getNextSibling(); e != null; e = e.getNextSibling()) {
        javadoc.addBefore(e, enclosingTag);
      }
      final PsiElement sibling = enclosingTag.getNextSibling();
      if (sibling != null && sibling.getNode().getElementType() == TokenType.WHITE_SPACE) {
        javadoc.addBefore(sibling, enclosingTag);
      }
      enclosingTag.delete();
    }

    protected abstract @NotNull PsiDocComment createDonorJavadoc(@NotNull PsiElement element);
    protected abstract @Nullable PsiElement extractElement(@Nullable PsiDocComment donorJavadoc);
  }

  static final class EncloseWithCodeFix extends AbstractUnknownTagFix {
    private final String myName;

    EncloseWithCodeFix(String name) {
      myName = name;
    }

    @Override
    public @NotNull String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.x.with.y", myName, "{@code " + myName + "}");
    }

    @Override
    protected @NotNull PsiDocComment createDonorJavadoc(@NotNull PsiElement element) {
      final PsiElementFactory instance = PsiElementFactory.getInstance(element.getProject());
      return instance.createDocCommentFromText(String.format("/** {@code %s} */", element.getText()));
    }

    @Override
    protected @Nullable PsiElement extractElement(@Nullable PsiDocComment donorJavadoc) {
      return PsiTreeUtil.findChildOfType(donorJavadoc, PsiInlineDocTag.class);
    }
  }

  static final class EscapeAtQuickFix extends AbstractUnknownTagFix {
    private final String myName;

    EscapeAtQuickFix(String name) {
      myName = name;
    }

    @Override
    public @NotNull String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.x.with.y", myName, "&#064;" + myName.substring(1));
    }

    @Override
    protected @NotNull PsiDocComment createDonorJavadoc(@NotNull PsiElement element) {
      final PsiElementFactory instance = PsiElementFactory.getInstance(element.getProject());
      return instance.createDocCommentFromText("/** &#064;" + element.getText().substring(1) + " */");
    }

    @Override
    protected @Nullable PsiElement extractElement(@Nullable PsiDocComment donorJavadoc) {
      if (donorJavadoc == null) return null;
      return donorJavadoc.getChildren()[2];
    }
  }
}
