// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.codeInsight.editorActions.wordSelection.DocTagSelectioner;
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.codeInsight.javadoc.SnippetMarkup;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.SuppressionUtilCore;
import com.intellij.codeInspection.javaDoc.JavadocDeclarationInspection;
import com.intellij.codeInspection.javaDoc.MissingJavadocInspection;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.impl.source.javadoc.PsiDocMethodOrFieldRef;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.javadoc.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.string;

public final class JavaDocCompletionContributor extends CompletionContributor implements DumbAware {
  private static final Set<String> INLINE_TAGS_WITH_PARAMETER =
    Set.of("link", "linkplain", "code", "return", "literal", "value", "index", "summary");

  private static final Logger LOG = Logger.getInstance(JavaDocCompletionContributor.class);
  private static final @NonNls String VALUE_TAG = "value";
  private static final @NonNls String LINK_TAG = "link";
  private static final InsertHandler<LookupElement> PARAM_DESCRIPTION_INSERT_HANDLER = (context, item) -> {
    if (context.getCompletionChar() != Lookup.REPLACE_SELECT_CHAR) return;

    context.commitDocument();
    PsiDocTag docTag = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiDocTag.class, false);
    if (docTag != null) {
      Document document = context.getDocument();
      int tagEnd = DocTagSelectioner.getDocTagRange(docTag, document.getCharsSequence(), 0).getEndOffset();
      int tail = context.getTailOffset();
      if (tail < tagEnd) {
        document.deleteString(tail, tagEnd);
      }
    }
  };
  static final PsiElementPattern.Capture<PsiElement> THROWS_TAG_EXCEPTION = psiElement().inside(
    psiElement(PsiDocTag.class).withName(
      string().oneOf(JavaKeywords.THROWS, "exception")));

  private static final PsiElementPattern<?, ?> SNIPPET_ATTRIBUTE_NAME = psiElement(PsiDocToken.class)
    .withElementType(JavaDocTokenType.DOC_TAG_ATTRIBUTE_NAME).inside(psiElement(PsiSnippetAttribute.class));

  private static final PsiElementPattern<?, ?> SNIPPET_ATTRIBUTE_VALUE = psiElement(PsiSnippetAttributeValue.class);

  public JavaDocCompletionContributor() {
    extend(CompletionType.BASIC, PsiJavaPatterns.psiElement(JavaDocTokenType.DOC_TAG_NAME), new TagChooser());

    extend(CompletionType.BASIC, PsiJavaPatterns.psiElement().inside(PsiDocComment.class), new CompletionProvider<>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    @NotNull ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        final PsiElement position = parameters.getPosition();
        boolean isArg = PsiJavaPatterns.psiElement().afterLeaf("(").accepts(position);
        PsiDocTag tag = PsiTreeUtil.getParentOfType(position, PsiDocTag.class);
        boolean onlyConstants = !isArg && tag != null && tag.getName().equals(VALUE_TAG);

        final PsiReference ref = position.getContainingFile().findReferenceAt(parameters.getOffset());
        PsiElement refElement = ref == null ? null : ref.getElement();
        if (refElement instanceof PsiDocParamRef) {
          result = result.withPrefixMatcher(
            refElement.getText().substring(0, parameters.getOffset() - refElement.getTextRange().getStartOffset()));
          for (PsiNamedElement param : getParametersToSuggest(PsiTreeUtil.getParentOfType(position, PsiDocComment.class))) {
            result.addElement(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(param, nameForParamTag(param)),
                                                                    param instanceof PsiTypeParameter ? 0 : 1));
          }
        }
        else if (ref instanceof PsiJavaReference) {
          result = JavaCompletionSorting.addJavaSorting(parameters, result);
          result.stopHere();

          for (LookupElement item : completeJavadocReference(position, (PsiJavaReference)ref)) {
            if (onlyConstants) {
              if (!(item.getObject() instanceof PsiField field)) continue;
              if (!(field.hasModifierProperty(PsiModifier.STATIC) && field.getInitializer() != null &&
                    JavaConstantExpressionEvaluator.computeConstantExpression(field.getInitializer(), false) != null)) {
                continue;
              }
            }

            if (isArg) {
              item = AutoCompletionPolicy.NEVER_AUTOCOMPLETE.applyPolicy(item);
            }
            result.addElement(item);
          }

          JavaCompletionContributor.addAllClasses(parameters, result, new JavaCompletionSession(result));
        }

        if (tag != null && "author".equals(tag.getName())) {
          result.addElement(LookupElementBuilder.create(SystemProperties.getUserName()));
        }
      }
    });

    extend(CompletionType.SMART, THROWS_TAG_EXCEPTION, new CompletionProvider<>() {
      @Override
      public void addCompletions(@NotNull CompletionParameters parameters,
                                 @NotNull ProcessingContext context,
                                 @NotNull CompletionResultSet result) {
        final PsiElement element = parameters.getPosition();
        final Set<PsiClass> throwsSet = new HashSet<>();
        final PsiMethod method = PsiTreeUtil.getContextOfType(element, PsiMethod.class, true);
        if (method != null) {
          for (PsiClassType ref : method.getThrowsList().getReferencedTypes()) {
            final PsiClass exception = ref.resolve();
            if (exception != null && throwsSet.add(exception)) {
              result.addElement(
                TailTypeDecorator.withTail(new JavaPsiClassReferenceElement(exception), TailTypes.humbleSpaceBeforeWordType()));
            }
          }
        }
      }
    });
    
    extend(CompletionType.BASIC, SNIPPET_ATTRIBUTE_NAME, new CompletionProvider<>() {
      static final String[] ATTRIBUTES = {
        PsiSnippetAttribute.CLASS_ATTRIBUTE, PsiSnippetAttribute.FILE_ATTRIBUTE, PsiSnippetAttribute.LANG_ATTRIBUTE,
        PsiSnippetAttribute.REGION_ATTRIBUTE, PsiSnippetAttribute.ID_ATTRIBUTE
      };
      
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    @NotNull ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        PsiSnippetAttributeList list = ObjectUtils.tryCast(parameters.getPosition().getParent().getParent(), PsiSnippetAttributeList.class);
        if (list == null) return;
        for (String attribute : ATTRIBUTES) {
          if (list.getAttribute(attribute) == null) {
            result.addElement(TailTypeDecorator.withTail(LookupElementBuilder.create(attribute).withTailText("=", true),
                                                         TailTypes.equalsType()));
          }
        }
      }
    });
    
    extend(CompletionType.BASIC, SNIPPET_ATTRIBUTE_VALUE, new CompletionProvider<>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    @NotNull ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        PsiSnippetAttributeValue value = (PsiSnippetAttributeValue)parameters.getPosition();
        PsiSnippetAttribute attribute = ObjectUtils.tryCast(value.getParent(), PsiSnippetAttribute.class);
        if (attribute == null) return;
        PsiSnippetAttributeList list = ObjectUtils.tryCast(attribute.getParent(), PsiSnippetAttributeList.class);
        if (list == null) return;
        PsiSnippetDocTagValue snippet = ObjectUtils.tryCast(list.getParent(), PsiSnippetDocTagValue.class);
        if (snippet == null) return;
        boolean alreadyQuoted = value.getText().startsWith("\"");
        switch (attribute.getName()) {
          case PsiSnippetAttribute.REGION_ATTRIBUTE -> {
            SnippetMarkup markup = SnippetMarkup.fromSnippet(snippet);
            if (markup != null) {
              for (String region : markup.getRegions()) {
                addAttributeValue(result, region, alreadyQuoted);
              }
            }
          }
          case PsiSnippetAttribute.LANG_ATTRIBUTE -> {
            result = result.caseInsensitive();
            for (Language language : Language.getRegisteredLanguages()) {
              String id = language.getID();
              if (id.equals("JAVA")) id = "java";
              addAttributeValue(result, id, alreadyQuoted);
            }
          }
        }
      }

      private static void addAttributeValue(@NotNull CompletionResultSet result, String id, boolean alreadyQuoted) {
        if (!alreadyQuoted && !StringUtil.isJavaIdentifier(id)) {
          result.addElement(LookupElementBuilder.create('"' + id + '"').withLookupStrings(List.of(id)));
        }
        else {
          result.addElement(LookupElementBuilder.create(id));
        }
      }
    });
  }

  private @Unmodifiable @NotNull List<LookupElement> completeJavadocReference(PsiElement position, PsiJavaReference ref) {
    JavaCompletionProcessor processor = new JavaCompletionProcessor(position, TrueFilter.INSTANCE, JavaCompletionProcessor.Options.CHECK_NOTHING, Conditions.alwaysTrue());
    ref.processVariants(processor);
    return ContainerUtil.map(processor.getResults(), (completionResult) -> {
      LookupElement item = createReferenceLookupItem(completionResult.getElement());
      item.putUserData(JavaCompletionUtil.FORCE_SHOW_SIGNATURE_ATTR, Boolean.TRUE);
      return item;
    });
  }

  private LookupElement createReferenceLookupItem(Object element) {
    if (element instanceof PsiMethod) {
      return new JavaMethodCallElement((PsiMethod)element) {
        @Override
        public void handleInsert(@NotNull InsertionContext context) {
          new MethodSignatureInsertHandler().handleInsert(context, this);
        }
      };
    }
    if (element instanceof PsiClass) {
      JavaPsiClassReferenceElement classElement = new JavaPsiClassReferenceElement((PsiClass)element);
      classElement.setInsertHandler(JavaClassNameInsertHandler.JAVA_CLASS_INSERT_HANDLER);
      return classElement;
    }

    return LookupItemUtil.objectToLookupItem(element);
  }

  private static PsiParameter getDocTagParam(PsiElement tag) {
    if (tag instanceof PsiDocTag && "param".equals(((PsiDocTag)tag).getName())) {
      PsiDocTagValue value = ((PsiDocTag)tag).getValueElement();
      if (value instanceof PsiDocParamRef) {
        final PsiReference psiReference = value.getReference();
        PsiElement target = psiReference != null ? psiReference.resolve() : null;
        if (target instanceof PsiParameter) {
          return (PsiParameter)target;
        }
      }
    }
    return null;
  }

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    PsiElement position = parameters.getPosition();
    if (PsiDocToken.isDocToken(position, JavaDocTokenType.DOC_TAG_VALUE_TOKEN)) {
      PsiElement parent = position.getParent();
      if (parent instanceof PsiDocTagValue && !(parent instanceof PsiDocParamRef) && !(parent instanceof PsiDocMethodOrFieldRef)) {
        PsiDocTag docTag = ObjectUtils.tryCast(parent.getParent(), PsiDocTag.class);
        if (docTag != null) {
          JavadocManager docManager = JavadocManager.getInstance(parameters.getOriginalFile().getProject());
          JavadocTagInfo info = docManager.getTagInfo(docTag.getName());
          if (info != null) {
            // Avoid suggesting standard tags inside custom tag value, as custom tag may require custom value (e.g., reference)
            suggestTags(parameters, result, position, true);
          }
        }
      }
    }
    if (PsiJavaPatterns.psiElement(JavaDocTokenType.DOC_COMMENT_DATA).accepts(position)) {
      final PsiParameter param = getDocTagParam(position.getParent());
      if (param != null) {
        suggestSimilarParameterDescriptions(result, position, param);
      }

      if (!(position.getParent() instanceof PsiInlineDocTag)) {
        suggestLinkWrappingVariants(parameters, result.withPrefixMatcher(CompletionUtil.findJavaIdentifierPrefix(parameters)), position);
      }

      suggestCodeLiterals(result, position);

      suggestTags(parameters, result, position, false);

      return;
    }

    super.fillCompletionVariants(parameters, result);
  }

  private static void suggestTags(@NotNull CompletionParameters parameters,
                                  @NotNull CompletionResultSet result,
                                  @NotNull PsiElement position,
                                  boolean forceInlineTag) {
    TextRange rangeBefore = new TextRange(position.getTextRange().getStartOffset(), parameters.getOffset());
    boolean hasOpeningBrace = rangeBefore.isEmpty() && position.getPrevSibling() != null &&
                              position.getPrevSibling().textMatches("{");
    boolean isInline = hasOpeningBrace || forceInlineTag || !parameters.getEditor().getDocument().getText(rangeBefore).isBlank();
    List<String> tags = getTags(position, isInline);
    for (String tag : tags) {
      if (isInline) {
        String lookupString = hasOpeningBrace ? "@" + tag : "{@" + tag + "}";
        result.addElement(LookupElementDecorator.withInsertHandler(LookupElementBuilder.create(lookupString), new InlineInsertHandler()));
      }
      else {
        result.addElement(TailTypeDecorator.withTail((LookupElement)LookupElementBuilder.create("@" + tag), TailTypes.insertSpaceType()));
      }
    }
  }

  private static void suggestCodeLiterals(@NotNull CompletionResultSet result, PsiElement position) {
    PsiElement parent = position.getParent();
    if (parent instanceof PsiInlineDocTag && !"code".equals(((PsiInlineDocTag)parent).getName())) {
      return;
    }

    if (!result.getPrefixMatcher().getPrefix().isEmpty()) {
      for (String keyword : ContainerUtil.ar("null", "true", "false")) {
        LookupElementBuilder element = LookupElementBuilder.create(keyword);
        result.addElement(parent instanceof PsiInlineDocTag ? element : wrapIntoCodeTag(element));
      }
    }
  }

  private static @NotNull LookupElementBuilder wrapIntoCodeTag(LookupElementBuilder element) {
    String tagText = "{@code " + element.getLookupString() + "}";
    return element.withPresentableText(tagText).withInsertHandler(
      (context, item) -> context.getDocument().replaceString(context.getStartOffset(), context.getTailOffset(), tagText));
  }

  private void suggestLinkWrappingVariants(@NotNull CompletionParameters parameters,
                                           @NotNull CompletionResultSet result,
                                           PsiElement position) {
    PrefixMatcher matcher = result.getPrefixMatcher();
    int prefixStart = parameters.getOffset() - matcher.getPrefix().length() - position.getTextRange().getStartOffset();
    String text = position.getText();
    if (prefixStart > 0 && text.charAt(prefixStart - 1) == '#') {
      int classNameStart = findClassNameStart(text, prefixStart - 1);
      String mockCommentPrefix = "/** {@link ";
      String mockText = mockCommentPrefix + text.substring(classNameStart) + "}*/";
      PsiDocComment mockComment = JavaPsiFacade.getElementFactory(position.getProject()).createDocCommentFromText(mockText, position);
      PsiJavaReference ref = (PsiJavaReference)mockComment.findReferenceAt(mockCommentPrefix.length() + prefixStart - classNameStart);
      assert ref != null : mockText;
      for (LookupElement element : completeJavadocReference(ref.getElement(), ref)) {
        result.addElement(LookupElementDecorator.withInsertHandler(element, wrapIntoLinkTag((context, item) -> element.handleInsert(context))));
      }
    }
    else if (!matcher.getPrefix().isEmpty()) {
      InsertHandler<JavaPsiClassReferenceElement> handler = wrapIntoLinkTag(JavaClassNameInsertHandler.JAVA_CLASS_INSERT_HANDLER);
      AllClassesGetter.processJavaClasses(parameters, matcher, parameters.getInvocationCount() == 1, psiClass ->
        result.addElement(AllClassesGetter.createLookupItem(psiClass, handler)));
    }
  }

  private static int findClassNameStart(CharSequence text, int sharpOffset) {
    int offset = sharpOffset;
    while (offset > 0 && isQualifiedNamePart(text.charAt(offset - 1))) {
      offset--;
    }
    return offset;
  }

  private static boolean isQualifiedNamePart(char c) {
    return c == '.' || Character.isJavaIdentifierPart(c);
  }

  private static @NotNull <T extends LookupElement> InsertHandler<T> wrapIntoLinkTag(InsertHandler<T> delegate) {
    return (context, item) -> {
      Document document = context.getDocument();

      String link = "{@link ";
      int startOffset = context.getStartOffset();
      int qualifierStart = document.getCharsSequence().charAt(startOffset - 1) == '#'
                            ? findClassNameStart(document.getCharsSequence(), startOffset - 1)
                            : startOffset;

      document.insertString(qualifierStart, link);
      document.insertString(context.getTailOffset(), "}");
      context.setTailOffset(context.getTailOffset() - 1);
      context.getOffsetMap().addOffset(CompletionInitializationContext.START_OFFSET, startOffset + link.length());

      context.commitDocument();
      delegate.handleInsert(context, item);
      if (item.getObject() instanceof PsiField) {
        context.getEditor().getCaretModel().moveToOffset(context.getTailOffset() + 1);
      }
    };
  }

  private static void suggestSimilarParameterDescriptions(CompletionResultSet result, PsiElement position, PsiParameter param) {
    final Set<String> descriptions = new HashSet<>();
    position.getContainingFile().accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        PsiParameter param1 = getDocTagParam(element);
        if (param1 != null && param1 != param &&
            Objects.equals(param1.getName(), param.getName()) && Comparing.equal(param1.getType(), param.getType())) {
          StringBuilder sb = new StringBuilder();
          for (PsiElement psiElement : ((PsiDocTag)element).getDataElements()) {
            if (psiElement != ((PsiDocTag)element).getValueElement()) {
              sb.append(psiElement.getText());
            }
          }
          String text = sb.toString().trim();
          if (text.contains(" ")) {
            descriptions.add(text);
          }
        }

        super.visitElement(element);
      }
    });
    for (String description : descriptions) {
      result.addElement(PrioritizedLookupElement.withPriority(
        LookupElementBuilder.create(description).withInsertHandler(PARAM_DESCRIPTION_INSERT_HANDLER), 1));
    }
  }

  private static class TagChooser extends CompletionProvider<CompletionParameters> {

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {
      final PsiElement position = parameters.getPosition();

      final boolean isInline = position.getContext() instanceof PsiInlineDocTag;

      final List<String> ret = getTags(position, isInline);
      for (String s : ret) {
        if (isInline) {
          result.addElement(LookupElementDecorator.withInsertHandler(LookupElementBuilder.create(s), new InlineInsertHandler()));
        }
        else {
          result.addElement(TailTypeDecorator.withTail(LookupElementBuilder.create(s), TailTypes.insertSpaceType()));
        }
      }
      result.stopHere(); // no word completions at this point
    }
  }

  private static @NotNull List<String> getTags(PsiElement position, boolean isInline) {
    final PsiDocComment comment = PsiTreeUtil.getParentOfType(position, PsiDocComment.class);
    assert comment != null;
    JavadocTagInfo[] infos = getTagInfos(position, comment);
    final List<String> ret = new ArrayList<>();
    for (JavadocTagInfo info : infos) {
      String tagName = info.getName();
      if (tagName.equals(SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME)) continue;
      if (isInline != info.isInline() && !(PsiUtil.getLanguageLevel(comment).isAtLeast(LanguageLevel.JDK_16) && tagName.equals("return")))
        continue;
      if (addSpecialTags(ret, comment, tagName)) {
        ret.add(tagName);
      }
    }

    InspectionProfile inspectionProfile =
      InspectionProjectProfileManager.getInstance(position.getProject()).getCurrentProfile();
    JavadocDeclarationInspection inspection =
      (JavadocDeclarationInspection)inspectionProfile.getUnwrappedTool(JavadocDeclarationInspection.SHORT_NAME, position);
    if (inspection != null) {
      final StringTokenizer tokenizer = new StringTokenizer(inspection.ADDITIONAL_TAGS, ", ");
      while (tokenizer.hasMoreTokens()) {
        ret.add(tokenizer.nextToken());
      }
    }
    return ret;
  }

  /**
   * @return true if simple tag (without parameter) should be added as well
   */
  private static boolean addSpecialTags(List<? super String> result, PsiDocComment comment, String tagName) {
    if ("author".equals(tagName)) {
      result.add(tagName + " " + SystemProperties.getUserName());
      return true;
    }

    if ("param".equals(tagName)) {
      List<PsiNamedElement> parameters = getParametersToSuggest(comment);
      for (PsiNamedElement parameter : parameters) {
        result.add(tagName + " " + nameForParamTag(parameter));
      }
      return !parameters.isEmpty();
    }

    if ("see".equals(tagName)) {
      PsiMember member = PsiTreeUtil.getParentOfType(comment, PsiMember.class);
      if (member instanceof PsiClass) {
        InheritanceUtil.processSupers((PsiClass)member, false, psiClass -> {
          String name = psiClass.getQualifiedName();
          if (StringUtil.isNotEmpty(name) && !CommonClassNames.JAVA_LANG_OBJECT.equals(name)) {
            result.add("see " + name);
          }
          return true;
        });
      }
    }
    return true;
  }

  private static JavadocTagInfo @NotNull [] getTagInfos(PsiElement position, PsiDocComment comment) {
    PsiElement parent = comment.getContext();
    if (parent instanceof PsiJavaFile file && PsiPackage.PACKAGE_INFO_FILE.equals(file.getName())) {
      final String packageName = file.getPackageName();
      parent = JavaPsiFacade.getInstance(position.getProject()).findPackage(packageName);
    }
    return JavadocManager.getInstance(position.getProject()).getTagInfos(parent);
  }

  private static @Unmodifiable List<PsiNamedElement> getParametersToSuggest(PsiDocComment comment) {
    List<PsiNamedElement> allParams = PsiDocParamRef.getAllParameters(comment);
    PsiDocTag[] tags = comment.getTags();
    return ContainerUtil.filter(allParams, param -> !MissingJavadocInspection.hasTagForParameter(tags, param));
  }

  private static String nameForParamTag(PsiNamedElement param) {
    String name = param.getName();
    return param instanceof PsiTypeParameter ? "<" + name + ">" : name;
  }

  private static class InlineInsertHandler implements InsertHandler<LookupElement> {
    @Override
    public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
      String lookupString = item.getLookupString();
      String tagName = lookupString.startsWith("@") ? lookupString.substring(1) :
                   lookupString.startsWith("{@") ? lookupString.substring(2, lookupString.length() - 1) :
                   lookupString;
      boolean hasParameter = INLINE_TAGS_WITH_PARAMETER.contains(tagName);
      if (hasParameter) {
        final Editor editor = context.getEditor();
        final CaretModel caretModel = editor.getCaretModel();
        int offset = caretModel.getOffset();
        if (lookupString.endsWith("}")) {
          offset--;
        }
        if (context.getCompletionChar() != ' ') {
          context.getDocument().insertString(offset, " ");
          caretModel.moveToOffset(offset + 1);
        }
      }
      if (context.getCompletionChar() == Lookup.REPLACE_SELECT_CHAR) {
        final Project project = context.getProject();
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        final Editor editor = context.getEditor();
        final CaretModel caretModel = editor.getCaretModel();
        final int offset = caretModel.getOffset();
        final PsiElement element = context.getFile().findElementAt(offset - 1);
        PsiDocTag tag = PsiTreeUtil.getParentOfType(element, PsiDocTag.class);
        assert tag != null;

        for (PsiElement child = tag.getFirstChild(); child != null; child = child.getNextSibling()) {
          if (PsiDocToken.isDocToken(child, JavaDocTokenType.DOC_INLINE_TAG_END)) return;
        }

        final String name = tag.getName();

        final CharSequence chars = editor.getDocument().getCharsSequence();
        final int currentOffset = caretModel.getOffset();
        if (chars.charAt(currentOffset) == '}') {
          caretModel.moveToOffset(offset + 1);
        }
        else if (chars.charAt(currentOffset + 1) == '}' && chars.charAt(currentOffset) == ' ') {
          caretModel.moveToOffset(offset + 2);
        }
        else if (name.equals(LINK_TAG)) {
          EditorModificationUtilEx.insertStringAtCaret(editor, " }");
          caretModel.moveToOffset(offset + 1);
          editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
          editor.getSelectionModel().removeSelection();
        }
        else {
          EditorModificationUtilEx.insertStringAtCaret(editor, "}");
          caretModel.moveToOffset(offset + 1);
        }
      }
    }
  }

  private static class MethodSignatureInsertHandler implements InsertHandler<JavaMethodCallElement> {
    @Override
    public void handleInsert(@NotNull InsertionContext context, @NotNull JavaMethodCallElement item) {
      PsiDocumentManager.getInstance(context.getProject()).commitDocument(context.getEditor().getDocument());
      final Editor editor = context.getEditor();
      final PsiMethod method = item.getObject();

      Document document = editor.getDocument();
      final CharSequence chars = document.getCharsSequence();
      int endOffset = editor.getCaretModel().getOffset();
      final Project project = context.getProject();
      int afterSharp = CharArrayUtil.shiftBackwardUntil(chars, endOffset - 1, "#") + 1;
      int signatureOffset = afterSharp;

      PsiElement element = context.getFile().findElementAt(signatureOffset - 1);
      final CodeStyleSettings styleSettings = CodeStyle.getSettings(context.getFile());
      PsiDocTag tag = PsiTreeUtil.getParentOfType(element, PsiDocTag.class);
      final PsiMarkdownReferenceLink link = tag == null ? PsiTreeUtil.getParentOfType(element, PsiMarkdownReferenceLink.class) : null;

      if (context.getCompletionChar() == Lookup.REPLACE_SELECT_CHAR && tag != null) {
        PsiDocTagValue valueElement = tag.getValueElement();
        int valueEnd = valueElement != null ? valueElement.getTextRange().getEndOffset() : -1;
        if (valueEnd >= afterSharp) {
          endOffset = valueEnd;
          context.setTailOffset(endOffset);
        }
      }
      document.deleteString(afterSharp, endOffset);
      editor.getCaretModel().moveToOffset(signatureOffset);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().removeSelection();

      String methodName = method.getName();
      int beforeParenth = signatureOffset + methodName.length();
      PsiParameter[] parameters = method.getParameterList().getParameters();

      String signatureSeparator = "," + (styleSettings.getCommonSettings(JavaLanguage.INSTANCE).SPACE_AFTER_COMMA ? " " : "");
      String signatureContent = link == null
                                 ? StringUtil.join(parameters,
                                                   p -> TypeConversionUtil.erasure(p.getType()).getCanonicalText(),
                                                   signatureSeparator)
                                 : StringUtil.join(parameters,
                                                   p -> escapeBrackets(TypeConversionUtil.erasure(p.getType()).getCanonicalText()),
                                                   signatureSeparator);
      String signature = "(" + signatureContent + ")";

      String insertString = methodName + signature;
      if (tag instanceof PsiInlineDocTag) {
        if (chars.charAt(signatureOffset) == '}') {
          afterSharp++;
        }
        else {
          insertString += "} ";
        }
      }
      else if (link != null) {
        if(chars.charAt(signatureOffset) == ']') {
          afterSharp++;
        }
      }
      else {
        insertString += " ";
      }

      document.insertString(signatureOffset, insertString);
      RangeMarker paramListMarker = document.createRangeMarker(TextRange.from(beforeParenth, signature.length()));
      editor.getCaretModel().moveToOffset(afterSharp + insertString.length());
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      PsiDocumentManager.getInstance(project).commitDocument(document);

      shortenReferences(project, editor, context, beforeParenth + 1);

      if (parameters.length > 0) {
        startParameterListTemplate(context, editor, document, project, paramListMarker);
      }
    }

    /** @return Escaped brackets to conform with the JEP-467 */
    private static String escapeBrackets(String input) {
      return input.replace("[", "\\[").replace("]", "\\]");
    }

    private static void startParameterListTemplate(@NotNull InsertionContext context,
                                                   Editor editor,
                                                   Document document,
                                                   Project project, RangeMarker paramListMarker) {
      PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
      int tail = editor.getCaretModel().getOffset();
      if (paramListMarker.isValid() && tail >= paramListMarker.getEndOffset()) {
        PsiDocComment docComment =
          PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), paramListMarker.getStartOffset(), PsiDocComment.class, false);
        if (docComment != null) {
          TemplateImpl template = new TemplateImpl("", "");
          ConstantNode node = new ConstantNode(document.getText(paramListMarker.getTextRange()));
          template.addVariable("PARAMETERS", node, node, true);
          template.addTextSegment(document.getText(TextRange.create(paramListMarker.getEndOffset(), tail)));
          template.addEndVariable();
          template.setToShortenLongNames(false);

          editor.getCaretModel().moveToOffset(paramListMarker.getStartOffset());
          document.deleteString(paramListMarker.getStartOffset(), tail);

          TemplateManager.getInstance(project).startTemplate(editor, template);
        }
      }
    }

    private static void shortenReferences(Project project, Editor editor, InsertionContext context, int offset) {
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
      final PsiElement element = context.getFile().findElementAt(offset);
      final PsiDocComment docComment = PsiTreeUtil.getParentOfType(element, PsiDocComment.class);
      if (!JavaDocUtil.isInsidePackageInfo(docComment)) {
        final PsiDocTagValue tagValue = PsiTreeUtil.getParentOfType(element, PsiDocTagValue.class);
        if (tagValue != null) {
          try {
            JavaCodeStyleManager.getInstance(project).shortenClassReferences(tagValue);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
        PsiDocumentManager.getInstance(context.getProject()).commitAllDocuments();
      }
    }
  }
}
