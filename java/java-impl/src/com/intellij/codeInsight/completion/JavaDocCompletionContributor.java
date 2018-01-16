/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.codeInsight.editorActions.wordSelection.DocTagSelectioner;
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.SuppressionUtilCore;
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspection;
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspectionBase;
import com.intellij.codeInspection.javaDoc.JavadocHighlightUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.javadoc.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.string;

public class JavaDocCompletionContributor extends CompletionContributor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.JavaDocCompletionContributor");
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
      string().oneOf(PsiKeyword.THROWS, "exception")));

  public JavaDocCompletionContributor() {
    extend(CompletionType.BASIC, PsiJavaPatterns.psiElement(JavaDocTokenType.DOC_TAG_NAME), new TagChooser());

    extend(CompletionType.BASIC, PsiJavaPatterns.psiElement().inside(PsiDocComment.class), new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull CompletionResultSet result) {
        final PsiElement position = parameters.getPosition();
        boolean isArg = PsiJavaPatterns.psiElement().afterLeaf("(").accepts(position);
        PsiDocTag tag = PsiTreeUtil.getParentOfType(position, PsiDocTag.class);
        boolean onlyConstants = !isArg && tag != null && tag.getName().equals(VALUE_TAG);

        final PsiReference ref = position.getContainingFile().findReferenceAt(parameters.getOffset());
        if (ref instanceof PsiJavaReference) {
          result = JavaCompletionSorting.addJavaSorting(parameters, result);
          result.stopHere();

          for (LookupElement item : completeJavadocReference(position, (PsiJavaReference)ref)) {
            if (onlyConstants) {
              Object o = item.getObject();
              if (!(o instanceof PsiField)) continue;
              PsiField field = (PsiField) o;
              if (!(field.hasModifierProperty(PsiModifier.STATIC) && field.getInitializer() != null &&
                  JavaConstantExpressionEvaluator.computeConstantExpression(field.getInitializer(), false) != null)) continue;
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

    extend(CompletionType.SMART, THROWS_TAG_EXCEPTION, new CompletionProvider<CompletionParameters>() {
      @Override
      public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final PsiElement element = parameters.getPosition();
        final Set<PsiClass> throwsSet = new HashSet<>();
        final PsiMethod method = PsiTreeUtil.getContextOfType(element, PsiMethod.class, true);
        if(method != null){
          for (PsiClassType ref : method.getThrowsList().getReferencedTypes()) {
            final PsiClass exception = ref.resolve();
            if (exception != null && throwsSet.add(exception)) {
              result.addElement(TailTypeDecorator.withTail(new JavaPsiClassReferenceElement(exception), TailType.HUMBLE_SPACE_BEFORE_WORD));
            }
          }
        }
      }
    });
  }

  @NotNull
  private List<LookupElement> completeJavadocReference(PsiElement position, PsiJavaReference ref) {
    JavaCompletionProcessor processor = new JavaCompletionProcessor(position, TrueFilter.INSTANCE, JavaCompletionProcessor.Options.CHECK_NOTHING, Conditions.alwaysTrue());
    ref.processVariants(processor);
    return ContainerUtil.map(processor.getResults(), (completionResult) -> {
      LookupElement item = createReferenceLookupItem(completionResult.getElement());
      item.putUserData(JavaCompletionUtil.FORCE_SHOW_SIGNATURE_ATTR, Boolean.TRUE);
      return item;
    });
  }

  private LookupElement createReferenceLookupItem(final Object element) {
    if (element instanceof PsiMethod) {
      return new JavaMethodCallElement((PsiMethod)element) {
        @Override
        public void handleInsert(InsertionContext context) {
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
  public void fillCompletionVariants(@NotNull final CompletionParameters parameters, @NotNull final CompletionResultSet result) {
    PsiElement position = parameters.getPosition();
    if (PsiJavaPatterns.psiElement(JavaDocTokenType.DOC_COMMENT_DATA).accepts(position)) {
      final PsiParameter param = getDocTagParam(position.getParent());
      if (param != null) {
        suggestSimilarParameterDescriptions(result, position, param);
      }

      suggestLinkWrappingVariants(parameters, result.withPrefixMatcher(CompletionUtil.findJavaIdentifierPrefix(parameters)), position);

      suggestCodeLiterals(result, position);

      return;
    }

    super.fillCompletionVariants(parameters, result);
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

  @NotNull
  private static LookupElementBuilder wrapIntoCodeTag(LookupElementBuilder element) {
    String tagText = "{@code " + element.getLookupString() + "}";
    return element.withPresentableText(tagText).withInsertHandler(
      (context, item) -> context.getDocument().replaceString(context.getStartOffset(), context.getTailOffset(), tagText));
  }

  private void suggestLinkWrappingVariants(@NotNull CompletionParameters parameters,
                                           @NotNull CompletionResultSet result,
                                           PsiElement position) {
    PrefixMatcher matcher = result.getPrefixMatcher();
    int prefixStart = parameters.getOffset() - matcher.getPrefix().length() - position.getTextRange().getStartOffset();
    if (prefixStart > 0 && position.getText().charAt(prefixStart - 1) == '#') {
      String mockCommentPrefix = "/** {@link ";
      String mockText = mockCommentPrefix + position.getText().substring(prefixStart - 1) + "}*/";
      PsiDocComment mockComment = JavaPsiFacade.getElementFactory(position.getProject()).createDocCommentFromText(mockText, position);
      PsiJavaReference ref = (PsiJavaReference)mockComment.findReferenceAt(mockCommentPrefix.length() + 1);
      assert ref != null : mockText;
      for (LookupElement element : completeJavadocReference(ref.getElement(), ref)) {
        result.addElement(LookupElementDecorator.withInsertHandler(element, wrapIntoLinkTag((context, item) -> element.handleInsert(context))));
      }
    } else if (matcher.getPrefix().length() > 0) {
      InsertHandler<JavaPsiClassReferenceElement> handler = wrapIntoLinkTag(JavaClassNameInsertHandler.JAVA_CLASS_INSERT_HANDLER);
      AllClassesGetter.processJavaClasses(parameters, matcher, parameters.getInvocationCount() == 1, psiClass ->
        result.addElement(AllClassesGetter.createLookupItem(psiClass, handler)));
    }
  }

  @NotNull
  private static <T extends LookupElement> InsertHandler<T> wrapIntoLinkTag(InsertHandler<T> delegate) {
    return (context, item) -> {
      Document document = context.getDocument();

      String link = "{@link ";
      int startOffset = context.getStartOffset();
      int sharpLength = document.getCharsSequence().charAt(startOffset - 1) == '#' ? 1 : 0;

      document.insertString(startOffset - sharpLength, link);
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

  private static void suggestSimilarParameterDescriptions(CompletionResultSet result, PsiElement position, final PsiParameter param) {
    final Set<String> descriptions = ContainerUtil.newHashSet();
    position.getContainingFile().accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        PsiParameter param1 = getDocTagParam(element);
        if (param1 != null && param1 != param &&
            Comparing.equal(param1.getName(), param.getName()) && Comparing.equal(param1.getType(), param.getType())) {
          String text = "";
          for (PsiElement psiElement : ((PsiDocTag)element).getDataElements()) {
            if (psiElement != ((PsiDocTag)element).getValueElement()) {
              text += psiElement.getText();
            }
          }
          text = text.trim();
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
    protected void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
      final List<String> ret = new ArrayList<>();
      final PsiElement position = parameters.getPosition();
      final PsiDocComment comment = PsiTreeUtil.getParentOfType(position, PsiDocComment.class);
      assert comment != null;
      PsiElement parent = comment.getContext();
      if (parent instanceof PsiJavaFile) {
        final PsiJavaFile file = (PsiJavaFile)parent;
        if (PsiPackage.PACKAGE_INFO_FILE.equals(file.getName())) {
          final String packageName = file.getPackageName();
          parent = JavaPsiFacade.getInstance(position.getProject()).findPackage(packageName);
        }
      }

      final boolean isInline = position.getContext() instanceof PsiInlineDocTag;

      for (JavadocTagInfo info : JavadocManager.SERVICE.getInstance(position.getProject()).getTagInfos(parent)) {
        String tagName = info.getName();
        if (tagName.equals(SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME)) continue;
        if (isInline != info.isInline()) continue;
        ret.add(tagName);
        addSpecialTags(ret, comment, tagName);
      }

      InspectionProfile inspectionProfile =
        InspectionProjectProfileManager.getInstance(position.getProject()).getCurrentProfile();
      JavaDocLocalInspection inspection =
        (JavaDocLocalInspection)inspectionProfile.getUnwrappedTool(JavaDocLocalInspectionBase.SHORT_NAME, position);
      if (inspection != null) {
        final StringTokenizer tokenizer = new StringTokenizer(inspection.myAdditionalJavadocTags, ", ");
        while (tokenizer.hasMoreTokens()) {
          ret.add(tokenizer.nextToken());
        }
      }
      for (final String s : ret) {
        if (isInline) {
          result.addElement(LookupElementDecorator.withInsertHandler(LookupElementBuilder.create(s), new InlineInsertHandler()));
        } else {
          result.addElement(TailTypeDecorator.withTail(LookupElementBuilder.create(s), TailType.INSERT_SPACE));
        }
      }
      result.stopHere(); // no word completions at this point
    }

    private static void addSpecialTags(final List<String> result, PsiDocComment comment, String tagName) {
      if ("author".equals(tagName)) {
        result.add(tagName + " " + SystemProperties.getUserName());
        return;
      }

      if ("param".equals(tagName)) {
        PsiMethod psiMethod = PsiTreeUtil.getParentOfType(comment, PsiMethod.class);
        if (psiMethod != null) {
          PsiDocTag[] tags = comment.getTags();
          for (PsiParameter param : psiMethod.getParameterList().getParameters()) {
            if (!JavadocHighlightUtil.hasTagForParameter(tags, param)) {
              result.add(tagName + " " + param.getName());
            }
          }
        }
        return;
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
    }
  }

  private static class InlineInsertHandler implements InsertHandler<LookupElement> {
    @Override
    public void handleInsert(InsertionContext context, LookupElement item) {
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
          if (child instanceof PsiDocToken) {
            PsiDocToken token = (PsiDocToken)child;
            if (token.getTokenType() == JavaDocTokenType.DOC_INLINE_TAG_END) return;
          }
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
          EditorModificationUtil.insertStringAtCaret(editor, " }");
          caretModel.moveToOffset(offset + 1);
          editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
          editor.getSelectionModel().removeSelection();
        }
        else {
          EditorModificationUtil.insertStringAtCaret(editor, "}");
          caretModel.moveToOffset(offset + 1);
        }
      }
    }
  }

  private static class MethodSignatureInsertHandler implements InsertHandler<JavaMethodCallElement> {
    @Override
    public void handleInsert(InsertionContext context, JavaMethodCallElement item) {
      PsiDocumentManager.getInstance(context.getProject()).commitDocument(context.getEditor().getDocument());
      final Editor editor = context.getEditor();
      final PsiMethod method = item.getObject();

      final PsiParameter[] parameters = method.getParameterList().getParameters();
      final StringBuilder buffer = new StringBuilder();

      final CharSequence chars = editor.getDocument().getCharsSequence();
      int endOffset = editor.getCaretModel().getOffset();
      final Project project = context.getProject();
      int afterSharp = CharArrayUtil.shiftBackwardUntil(chars, endOffset - 1, "#") + 1;
      int signatureOffset = afterSharp;

      PsiElement element = context.getFile().findElementAt(signatureOffset - 1);
      final CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(context.getProject());
      PsiDocTag tag = PsiTreeUtil.getParentOfType(element, PsiDocTag.class);
      if (context.getCompletionChar() == Lookup.REPLACE_SELECT_CHAR && tag != null) {
        final PsiDocTagValue valueElement = tag.getValueElement();
        if (valueElement != null) {
          endOffset = valueElement.getTextRange().getEndOffset();
          context.setTailOffset(endOffset);
        }
      }
      editor.getDocument().deleteString(afterSharp, endOffset);
      editor.getCaretModel().moveToOffset(signatureOffset);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().removeSelection();
      buffer.append(method.getName()).append("(");
      final int afterParenth = afterSharp + buffer.length();
      for (int i = 0; i < parameters.length; i++) {
        final PsiType type = TypeConversionUtil.erasure(parameters[i].getType());
        buffer.append(type.getCanonicalText());

        if (i < parameters.length - 1) {
          buffer.append(",");
          if (styleSettings.getCommonSettings(JavaLanguage.INSTANCE).SPACE_AFTER_COMMA) buffer.append(" ");
        }
      }
      buffer.append(")");
      if (!(tag instanceof PsiInlineDocTag)) {
        buffer.append(" ");
      }
      else {
        final int currentOffset = editor.getCaretModel().getOffset();
        if (chars.charAt(currentOffset) == '}') {
          afterSharp++;
        }
        else {
          buffer.append("} ");
        }
      }
      String insertString = buffer.toString();
      EditorModificationUtil.insertStringAtCaret(editor, insertString);
      editor.getCaretModel().moveToOffset(afterSharp + buffer.length());
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());

      shortenReferences(project, editor, context, afterParenth);
    }

    private static void shortenReferences(final Project project, final Editor editor, InsertionContext context, int offset) {
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
