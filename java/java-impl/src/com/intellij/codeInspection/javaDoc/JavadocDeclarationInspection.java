// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.javaDoc;

import com.intellij.codeInsight.daemon.impl.analysis.IncreaseLanguageLevelFix;
import com.intellij.codeInspection.*;
import com.intellij.java.JavaBundle;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.javadoc.*;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JavadocDeclarationInspection extends LocalInspectionTool {
  public static final String SHORT_NAME = "JavadocDeclaration";

  public String ADDITIONAL_TAGS = "";
  public boolean IGNORE_THROWS_DUPLICATE = true;
  public boolean IGNORE_PERIOD_PROBLEM = true;
  public boolean IGNORE_SELF_REFS = false;

  private static final String[] TAGS_TO_CHECK = {"author", "version", "since"};
  private static final Set<String> UNIQUE_TAGS = ContainerUtil.newHashSet("return", "deprecated", "serial", "serialData");

  public void registerAdditionalTag(@NotNull String tag) {
    if (!ADDITIONAL_TAGS.isEmpty()) {
      ADDITIONAL_TAGS += "," + tag;
    }
    else {
      ADDITIONAL_TAGS = tag;
    }
  }

  @Override
  public @Nullable JComponent createOptionsPanel() {
    return JavadocUIUtil.INSTANCE.javadocDeclarationOptions(this);
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitJavaFile(PsiJavaFile file) {
        if (PsiPackage.PACKAGE_INFO_FILE.equals(file.getName())) {
          checkFile(file, holder);
        }
      }

      @Override
      public void visitModule(PsiJavaModule module) {
        checkModule(module, holder);
      }

      @Override
      public void visitClass(PsiClass aClass) {
        checkClass(aClass, holder);
      }

      @Override
      public void visitField(PsiField field) {
        checkField(field, holder);
      }

      @Override
      public void visitMethod(PsiMethod method) {
        checkMethod(method, holder);
      }
    };
  }


  private void checkFile(PsiJavaFile file, ProblemsHolder holder) {
    PsiPackage pkg = JavaDirectoryService.getInstance().getPackage(file.getContainingDirectory());
    if (pkg == null) return;

    PsiDocComment docComment = PsiTreeUtil.getChildOfType(file, PsiDocComment.class);
    if (docComment != null) {
      PsiDocTag[] tags = docComment.getTags();
      checkBasics(docComment, tags, pkg, holder);
    }
  }

  private void checkModule(PsiJavaModule module, ProblemsHolder holder) {
    PsiDocComment docComment = module.getDocComment();

    if (docComment != null) {
      checkBasics(docComment, docComment.getTags(), module, holder);
    }
  }

  private void checkClass(PsiClass aClass, ProblemsHolder holder) {
    if (aClass instanceof PsiAnonymousClass || aClass instanceof PsiSyntheticClass || aClass instanceof PsiTypeParameter) {
      return;
    }

    PsiDocComment docComment = aClass.getDocComment();

    if (docComment != null) {
      PsiDocTag[] tags = docComment.getTags();

      checkBasics(docComment, tags, aClass, holder);
    }
  }

  private void checkField(PsiField field, ProblemsHolder holder) {
    PsiDocComment docComment = field.getDocComment();

    if (docComment != null) {
      checkBasics(docComment, docComment.getTags(), field, holder);
    }
  }

  private void checkMethod(PsiMethod method, ProblemsHolder holder) {
    if (method instanceof SyntheticElement) {
      return;
    }

    PsiDocComment docComment = method.getDocComment();

    if (docComment != null) {
      if (!MissingJavadocInspection.isInherited(docComment, method)) {
        PsiDocTag[] tags = docComment.getTags();

        checkEmptyMethodTagsDescription(tags, method, holder);

        checkBasics(docComment, tags, method, holder);
      }
    }
  }

  private void checkBasics(PsiDocComment docComment, PsiDocTag[] tags, PsiElement context, ProblemsHolder holder) {

    checkRequiredTagDescriptions(tags, holder);

    checkTagValues(tags, context, holder);

    if (!IGNORE_PERIOD_PROBLEM) {
      checkForPeriod(docComment, context, holder);
    }

    checkInlineTags(docComment.getDescriptionElements(), holder);

    checkForBadCharacters(docComment, holder);

    checkDuplicateTags(tags, holder);
  }

  private static void checkEmptyMethodTagsDescription(PsiDocTag @NotNull [] tags,
                                              @NotNull PsiMethod psiMethod,
                                              @NotNull ProblemsHolder holder) {
    for (PsiDocTag tag : tags) {
      if (ContainerUtil
        .exists(tag.getChildren(), e -> e instanceof PsiInlineDocTag && ((PsiInlineDocTag)e).getName().equals("inheritDoc"))) {
        continue;
      }
      if ("return".equals(tag.getName())) {
        if (!PsiType.VOID.equals(psiMethod.getReturnType()) && isEmptyTag(tag)) {
          String tagText = "<code>@return</code>";
          LocalQuickFix fix = new JavaDocFixes.RemoveTagFix("return");
          holder.registerProblem(tag.getNameElement(), JavaBundle.message("inspection.javadoc.method.problem.missing.tag.description", tagText), fix);
        }
      }
      else if ("throws".equals(tag.getName()) || "exception".equals(tag.getName())) {
        if (isEmptyThrowsTag(tag)) {
          String tagText = "<code>@" + tag.getName() + "</code>";
          LocalQuickFix fix = new JavaDocFixes.RemoveTagFix(tag.getName());
          holder.registerProblem(tag.getNameElement(), JavaBundle.message("inspection.javadoc.method.problem.missing.tag.description", tagText), fix);
        }
      }
      else if ("param".equals(tag.getName())) {
        PsiDocTagValue valueElement = tag.getValueElement();
        if (valueElement != null && isEmptyParamTag(tag, valueElement)) {
          String tagText = "<code>@param " + valueElement.getText() + "</code>";
          LocalQuickFix fix = new JavaDocFixes.RemoveTagFix("param " + valueElement.getText());
          holder.registerProblem(valueElement, JavaBundle.message("inspection.javadoc.method.problem.missing.tag.description", tagText), fix);
        }
      }
    }
  }

  private static void checkRequiredTagDescriptions(PsiDocTag @NotNull [] tags, @NotNull ProblemsHolder holder) {
    for (PsiDocTag tag : tags) {
      String tagName = tag.getName();
      if (ArrayUtil.find(TAGS_TO_CHECK, tagName) >= 0 && isEmptyTag(tag)) {
        String message = JavaBundle.message("inspection.javadoc.problem.missing.tag.description", StringUtil.capitalize(tagName), tagName);
        holder.registerProblem(tag.getNameElement(), message);
      }
    }
  }

  private static void checkForBadCharacters(@NotNull PsiDocComment docComment, @NotNull ProblemsHolder holder) {
    docComment.accept(new PsiRecursiveElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        super.visitElement(element);
        ASTNode node = element.getNode();
        if (node != null && node.getElementType() == JavaDocTokenType.DOC_COMMENT_BAD_CHARACTER) {
          holder.registerProblem(element, JavaBundle.message("inspection.illegal.character"));
        }
      }
    });
  }

  private void checkDuplicateTags(PsiDocTag @NotNull [] tags, @NotNull ProblemsHolder holder) {
    Set<String> documentedParamNames = null;
    Set<String> documentedExceptions = null;
    Set<String> uniqueTags = null;

    for (PsiDocTag tag : tags) {
      if ("param".equals(tag.getName())) {
        PsiDocTagValue value = tag.getValueElement();
        if (value instanceof PsiDocParamRef) {
          PsiReference reference = value.getReference();
          if (reference != null) {
            String paramName = reference.getCanonicalText();
            if(((PsiDocParamRef)value).isTypeParamRef()){
              paramName = "<" + paramName + ">";
            }
            documentedParamNames = set(documentedParamNames);
            if (documentedParamNames.contains(paramName)) {
              holder.registerProblem(tag.getNameElement(), JavaBundle.message("inspection.javadoc.problem.duplicate.param", paramName));
            }
            documentedParamNames.add(paramName);
          }
        }
      }
      else if (!IGNORE_THROWS_DUPLICATE && ("throws".equals(tag.getName()) || "exception".equals(tag.getName()))) {
        PsiDocTagValue value = tag.getValueElement();
        if (value != null) {
          PsiElement firstChild = value.getFirstChild();
          if (firstChild != null && firstChild.getFirstChild() instanceof PsiJavaCodeReferenceElement) {
            PsiJavaCodeReferenceElement refElement = (PsiJavaCodeReferenceElement)firstChild.getFirstChild();
            PsiElement element = refElement.resolve();
            if (element instanceof PsiClass) {
              String fqName = ((PsiClass)element).getQualifiedName();
              documentedExceptions = set(documentedExceptions);
              if (documentedExceptions.contains(fqName)) {
                holder.registerProblem(tag.getNameElement(), JavaBundle.message("inspection.javadoc.problem.duplicate.throws", fqName));
              }
              documentedExceptions.add(fqName);
            }
          }
        }
      }
      else if (UNIQUE_TAGS.contains(tag.getName())) {
        uniqueTags = set(uniqueTags);
        if (uniqueTags.contains(tag.getName())) {
          holder.registerProblem(tag.getNameElement(), JavaBundle.message("inspection.javadoc.problem.duplicate.tag", tag.getName()));
        }
        uniqueTags.add(tag.getName());
      }
    }
  }

  private static void checkForPeriod(@NotNull PsiDocComment docComment, @Nullable PsiElement context, @NotNull ProblemsHolder holder) {
    int dotIndex = docComment.getText().indexOf('.'), tagOffset = 0;
    if (dotIndex >= 0) {  // need to find first valid tag
      for (PsiDocTag tag : docComment.getTags()) {
        String tagName = tag.getName();
        JavadocTagInfo tagInfo = JavadocManager.SERVICE.getInstance(tag.getProject()).getTagInfo(tagName);
        if (tagInfo != null && tagInfo.isValidInContext(context) && !tagInfo.isInline()) {
          tagOffset = tag.getTextOffset();
          break;
        }
      }
    }

    if (dotIndex == -1 || tagOffset > 0 && dotIndex + docComment.getTextOffset() > tagOffset) {
      holder.registerProblem(docComment.getFirstChild(), JavaBundle.message("inspection.javadoc.problem.descriptor1"));
    }
  }

  private void checkInlineTags(PsiElement @NotNull [] elements, @NotNull ProblemsHolder holder) {
    JavadocManager docManager = JavadocManager.SERVICE.getInstance(holder.getProject());
    for (PsiElement element : elements) {
      if (element instanceof PsiInlineDocTag) {
        PsiInlineDocTag tag = (PsiInlineDocTag)element;
        String tagName = tag.getName();
        if (docManager.getTagInfo(tagName) == null) {
          checkTagInfo(tag, tagName, null, holder);
        }
        checkPointToSelf(holder, tag);
        checkSnippetTag(holder, element, tag);
      }
    }
  }

  private void checkTagValues(PsiDocTag @NotNull [] tags, @Nullable PsiElement context, @NotNull ProblemsHolder holder) {
    JavadocManager docManager = JavadocManager.SERVICE.getInstance(holder.getProject());
    for (PsiDocTag tag : tags) {
      String tagName = tag.getName();
      JavadocTagInfo tagInfo = docManager.getTagInfo(tagName);

      if (tagInfo == null || !tagInfo.isValidInContext(context)) {
        if (checkTagInfo(tag, tagName, tagInfo, holder)) continue;
      }

      PsiDocTagValue value = tag.getValueElement();
      if (tagInfo != null && !tagInfo.isValidInContext(context)) continue;
      String message = tagInfo == null ? null : tagInfo.checkTagValue(value);

      PsiReference reference = value != null ? value.getReference() : null;
      if (message == null && reference != null) {
        PsiElement element = reference.resolve();
        if (element == null) {
          int textOffset = value.getTextOffset();
          if (textOffset == value.getTextRange().getEndOffset()) {
            ProblemDescriptor problem = holder.getManager().createProblemDescriptor(
              tag, JavaBundle.message("inspection.javadoc.problem.name.expected"), null,
              ProblemHighlightType.GENERIC_ERROR_OR_WARNING, holder.isOnTheFly(), true);
            holder.registerProblem(problem);
          }
        }
      }

      if (message != null) {
        PsiElement toHighlight = ObjectUtils.notNull(tag.getValueElement(), tag.getNameElement());
        holder.registerProblem(toHighlight, message);
      }

      PsiElement[] dataElements = tag.getDataElements();

      if ("see".equals(tagName)) {
        if (dataElements.length == 0 || dataElements.length == 1 && isEmpty(dataElements[0])) {
          holder.registerProblem(tag.getNameElement(), JavaBundle.message("inspection.javadoc.problem.see.tag.expecting.ref"));
        }
        else if (!isValidSeeRef(dataElements)) {
          holder.registerProblem(dataElements[0], JavaBundle.message("inspection.javadoc.problem.see.tag.expecting.ref"));
        }
      }

      checkInlineTags(dataElements, holder);
    }
  }

  private boolean checkTagInfo(PsiDocTag tag, String tagName, JavadocTagInfo tagInfo, ProblemsHolder holder) {
    StringTokenizer tokenizer = new StringTokenizer(ADDITIONAL_TAGS, ", ");
    while (tokenizer.hasMoreTokens()) {
      if (Comparing.strEqual(tagName, tokenizer.nextToken())) return true;
    }

    PsiElement nameElement = tag.getNameElement();
    if (nameElement != null) {
      String key = tagInfo == null ? "inspection.javadoc.problem.wrong.tag" : "inspection.javadoc.problem.disallowed.tag";
      LocalQuickFix fix = tagInfo == null ? new JavaDocFixes.AddUnknownTagToCustoms(this, tagName) : new JavaDocFixes.RemoveTagFix(tagName);
      final LocalQuickFix[] fixes;
      if (tagInfo != null) {
        fixes = new LocalQuickFix[]{ fix };
      }
      else {
        final String nameElementText = nameElement.getText();
        fixes = new LocalQuickFix[]{ fix, new JavaDocFixes.EncloseWithCodeFix(nameElementText), new JavaDocFixes.EscapeAtQuickFix(nameElementText) };
      }
      holder.registerProblem(nameElement, JavaBundle.message(key, "<code>" + tagName + "</code>"), fixes);
    }

    return false;
  }

  private void checkPointToSelf(@NotNull ProblemsHolder holder, PsiInlineDocTag tag) {
    if (IGNORE_SELF_REFS) {
      return;
    }
    PsiDocTagValue value = tag.getValueElement();
    if (value == null) {
      return;
    }
    PsiReference reference = value.getReference();
    if (reference == null) {
      return;
    }
    PsiElement target = reference.resolve();
    if (target == null) {
      return;
    }
    if (PsiTreeUtil.getParentOfType(tag, PsiDocCommentOwner.class) !=
        PsiTreeUtil.getParentOfType(target, PsiDocCommentOwner.class, false)) {
      return;
    }
    PsiElement nameElement = tag.getNameElement();
    if (nameElement == null) {
      return;
    }
    holder.registerProblem(nameElement, JavaBundle.message("inspection.javadoc.problem.pointing.to.itself"));
  }

  private static final TokenSet SEE_TAG_REFS = TokenSet.create(JavaDocElementType.DOC_REFERENCE_HOLDER, JavaDocElementType.DOC_METHOD_OR_FIELD_REF);

  private static boolean isValidSeeRef(PsiElement... elements) {
    if (SEE_TAG_REFS.contains(elements[0].getNode().getElementType())) return true;

    String text = Stream.of(elements).map(e -> e.getText().trim()).collect(Collectors.joining(" ")).trim();
    if (StringUtil.isQuotedString(text) && text.charAt(0) == '"') return true;

    if (StringUtil.toLowerCase(text).matches("^<a\\s+href=.+")) return true;

    return false;
  }

  private static void checkSnippetTag(@NotNull ProblemsHolder holder, PsiElement element, PsiInlineDocTag tag) {
    if (element instanceof PsiSnippetDocTag) {
      if (!PsiUtil.getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_18)) {
        PsiElement nameElement = tag.getNameElement();
        if (nameElement != null) {
          String message = JavaBundle.message("inspection.javadoc.problem.snippet.tag.is.not.available");
          holder.registerProblem(nameElement, message, new IncreaseLanguageLevelFix(LanguageLevel.JDK_18));
        }
      }
    }
  }

  private static boolean isEmptyTag(PsiDocTag tag) {
    return Stream.of(tag.getChildren())
      .filter(e -> e instanceof PsiDocToken && ((PsiDocToken)e).getTokenType() == JavaDocTokenType.DOC_COMMENT_DATA ||
                   e instanceof PsiDocTagValue ||
                   e instanceof PsiInlineDocTag)
      .allMatch(JavadocDeclarationInspection::isEmpty);
  }

  private static boolean isEmptyThrowsTag(PsiDocTag tag) {
    return Stream.of(tag.getChildren())
      .filter(e -> e instanceof PsiDocToken && ((PsiDocToken)e).getTokenType() == JavaDocTokenType.DOC_COMMENT_DATA)
      .allMatch(JavadocDeclarationInspection::isEmpty);
  }

  private static boolean isEmptyParamTag(PsiDocTag tag, PsiDocTagValue valueElement) {
    PsiElement[] dataElements = tag.getDataElements();
    return dataElements.length < 2 || Stream.of(dataElements)
      .filter(e -> e != valueElement)
      .allMatch(JavadocDeclarationInspection::isEmpty);
  }

  private static boolean isEmpty(PsiElement e) {
    return e.getText().chars().allMatch(c -> c <= ' ');
  }

  private static <T> Set<T> set(Set<T> set) {
    return set != null ? set : new HashSet<>();
  }
}