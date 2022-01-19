// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.javaDoc;

import com.intellij.codeInsight.daemon.impl.analysis.IncreaseLanguageLevelFix;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.reference.RefJavaUtil;
import com.intellij.java.JavaBundle;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.javadoc.*;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class JavadocHighlightUtil {
  private static final String[] TAGS_TO_CHECK = {"author", "version", "since"};
  private static final Set<String> UNIQUE_TAGS = ContainerUtil.newHashSet("return", "deprecated", "serial", "serialData");
  private static final TokenSet SEE_TAG_REFS = TokenSet.create(
    JavaDocElementType.DOC_REFERENCE_HOLDER, JavaDocElementType.DOC_METHOD_OR_FIELD_REF);

  @SuppressWarnings("SameParameterValue")
  public interface ProblemHolder {
    Project project();
    JavaDocLocalInspection inspection();

    void problem(@NotNull PsiElement toHighlight, @NotNull @Nls String message, @Nullable LocalQuickFix fix);
    void problemWithFixes(@NotNull PsiElement toHighlight, @NotNull @Nls String message, LocalQuickFix@NotNull [] fixes);
    void eolProblem(@NotNull PsiElement toHighlight, @NotNull @Nls String message, @Nullable LocalQuickFix fix);

    LocalQuickFix addJavadocFix(@NotNull PsiElement nameIdentifier);
    LocalQuickFix addMissingTagFix(@NotNull String tag, @NotNull String value);
    LocalQuickFix addMissingParamTagFix(@NotNull String name);
    LocalQuickFix registerTagFix(@NotNull String tag);
    LocalQuickFix removeTagFix(@NotNull String tag);
  }

  static boolean isJavaDocRequired(@NotNull JavaDocLocalInspection inspection, @NotNull PsiModifierListOwner element) {
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

  private static int getAccessNumber(JavaDocLocalInspection.Options options) {
    return getAccessNumber(options.ACCESS_JAVADOC_REQUIRED_FOR);
  }

  private static int getAccessNumber(String accessModifier) {
    if (accessModifier.startsWith(JavaDocLocalInspection.NONE)) return 0;
    if (accessModifier.startsWith(JavaDocLocalInspection.PUBLIC)) return 1;
    if (accessModifier.startsWith(JavaDocLocalInspection.PROTECTED)) return 2;
    if (accessModifier.startsWith(JavaDocLocalInspection.PACKAGE_LOCAL)) return 3;
    if (accessModifier.startsWith(JavaDocLocalInspection.PRIVATE)) return 4;

    return 5;
  }

  static void reportMissingTag(@NotNull PsiElement toHighlight, @NotNull ProblemHolder holder) {
    String message = JavaBundle.message("inspection.javadoc.problem.descriptor");
    holder.problem(toHighlight, message, holder.addJavadocFix(toHighlight));
  }

  static void checkRequiredTags(PsiDocTag @NotNull [] tags,
                                @NotNull JavaDocLocalInspection.Options options,
                                @NotNull PsiElement toHighlight,
                                @NotNull ProblemHolder holder) {
    boolean[] isTagRequired = new boolean[TAGS_TO_CHECK.length];
    boolean[] isTagPresent = new boolean[TAGS_TO_CHECK.length];
    boolean someTagsAreRequired = false;

    for (int i = 0; i < TAGS_TO_CHECK.length; i++) {
      someTagsAreRequired |= (isTagRequired[i] = JavaDocLocalInspection.isTagRequired(options, TAGS_TO_CHECK[i]));
    }

    if (!someTagsAreRequired) return;

    for (PsiDocTag tag : tags) {
      int p = ArrayUtil.find(TAGS_TO_CHECK, tag.getName());
      if (p >= 0) isTagPresent[p] = true;
    }

    for (int i = 0; i < TAGS_TO_CHECK.length; i++) {
      if (isTagRequired[i] && !isTagPresent[i]) {
        String tagName = TAGS_TO_CHECK[i];
        String message = JavaBundle.message("inspection.javadoc.problem.missing.tag", "<code>@" + tagName + "</code>");
        holder.problem(toHighlight, message, holder.addMissingTagFix(tagName, ""));
      }
    }
  }

  static void checkRequiredTagDescriptions(PsiDocTag @NotNull [] tags, @NotNull ProblemHolder holder) {
    for (PsiDocTag tag : tags) {
      String tagName = tag.getName();
      if (ArrayUtil.find(TAGS_TO_CHECK, tagName) >= 0 && emptyTag(tag)) {
        String message = JavaBundle.message("inspection.javadoc.problem.missing.tag.description", StringUtil.capitalize(tagName), tagName);
        holder.problem(tag.getNameElement(), message, null);
      }
    }
  }

  static void checkTagValues(PsiDocTag @NotNull [] tags, @Nullable PsiElement context, @NotNull ProblemHolder holder) {
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
            holder.eolProblem(tag, JavaBundle.message("inspection.javadoc.problem.name.expected"), null);
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
          holder.problem(tag.getNameElement(), JavaBundle.message("inspection.javadoc.problem.see.tag.expecting.ref"), null);
        }
        else if (!isValidSeeRef(dataElements)) {
          holder.problem(dataElements[0], JavaBundle.message("inspection.javadoc.problem.see.tag.expecting.ref"), null);
        }
      }

      checkInlineTags(dataElements, holder);
    }
  }

  private static boolean isValidSeeRef(PsiElement... elements) {
    if (SEE_TAG_REFS.contains(elements[0].getNode().getElementType())) return true;

    String text = Stream.of(elements).map(e -> e.getText().trim()).collect(Collectors.joining(" ")).trim();
    if (StringUtil.isQuotedString(text) && text.charAt(0) == '"') return true;

    if (StringUtil.toLowerCase(text).matches("^<a\\s+href=.+")) return true;

    return false;
  }

  static void checkInlineTags(PsiElement @NotNull [] elements, @NotNull ProblemHolder holder) {
    JavadocManager docManager = JavadocManager.SERVICE.getInstance(holder.project());
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

  private static void checkSnippetTag(@NotNull ProblemHolder holder, PsiElement element, PsiInlineDocTag tag) {
    if (element instanceof PsiSnippetDocTag) {
      if (!PsiUtil.getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_18)) {
        PsiElement nameElement = tag.getNameElement();
        if (nameElement != null) {
          String message = JavaBundle.message("inspection.javadoc.problem.snippet.tag.is.not.available");
          holder.problem(nameElement, message, new IncreaseLanguageLevelFix(LanguageLevel.JDK_18));
        }
      }
    }
  }

  private static void checkPointToSelf(@NotNull ProblemHolder holder, PsiInlineDocTag tag) {
    if (holder.inspection().IGNORE_POINT_TO_ITSELF) {
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
    holder.problem(nameElement, JavaBundle.message("inspection.javadoc.problem.pointing.to.itself"), null);
  }

  private static boolean checkTagInfo(PsiDocTag tag, String tagName, JavadocTagInfo tagInfo, ProblemHolder holder) {
    StringTokenizer tokenizer = new StringTokenizer(holder.inspection().myAdditionalJavadocTags, ", ");
    while (tokenizer.hasMoreTokens()) {
      if (Comparing.strEqual(tagName, tokenizer.nextToken())) return true;
    }

    PsiElement nameElement = tag.getNameElement();
    if (nameElement != null) {
      String key = tagInfo == null ? "inspection.javadoc.problem.wrong.tag" : "inspection.javadoc.problem.disallowed.tag";
      LocalQuickFix fix = tagInfo == null ? holder.registerTagFix(tagName) : holder.removeTagFix(tagName);
      final LocalQuickFix[] fixes;
      if (tagInfo != null) {
        fixes = new LocalQuickFix[]{ fix };
      }
      else {
        final String nameElementText = nameElement.getText();
        fixes = new LocalQuickFix[]{ fix, new EncloseWithCodeFix(nameElementText), new EscapeAtQuickFix(nameElementText) };
      }
      holder.problemWithFixes(nameElement, JavaBundle.message(key, "<code>" + tagName + "</code>"), fixes);
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
      holder.problem(docComment.getFirstChild(), JavaBundle.message("inspection.javadoc.problem.descriptor1"), null);
    }
  }

  static void checkDuplicateTags(PsiDocTag @NotNull [] tags, @NotNull ProblemHolder holder) {
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
              holder.problem(tag.getNameElement(), JavaBundle.message("inspection.javadoc.problem.duplicate.param", paramName), null);
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
                holder.problem(tag.getNameElement(), JavaBundle.message("inspection.javadoc.problem.duplicate.throws", fqName), null);
              }
              documentedExceptions.add(fqName);
            }
          }
        }
      }
      else if (UNIQUE_TAGS.contains(tag.getName())) {
        uniqueTags = set(uniqueTags);
        if (uniqueTags.contains(tag.getName())) {
          holder.problem(tag.getNameElement(), JavaBundle.message("inspection.javadoc.problem.duplicate.tag", tag.getName()), null);
        }
        uniqueTags.add(tag.getName());
      }
    }
  }

  static void checkForBadCharacters(@NotNull PsiDocComment docComment, @NotNull ProblemHolder holder) {
    docComment.accept(new PsiRecursiveElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        super.visitElement(element);
        ASTNode node = element.getNode();
        if (node != null && node.getElementType() == JavaDocTokenType.DOC_COMMENT_BAD_CHARACTER) {
          holder.problem(element, JavaBundle.message("inspection.illegal.character"), null);
        }
      }
    });
  }

  static void checkMissingTypeParamTags(@NotNull PsiTypeParameterListOwner owner,
                                        PsiDocTag @NotNull [] tags,
                                        @NotNull PsiElement toHighlight,
                                        @NotNull ProblemHolder holder) {
    if (owner.hasTypeParameters()) {
      List<PsiTypeParameter> absentParameters = null;

      for (PsiTypeParameter typeParameter : owner.getTypeParameters()) {
        if (!hasTagForParameter(tags, typeParameter)) {
          (absentParameters = list(absentParameters)).add(typeParameter);
        }
      }

      if (absentParameters != null) {
        for (PsiTypeParameter typeParameter : absentParameters) {
          String name = typeParameter.getName();
          if (name != null) {
            String tagText = "<code>&lt;" + name + "&gt;</code>";
            String message = JavaBundle.message("inspection.javadoc.method.problem.missing.param.tag", tagText);
            holder.problem(toHighlight, message, holder.addMissingTagFix("param", "<" + name + ">"));
          }
        }
      }
    }
  }

  static void checkMissingReturnTag(PsiDocTag @NotNull [] tags,
                                    @NotNull PsiMethod psiMethod,
                                    @NotNull PsiElement toHighlight,
                                    @NotNull ProblemHolder holder) {
    if (!psiMethod.isConstructor() && !PsiType.VOID.equals(psiMethod.getReturnType())) {
      boolean hasReturnTag = ContainerUtil.exists(tags, tag -> "return".equals(tag.getName()));
      if (!hasReturnTag) {
        String message = JavaBundle.message("inspection.javadoc.problem.missing.tag", "<code>@" + "return" + "</code>");
        holder.problem(toHighlight, message, holder.addMissingTagFix("return", ""));
      }
    }
  }

  static void checkMissingParamTags(PsiDocTag @NotNull [] tags,
                                    @NotNull PsiMethod psiMethod,
                                    @NotNull PsiElement toHighlight,
                                    @NotNull ProblemHolder holder) {
    List<PsiNamedElement> absentParameters = null;

    for (PsiParameter param : psiMethod.getParameterList().getParameters()) {
      if (!hasTagForParameter(tags, param)) {
        (absentParameters = list(absentParameters)).add(param);
      }
    }

    if (absentParameters != null) {
      for (PsiNamedElement parameter : absentParameters) {
        String name = parameter.getName();
        if (name != null) {
          String tagText = "<code>" + name + "</code>";
          String message = JavaBundle.message("inspection.javadoc.method.problem.missing.param.tag", tagText);
          holder.problem(toHighlight, message, holder.addMissingParamTagFix(name));
        }
      }
    }
  }

  static void checkMissingThrowsTags(PsiDocTag @NotNull [] tags,
                                     @NotNull PsiMethod psiMethod,
                                     @NotNull PsiElement toHighlight,
                                     @NotNull ProblemHolder holder) {
    PsiClassType[] thrownTypes = psiMethod.getThrowsList().getReferencedTypes();
    if (thrownTypes.length <= 0) return;

    Map<PsiClassType, PsiClass> declaredExceptions = new LinkedHashMap<>();

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
      String message = JavaBundle.message("inspection.javadoc.problem.missing.tag", tagText);
      String firstDeclaredException = declaredException.getCanonicalText();
      holder.problem(toHighlight, message, holder.addMissingTagFix("throws", firstDeclaredException));
    }
  }

  static void checkEmptyMethodTagsDescription(PsiDocTag @NotNull [] tags,
                                              @NotNull PsiMethod psiMethod,
                                              @NotNull ProblemHolder holder) {
    for (PsiDocTag tag : tags) {
      if (ContainerUtil
        .exists(tag.getChildren(), e -> e instanceof PsiInlineDocTag && ((PsiInlineDocTag)e).getName().equals("inheritDoc"))) {
        continue;
      }
      if ("return".equals(tag.getName())) {
        if (!PsiType.VOID.equals(psiMethod.getReturnType()) && emptyTag(tag)) {
          String tagText = "<code>@return</code>";
          LocalQuickFix fix = holder.removeTagFix("return");
          holder.problem(tag.getNameElement(), JavaBundle.message("inspection.javadoc.method.problem.missing.tag.description", tagText), fix);
        }
      }
      else if ("throws".equals(tag.getName()) || "exception".equals(tag.getName())) {
        if (emptyThrowsTag(tag)) {
          String tagText = "<code>@" + tag.getName() + "</code>";
          LocalQuickFix fix = holder.removeTagFix(tag.getName());
          holder.problem(tag.getNameElement(), JavaBundle.message("inspection.javadoc.method.problem.missing.tag.description", tagText), fix);
        }
      }
      else if ("param".equals(tag.getName())) {
        PsiDocTagValue valueElement = tag.getValueElement();
        if (valueElement != null && emptyParamTag(tag, valueElement)) {
          String tagText = "<code>@param " + valueElement.getText() + "</code>";
          LocalQuickFix fix = holder.removeTagFix("param " + valueElement.getText());
          holder.problem(valueElement, JavaBundle.message("inspection.javadoc.method.problem.missing.tag.description", tagText), fix);
        }
      }
    }
  }

  private static <T> Set<T> set(Set<T> set) {
    return set != null ? set : new HashSet<>();
  }

  private static <T> List<T> list(List<T> list) {
    return list != null ? list : new SmartList<>();
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

  public static boolean hasTagForParameter(PsiDocTag @NotNull [] tags, PsiElement param) {
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

  private static abstract class AbstractUnknownTagFix implements LocalQuickFix {
    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (element == null) return;

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

  private static class EncloseWithCodeFix extends AbstractUnknownTagFix {
    private final String myName;

    private EncloseWithCodeFix(String name) {
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

  private static class EscapeAtQuickFix extends AbstractUnknownTagFix {
    private final String myName;

    private EscapeAtQuickFix(String name) {
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
