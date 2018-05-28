// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.javaDoc;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.reference.RefJavaUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.javadoc.*;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JavadocHighlightUtil {
  private static final String[] TAGS_TO_CHECK = {"author", "version", "since"};
  private static final Set<String> UNIQUE_TAGS = ContainerUtil.newHashSet("return", "deprecated", "serial", "serialData");
  private static final TokenSet SEE_TAG_REFS = TokenSet.create(
    JavaDocElementType.DOC_REFERENCE_HOLDER, JavaDocElementType.DOC_METHOD_OR_FIELD_REF);

  @SuppressWarnings("SameParameterValue")
  public interface ProblemHolder {
    Project project();
    JavaDocLocalInspectionBase inspection();

    void problem(@NotNull PsiElement toHighlight, @NotNull @Nls String message, @Nullable LocalQuickFix fix);
    void eolProblem(@NotNull PsiElement toHighlight, @NotNull @Nls String message, @Nullable LocalQuickFix fix);

    LocalQuickFix addJavadocFix(@NotNull PsiElement nameIdentifier);
    LocalQuickFix addMissingTagFix(@NotNull String tag, @NotNull String value);
    LocalQuickFix addMissingParamTagFix(@NotNull String name);
    LocalQuickFix registerTagFix(@NotNull String tag);
  }

  static boolean isJavaDocRequired(@NotNull JavaDocLocalInspectionBase inspection, @NotNull PsiModifierListOwner element) {
    if (element instanceof PsiPackage) {
      return 1 <= getAccessNumber(inspection.PACKAGE_OPTIONS);
    }

    if (element instanceof PsiJavaModule) {
      return 1 <= getAccessNumber(inspection.MODULE_OPTIONS);
    }

    int actualAccess = getAccessNumber(RefJavaUtil.getInstance().getAccessModifier(element));

    if (element instanceof PsiClass) {
      boolean isInner = PsiTreeUtil.getParentOfType(element, PsiClass.class) != null;
      return actualAccess <= getAccessNumber(isInner ? inspection.INNER_CLASS_OPTIONS : inspection.TOP_LEVEL_CLASS_OPTIONS);
    }

    if (element instanceof PsiMethod) {
      element = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      while (element != null) {
        actualAccess = Math.max(actualAccess, getAccessNumber(RefJavaUtil.getInstance().getAccessModifier(element)));
        element = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      }

      return actualAccess <= getAccessNumber(inspection.METHOD_OPTIONS);
    }

    if (element instanceof PsiField) {
      element = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      while (element != null) {
        actualAccess = Math.max(actualAccess, getAccessNumber(RefJavaUtil.getInstance().getAccessModifier(element)));
        element = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      }

      return actualAccess <= getAccessNumber(inspection.FIELD_OPTIONS);
    }

    return false;
  }

  private static int getAccessNumber(JavaDocLocalInspectionBase.Options options) {
    return getAccessNumber(options.ACCESS_JAVADOC_REQUIRED_FOR);
  }

  private static int getAccessNumber(String accessModifier) {
    if (accessModifier.startsWith(JavaDocLocalInspectionBase.NONE)) return 0;
    if (accessModifier.startsWith(JavaDocLocalInspectionBase.PUBLIC)) return 1;
    if (accessModifier.startsWith(JavaDocLocalInspectionBase.PROTECTED)) return 2;
    if (accessModifier.startsWith(JavaDocLocalInspectionBase.PACKAGE_LOCAL)) return 3;
    if (accessModifier.startsWith(JavaDocLocalInspectionBase.PRIVATE)) return 4;

    return 5;
  }

  static void reportMissingTag(@NotNull PsiElement toHighlight, @NotNull ProblemHolder holder) {
    String message = InspectionsBundle.message("inspection.javadoc.problem.descriptor");
    holder.problem(toHighlight, message, holder.addJavadocFix(toHighlight));
  }

  static void checkRequiredTags(@NotNull PsiDocTag[] tags,
                                @NotNull JavaDocLocalInspectionBase.Options options,
                                @NotNull PsiElement toHighlight,
                                @NotNull ProblemHolder holder) {
    boolean[] isTagRequired = new boolean[TAGS_TO_CHECK.length];
    boolean[] isTagPresent = new boolean[TAGS_TO_CHECK.length];
    boolean someTagsAreRequired = false;

    for (int i = 0; i < TAGS_TO_CHECK.length; i++) {
      someTagsAreRequired |= (isTagRequired[i] = JavaDocLocalInspectionBase.isTagRequired(options, TAGS_TO_CHECK[i]));
    }

    if (!someTagsAreRequired) return;

    for (PsiDocTag tag : tags) {
      int p = ArrayUtil.find(TAGS_TO_CHECK, tag.getName());
      if (p >= 0) isTagPresent[p] = true;
    }

    for (int i = 0; i < TAGS_TO_CHECK.length; i++) {
      if (isTagRequired[i] && !isTagPresent[i]) {
        String tagName = TAGS_TO_CHECK[i];
        String message = InspectionsBundle.message("inspection.javadoc.problem.missing.tag", "<code>@" + tagName + "</code>");
        holder.problem(toHighlight, message, holder.addMissingTagFix(tagName, ""));
      }
    }
  }

  static void checkRequiredTagDescriptions(@NotNull PsiDocTag[] tags, @NotNull ProblemHolder holder) {
    for (PsiDocTag tag : tags) {
      String tagName = tag.getName();
      if (ArrayUtil.find(TAGS_TO_CHECK, tagName) >= 0 && emptyTag(tag)) {
        String message = InspectionsBundle.message("inspection.javadoc.problem.missing.tag.description", StringUtil.capitalize(tagName), tagName);
        holder.problem(tag.getNameElement(), message, null);
      }
    }
  }

  static void checkTagValues(@NotNull PsiDocTag[] tags, @Nullable PsiElement context, @NotNull ProblemHolder holder) {
    JavadocManager docManager = JavadocManager.SERVICE.getInstance(holder.project());
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
            holder.eolProblem(tag, InspectionsBundle.message("inspection.javadoc.problem.name.expected"), null);
          }
        }
      }

      if (message != null) {
        PsiElement toHighlight = ObjectUtils.notNull(tag.getValueElement(), tag.getNameElement());
        holder.problem(toHighlight, message, null);
      }

      PsiElement[] dataElements = tag.getDataElements();

      if ("see".equals(tagName)) {
        if (dataElements.length == 0 || dataElements.length == 1 && empty(dataElements[0])) {
          holder.problem(tag.getNameElement(), InspectionsBundle.message("inspection.javadoc.problem.see.tag.expecting.ref"), null);
        }
        else if (!isValidSeeRef(dataElements)) {
          holder.problem(dataElements[0], InspectionsBundle.message("inspection.javadoc.problem.see.tag.expecting.ref"), null);
        }
      }

      checkInlineTags(dataElements, holder);
    }
  }

  private static boolean isValidSeeRef(PsiElement... elements) {
    if (SEE_TAG_REFS.contains(elements[0].getNode().getElementType())) return true;

    String text = Stream.of(elements).map(e -> e.getText().trim()).collect(Collectors.joining(" ")).trim();
    if (StringUtil.isQuotedString(text) && text.charAt(0) == '"') return true;

    if (text.toLowerCase(Locale.US).matches("^<a\\s+href=.+")) return true;

    return false;
  }

  static void checkInlineTags(@NotNull PsiElement[] elements, @NotNull ProblemHolder holder) {
    JavadocManager docManager = JavadocManager.SERVICE.getInstance(holder.project());
    for (PsiElement element : elements) {
      if (element instanceof PsiInlineDocTag) {
        PsiInlineDocTag tag = (PsiInlineDocTag)element;
        String tagName = tag.getName();
        if (docManager.getTagInfo(tagName) == null) {
          checkTagInfo(tag, tagName, null, holder);
        }
        if (!holder.inspection().IGNORE_POINT_TO_ITSELF) {
          PsiDocTagValue value = tag.getValueElement();
          if (value != null) {
            PsiReference reference = value.getReference();
            if (reference != null) {
              PsiElement target = reference.resolve();
              if (target != null) {
                if (PsiTreeUtil.getParentOfType(tag, PsiDocCommentOwner.class) ==
                    PsiTreeUtil.getParentOfType(target, PsiDocCommentOwner.class, false)) {
                  PsiElement nameElement = tag.getNameElement();
                  if (nameElement != null) {
                    holder.problem(nameElement, InspectionsBundle.message("inspection.javadoc.problem.pointing.to.itself"), null);
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  private static boolean checkTagInfo(PsiDocTag tag, String tagName, JavadocTagInfo tagInfo, ProblemHolder holder) {
    StringTokenizer tokenizer = new StringTokenizer(holder.inspection().myAdditionalJavadocTags, ", ");
    while (tokenizer.hasMoreTokens()) {
      if (Comparing.strEqual(tagName, tokenizer.nextToken())) return true;
    }

    PsiElement nameElement = tag.getNameElement();
    if (nameElement != null) {
      String key = tagInfo == null ? "inspection.javadoc.problem.wrong.tag" : "inspection.javadoc.problem.disallowed.tag";
      holder.problem(nameElement, InspectionsBundle.message(key, "<code>" + tagName + "</code>"), holder.registerTagFix(tagName));
    }

    return false;
  }

  static void checkForPeriod(@NotNull PsiDocComment docComment, @Nullable PsiElement context, @NotNull ProblemHolder holder) {
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
      holder.problem(docComment.getFirstChild(), InspectionsBundle.message("inspection.javadoc.problem.descriptor1"), null);
    }
  }

  static void checkDuplicateTags(@NotNull PsiDocTag[] tags, @NotNull ProblemHolder holder) {
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
            documentedParamNames = set(documentedParamNames);
            if (documentedParamNames.contains(paramName)) {
              holder.problem(tag.getNameElement(), InspectionsBundle.message("inspection.javadoc.problem.duplicate.param", paramName), null);
            }
            documentedParamNames.add(paramName);
          }
        }
      }
      else if (!holder.inspection().isIgnoreDuplicatedThrows() && ("throws".equals(tag.getName()) || "exception".equals(tag.getName()))) {
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
                holder.problem(tag.getNameElement(), InspectionsBundle.message("inspection.javadoc.problem.duplicate.throws", fqName), null);
              }
              documentedExceptions.add(fqName);
            }
          }
        }
      }
      else if (UNIQUE_TAGS.contains(tag.getName())) {
        uniqueTags = set(uniqueTags);
        if (uniqueTags.contains(tag.getName())) {
          holder.problem(tag.getNameElement(), InspectionsBundle.message("inspection.javadoc.problem.duplicate.tag", tag.getName()), null);
        }
        uniqueTags.add(tag.getName());
      }
    }
  }

  static void checkForBadCharacters(@NotNull PsiDocComment docComment, @NotNull ProblemHolder holder) {
    docComment.accept(new PsiRecursiveElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        super.visitElement(element);
        ASTNode node = element.getNode();
        if (node != null && node.getElementType() == JavaDocTokenType.DOC_COMMENT_BAD_CHARACTER) {
          holder.problem(element, InspectionsBundle.message("inspection.illegal.character"), null);
        }
      }
    });
  }

  static void checkMissingTypeParamTags(@NotNull PsiClass psiClass,
                                        @NotNull PsiDocTag[] tags,
                                        @NotNull PsiElement toHighlight,
                                        @NotNull ProblemHolder holder) {
    if (psiClass.hasTypeParameters()) {
      List<PsiTypeParameter> absentParameters = null;

      for (PsiTypeParameter typeParameter : psiClass.getTypeParameters()) {
        if (!hasTagForParameter(tags, typeParameter)) {
          (absentParameters = list(absentParameters)).add(typeParameter);
        }
      }

      if (absentParameters != null) {
        for (PsiTypeParameter typeParameter : absentParameters) {
          String message = InspectionsBundle.message("inspection.javadoc.problem.missing.tag", "<code>@param</code>");
          holder.problem(toHighlight, message, holder.addMissingTagFix("param", "<" + typeParameter.getName() + ">"));
        }
      }
    }
  }

  static void checkMissingReturnTag(@NotNull PsiDocTag[] tags,
                                    @NotNull PsiMethod psiMethod,
                                    @NotNull PsiElement toHighlight,
                                    @NotNull ProblemHolder holder) {
    if (!psiMethod.isConstructor() && !PsiType.VOID.equals(psiMethod.getReturnType())) {
      boolean hasReturnTag = Stream.of(tags).anyMatch(tag -> "return".equals(tag.getName()));
      if (!hasReturnTag) {
        String message = InspectionsBundle.message("inspection.javadoc.problem.missing.tag", "<code>@" + "return" + "</code>");
        holder.problem(toHighlight, message, holder.addMissingTagFix("return", ""));
      }
    }
  }

  static void checkMissingParamTags(@NotNull PsiDocTag[] tags,
                                    @NotNull PsiMethod psiMethod,
                                    @NotNull PsiElement toHighlight,
                                    @NotNull ProblemHolder holder) {
    List<PsiNamedElement> absentParameters = null;

    for (PsiParameter param : psiMethod.getParameterList().getParameters()) {
      if (!hasTagForParameter(tags, param)) {
        (absentParameters = list(absentParameters)).add(param);
      }
    }

    for (PsiTypeParameter parameter : psiMethod.getTypeParameters()) {
      if (!hasTagForParameter(tags, parameter)) {
        (absentParameters = list(absentParameters)).add(parameter);
      }
    }


    if (absentParameters != null) {
      for (PsiNamedElement parameter : absentParameters) {
        String name = parameter.getName();
        if (name != null) {
          String tagText = "<code>" + name + "</code>";
          String message = InspectionsBundle.message("inspection.javadoc.method.problem.missing.param.tag", tagText);
          holder.problem(toHighlight, message, holder.addMissingParamTagFix(name));
        }
      }
    }
  }

  static void checkMissingThrowsTags(@NotNull PsiDocTag[] tags,
                                     @NotNull PsiMethod psiMethod,
                                     @NotNull PsiElement toHighlight,
                                     @NotNull ProblemHolder holder) {
    PsiClassType[] thrownTypes = psiMethod.getThrowsList().getReferencedTypes();
    if (thrownTypes.length <= 0) return;

    Map<PsiClassType, PsiClass> declaredExceptions = ContainerUtil.newLinkedHashMap();

    for (PsiClassType classType : thrownTypes) {
      PsiClass psiClass = classType.resolve();
      if (psiClass != null) {
        declaredExceptions.put(classType, psiClass);
      }
    }

    for (PsiDocTag tag : tags) {
      if ("throws".equals(tag.getName()) || "exception".equals(tag.getName())) {
        PsiDocTagValue value = tag.getValueElement();
        if (value != null) {
          PsiElement firstChild = value.getFirstChild();
          if (firstChild != null) {
            PsiElement psiElement = firstChild.getFirstChild();
            if ((psiElement instanceof PsiJavaCodeReferenceElement)) {
              PsiElement target = ((PsiJavaCodeReferenceElement)psiElement).resolve();
              if (target instanceof PsiClass) {
                for (Iterator<PsiClassType> it = declaredExceptions.keySet().iterator(); it.hasNext(); ) {
                  PsiClass psiClass = declaredExceptions.get(it.next());
                  if (InheritanceUtil.isInheritorOrSelf((PsiClass)target, psiClass, true)) {
                    it.remove();
                  }
                }
              }
            }
          }
        }
      }
    }

    for (PsiClassType declaredException : declaredExceptions.keySet()) {
      String tagText = "<code>@throws</code> " + declaredException.getCanonicalText();
      String message = InspectionsBundle.message("inspection.javadoc.problem.missing.tag", tagText);
      String firstDeclaredException = declaredException.getCanonicalText();
      holder.problem(toHighlight, message, holder.addMissingTagFix("throws", firstDeclaredException));
    }
  }

  static void checkEmptyMethodTagsDescription(@NotNull PsiDocTag[] tags, @NotNull ProblemHolder holder) {
    for (PsiDocTag tag : tags) {
      if ("return".equals(tag.getName())) {
        if (emptyTag(tag)) {
          String tagText = "<code>@return</code>";
          holder.problem(tag.getNameElement(), InspectionsBundle.message("inspection.javadoc.method.problem.missing.tag.description", tagText), null);
        }
      }
      else if ("throws".equals(tag.getName()) || "exception".equals(tag.getName())) {
        if (emptyThrowsTag(tag)) {
          String tagText = "<code>" + tag.getName() + "</code>";
          holder.problem(tag.getNameElement(), InspectionsBundle.message("inspection.javadoc.method.problem.missing.tag.description", tagText), null);
        }
      }
      else if ("param".equals(tag.getName())) {
        PsiDocTagValue valueElement = tag.getValueElement();
        if (valueElement != null && emptyParamTag(tag, valueElement)) {
          String tagText = "<code>@param " + valueElement.getText() + "</code>";
          holder.problem(valueElement, InspectionsBundle.message("inspection.javadoc.method.problem.missing.tag.description", tagText), null);
        }
      }
    }
  }

  private static <T> Set<T> set(Set<T> set) {
    return set != null ? set : ContainerUtil.newHashSet();
  }

  private static <T> List<T> list(List<T> list) {
    return list != null ? list : ContainerUtil.newSmartList();
  }

  private static boolean emptyTag(PsiDocTag tag) {
    return Stream.of(tag.getChildren())
      .filter(e -> e instanceof PsiDocToken && ((PsiDocToken)e).getTokenType() == JavaDocTokenType.DOC_COMMENT_DATA ||
                   e instanceof PsiDocTagValue ||
                   e instanceof PsiInlineDocTag)
      .allMatch(JavadocHighlightUtil::empty);
  }

  private static boolean emptyThrowsTag(PsiDocTag tag) {
    return Stream.of(tag.getChildren())
      .filter(e -> e instanceof PsiDocToken && ((PsiDocToken)e).getTokenType() == JavaDocTokenType.DOC_COMMENT_DATA)
      .allMatch(JavadocHighlightUtil::empty);
  }

  private static boolean emptyParamTag(PsiDocTag tag, PsiDocTagValue valueElement) {
    PsiElement[] dataElements = tag.getDataElements();
    return dataElements.length < 2 || Stream.of(dataElements)
      .filter(e -> e != valueElement)
      .allMatch(JavadocHighlightUtil::empty);
  }

  private static boolean empty(PsiElement e) {
    return e.getText().chars().allMatch(c -> c <= ' ');
  }

  public static boolean hasTagForParameter(@NotNull PsiDocTag[] tags, PsiElement param) {
    for (PsiDocTag tag : tags) {
      if ("param".equals(tag.getName())) {
        PsiDocTagValue value = tag.getValueElement();
        if (value instanceof PsiDocParamRef) {
          PsiReference psiReference = value.getReference();
          if (psiReference != null && psiReference.isReferenceTo(param)) {
            return true;
          }
        }
      }
    }

    return false;
  }
}