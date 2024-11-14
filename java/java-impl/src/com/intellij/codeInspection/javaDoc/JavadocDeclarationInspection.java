// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.javaDoc;

import com.intellij.codeInsight.daemon.impl.analysis.IncreaseLanguageLevelFix;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightErrorFilter;
import com.intellij.codeInsight.daemon.impl.quickfix.DeleteElementFix;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.javadoc.SnippetMarkup;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.JavaBundle;
import com.intellij.lang.ASTNode;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
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
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.codeInspection.javaDoc.MissingJavadocInspection.isDeprecated;
import static com.intellij.codeInspection.options.OptPane.*;

public final class JavadocDeclarationInspection extends LocalInspectionTool {
  public static final String SHORT_NAME = "JavadocDeclaration";

  public String ADDITIONAL_TAGS = "";
  public boolean IGNORE_THROWS_DUPLICATE = true;
  public boolean IGNORE_PERIOD_PROBLEM = true;
  public boolean IGNORE_SELF_REFS = false;
  public boolean IGNORE_DEPRECATED_ELEMENTS = false;
  public boolean IGNORE_SYNTAX_ERRORS = false;

  private boolean myIgnoreEmptyDescriptions = false;

  public void setIgnoreEmptyDescriptions(boolean ignoreEmptyDescriptions) {
    myIgnoreEmptyDescriptions = ignoreEmptyDescriptions;
  }

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
  public @NotNull OptPane getOptionsPane() {
    return pane(
      expandableString("ADDITIONAL_TAGS", JavaBundle.message("inspection.javadoc.additional.tags"), ",")
        .description(JavaBundle.message("inspection.javadoc.additional.tags.description")),
      checkbox("IGNORE_THROWS_DUPLICATE", JavaBundle.message("inspection.javadoc.option.ignore.throws"))
        .description(HtmlChunk.raw(JavaBundle.message("inspection.javadoc.option.ignore.throws.description"))),
      checkbox("IGNORE_PERIOD_PROBLEM", JavaBundle.message("inspection.javadoc.option.ignore.period"))
        .description(JavaBundle.message("inspection.javadoc.option.ignore.period.description")),
      checkbox("IGNORE_SELF_REFS", JavaBundle.message("inspection.javadoc.option.ignore.self.ref"))
        .description(JavaBundle.message("inspection.javadoc.option.ignore.self.ref.description")),
      checkbox("IGNORE_DEPRECATED_ELEMENTS", JavaBundle.message("inspection.javadoc.option.ignore.deprecated"))
        .description(JavaBundle.message("inspection.javadoc.option.ignore.deprecated.description")),
      checkbox("IGNORE_SYNTAX_ERRORS", JavaBundle.message("inspection.javadoc.option.ignore.syntax.errors"))
        .description(JavaBundle.message("inspection.javadoc.option.ignore.syntax.errors.description"))
    );
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitJavaFile(@NotNull PsiJavaFile file) {
        if (PsiPackage.PACKAGE_INFO_FILE.equals(file.getName())) {
          checkFile(file, holder);
        }
      }

      @Override
      public void visitErrorElement(@NotNull PsiErrorElement element) {
        if (IGNORE_SYNTAX_ERRORS || !JavaHighlightErrorFilter.isJavaDocProblem(element)) return;
        PsiElement parent = element.getParent();
        TextRange range = element.getTextRangeInParent();
        if (range.isEmpty()) {
          range = new TextRange(range.getStartOffset(), range.getEndOffset() + 1);
          if (range.getEndOffset() > parent.getTextLength()) {
            range = range.shiftLeft(1);
          }
        }
        holder.problem(parent, element.getErrorDescription())
          .range(range)
          .highlight(ProblemHighlightType.GENERIC_ERROR)
          .fix(new UpdateInspectionOptionFix(JavadocDeclarationInspection.this, "IGNORE_SYNTAX_ERRORS", 
                                             JavaBundle.message("inspection.javadoc.option.ignore.syntax.errors"), true))
          .register();
      }

      @Override
      public void visitModule(@NotNull PsiJavaModule module) {
        checkModule(module, holder);
      }

      @Override
      public void visitClass(@NotNull PsiClass aClass) {
        checkClass(aClass, holder);
      }

      @Override
      public void visitField(@NotNull PsiField field) {
        checkField(field, holder);
      }

      @Override
      public void visitMethod(@NotNull PsiMethod method) {
        checkMethod(method, holder);
      }
    };
  }


  private void checkFile(PsiJavaFile file, ProblemsHolder holder) {
    PsiPackage pkg = JavaDirectoryService.getInstance().getPackage(file.getContainingDirectory());
    if (pkg == null) return;

    PsiDocComment docComment = PsiTreeUtil.getChildOfType(file, PsiDocComment.class);
    if (IGNORE_DEPRECATED_ELEMENTS && isDeprecated(pkg, docComment)) {
      return;
    }

    if (docComment != null) {
      PsiDocTag[] tags = docComment.getTags();
      checkBasics(docComment, tags, pkg, holder);
    }
  }

  private void checkModule(PsiJavaModule module, ProblemsHolder holder) {
    PsiDocComment docComment = module.getDocComment();
    if (IGNORE_DEPRECATED_ELEMENTS && isDeprecated(module, docComment)) {
      return;
    }

    if (docComment != null) {
      checkBasics(docComment, docComment.getTags(), module, holder);
    }
  }

  private void checkClass(PsiClass aClass, ProblemsHolder holder) {
    if (aClass instanceof PsiAnonymousClass || aClass instanceof PsiSyntheticClass || aClass instanceof PsiTypeParameter) {
      return;
    }
    if (IGNORE_DEPRECATED_ELEMENTS && aClass.isDeprecated()) {
      return;
    }

    PsiDocComment docComment = aClass.getDocComment();
    if (docComment != null) {
      PsiDocTag[] tags = docComment.getTags();

      checkBasics(docComment, tags, aClass, holder);
    }
  }

  private void checkField(PsiField field, ProblemsHolder holder) {
    if (IGNORE_DEPRECATED_ELEMENTS && isDeprecated(field)) {
      return;
    }

    PsiDocComment docComment = field.getDocComment();
    if (docComment != null) {
      checkBasics(docComment, docComment.getTags(), field, holder);
    }
  }

  private void checkMethod(PsiMethod method, ProblemsHolder holder) {
    if (method instanceof SyntheticElement) {
      return;
    }
    if (IGNORE_DEPRECATED_ELEMENTS && isDeprecated(method)) {
      return;
    }

    PsiDocComment docComment = method.getDocComment();
    if (docComment != null) {
      if (!MissingJavadocInspection.isInherited(docComment, method)) {
        PsiDocTag[] tags = docComment.getTags();

        if (!myIgnoreEmptyDescriptions) {
          checkEmptyMethodTagsDescription(tags, method, holder);
        }

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
        if (!PsiTypes.voidType().equals(psiMethod.getReturnType()) && isEmptyTag(tag)) {
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
            if (!documentedParamNames.add(paramName)) {
              holder.registerProblem(tag.getNameElement(), JavaBundle.message("inspection.javadoc.problem.duplicate.param", paramName));
            }
          }
        }
      }
      else if (!IGNORE_THROWS_DUPLICATE && ("throws".equals(tag.getName()) || "exception".equals(tag.getName()))) {
        PsiDocTagValue value = tag.getValueElement();
        if (value != null) {
          PsiElement firstChild = value.getFirstChild();
          if (firstChild != null && firstChild.getFirstChild() instanceof PsiJavaCodeReferenceElement refElement) {
            PsiElement element = refElement.resolve();
            if (element instanceof PsiClass psiClass) {
              String fqName = psiClass.getQualifiedName();
              documentedExceptions = set(documentedExceptions);
              if (!documentedExceptions.add(fqName)) {
                holder.registerProblem(tag.getNameElement(), JavaBundle.message("inspection.javadoc.problem.duplicate.throws", fqName));
              }
            }
          }
        }
      }
      else if (UNIQUE_TAGS.contains(tag.getName())) {
        uniqueTags = set(uniqueTags);
        if (!uniqueTags.add(tag.getName())) {
          holder.registerProblem(tag.getNameElement(), JavaBundle.message("inspection.javadoc.problem.duplicate.tag", tag.getName()));
        }
      }
    }
  }

  private static void checkForPeriod(@NotNull PsiDocComment docComment, @Nullable PsiElement context, @NotNull ProblemsHolder holder) {
    int dotIndex = docComment.getText().indexOf('.'), tagOffset = 0;
    if (dotIndex >= 0) {  // need to find first valid tag
      for (PsiDocTag tag : docComment.getTags()) {
        String tagName = tag.getName();
        JavadocTagInfo tagInfo = JavadocManager.getInstance(tag.getProject()).getTagInfo(tagName);
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
    JavadocManager docManager = JavadocManager.getInstance(holder.getProject());
    for (PsiElement element : elements) {
      if (element instanceof PsiInlineDocTag tag) {
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
    JavadocManager docManager = JavadocManager.getInstance(holder.getProject());
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
        holder.registerProblem(toHighlight, message, new RemoveTagFix(tagName));
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
    int referenceNumber = 0;
    while (referenceNumber < elements.length && elements[referenceNumber].getText().isBlank()) {
      referenceNumber++;
    }
    if (SEE_TAG_REFS.contains(elements[referenceNumber].getNode().getElementType())) return true;

    String text = Stream.of(elements).map(e -> e.getText().trim()).collect(Collectors.joining(" ")).trim();
    if (StringUtil.isQuotedString(text) && text.charAt(0) == '"') return true;

    if (StringUtil.toLowerCase(text).matches("^<a\\s+href=.+")) return true;

    return false;
  }

  private static void checkSnippetTag(@NotNull ProblemsHolder holder, PsiElement element, PsiInlineDocTag tag) {
    if (element instanceof PsiSnippetDocTag snippet) {
      PsiElement nameElement = tag.getNameElement();
      if (!PsiUtil.isAvailable(JavaFeature.JAVADOC_SNIPPETS, snippet)) {
        if (nameElement != null) {
          String message = JavaBundle.message("inspection.javadoc.problem.snippet.tag.is.not.available");
          holder.registerProblem(nameElement, message, new IncreaseLanguageLevelFix(JavaFeature.JAVADOC_SNIPPETS.getMinimumLevel()));
        }
        return;
      }
      PsiSnippetDocTagValue valueElement = snippet.getValueElement();
      if (valueElement != null && nameElement != null) {
        PsiSnippetDocTagBody body = valueElement.getBody();
        if (body != null) {
          SnippetMarkup markup = SnippetMarkup.fromElement(body);
          markup.visitSnippet(null, new SnippetMarkup.SnippetVisitor() {
            @Override
            public void visitError(SnippetMarkup.@NotNull ErrorMarkup errorMarkup) {
              holder.registerProblem(body, errorMarkup.range(), errorMarkup.message());
            }
          });
          SnippetMarkup externalMarkup = SnippetMarkup.fromExternalSnippet(valueElement);
          if (externalMarkup != null) {
            PsiSnippetAttribute regionAttr = valueElement.getAttributeList().getAttribute("region");
            String region = regionAttr != null && regionAttr.getValue() != null ? regionAttr.getValue().getValue() : null;
            String externalRendered = renderText(externalMarkup, region);
            String inlineRegion = region != null && markup.getRegionStart(region) == null ? null : region;
            String inlineRendered = renderText(markup, inlineRegion);
            if (!externalRendered.equals(inlineRendered)) {
              holder.problem(nameElement, JavaBundle.message("inspection.message.external.snippet.differs.from.inline.snippet"))
                .fix(new SynchronizeInlineMarkupFix(externalRendered))
                .fix(new DeleteElementFix(body))
                .register();
            }
          }
        }
      }
    }
  }

  static @NotNull String renderText(@NotNull SnippetMarkup snippet, @Nullable String region) {
    StringBuilder content = new StringBuilder();
    snippet.visitSnippet(region, true, new SnippetMarkup.SnippetVisitor() {
      @Override
      public void visitPlainText(SnippetMarkup.@NotNull PlainText plainText,
                                 @NotNull List<SnippetMarkup.@NotNull LocationMarkupNode> activeNodes) {
        if (plainText.content().isEmpty()) {
          // Empty content = tag was on a given line. In most of the cases, javadoc tool considers such a line as content
          content.append("\n");
        }
        else {
          content.append(plainText.content());
        }
      }

      @Override
      public void visitError(SnippetMarkup.@NotNull ErrorMarkup errorMarkup) {
      }
    });
    return content.toString().stripTrailing().replaceAll("[ \t]+\n", "\n");
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

  private static class SynchronizeInlineMarkupFix extends PsiUpdateModCommandQuickFix implements HighPriorityAction {
    private final String myText;

    private SynchronizeInlineMarkupFix(@NotNull String text) {
      myText = text;
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("intention.family.name.synchronize.inline.snippet");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiSnippetDocTag snippetTag = PsiTreeUtil.getParentOfType(element, PsiSnippetDocTag.class);
      if (snippetTag == null) return;
      PsiSnippetDocTagValue valueElement = snippetTag.getValueElement();
      if (valueElement == null) return;
      PsiSnippetDocTagBody body = valueElement.getBody();
      if (body == null) return;
      PsiDocComment comment =
        JavaPsiFacade.getElementFactory(project).createDocCommentFromText(StringUtil.join(
          "/**\n* {@snippet :\n" + StreamEx.split(myText, '\n', false)
            .map(line -> "* " + line).joining("\n") + "}*/"
        ));
      PsiSnippetDocTag newSnippet = PsiTreeUtil.getChildOfType(comment, PsiSnippetDocTag.class);
      if (newSnippet == null) return;
      PsiSnippetDocTagValue newValueElement = Objects.requireNonNull(newSnippet.getValueElement());
      newValueElement.getAttributeList().replace(valueElement.getAttributeList());
      snippetTag.replace(newSnippet);
    }
  }
}
