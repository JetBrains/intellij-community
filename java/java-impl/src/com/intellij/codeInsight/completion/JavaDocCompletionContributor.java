/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.codeInsight.completion.scope.CompletionElement;
import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.codeInsight.editorActions.wordSelection.DocTagSelectioner;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspection;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
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
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 05.03.2003
 * Time: 21:40:11
 * To change this template use Options | File Templates.
 */
public class JavaDocCompletionContributor extends CompletionContributor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.JavaDocCompletionContributor");
  private static final @NonNls String VALUE_TAG = "value";
  private static final @NonNls String LINK_TAG = "link";

  public JavaDocCompletionContributor() {
    extend(CompletionType.BASIC, PsiJavaPatterns.psiElement(JavaDocTokenType.DOC_TAG_NAME), new TagChooser());

    extend(CompletionType.BASIC, PsiJavaPatterns.psiElement().inside(PsiDocComment.class), new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final PsiElement position = parameters.getPosition();
        boolean isArg = PsiJavaPatterns.psiElement().afterLeaf("(").accepts(position);
        PsiDocTag tag = PsiTreeUtil.getParentOfType(position, PsiDocTag.class);
        boolean onlyConstants = !isArg && tag != null && tag.getName().equals(VALUE_TAG);

        final PsiReference ref = position.getContainingFile().findReferenceAt(parameters.getOffset());
        if (ref instanceof PsiJavaReference) {
          result.stopHere();

          final JavaCompletionProcessor processor = new JavaCompletionProcessor(position, TrueFilter.INSTANCE, JavaCompletionProcessor.Options.CHECK_NOTHING, Condition.TRUE);
          ((PsiJavaReference) ref).processVariants(processor);

          for (final CompletionElement _item : processor.getResults()) {
            final Object element = _item.getElement();
            LookupItem item = createLookupItem(element);
            if (onlyConstants) {
              Object o = item.getObject();
              if (!(o instanceof PsiField)) continue;
              PsiField field = (PsiField) o;
              if (!(field.hasModifierProperty(PsiModifier.STATIC) && field.getInitializer() != null &&
                  JavaConstantExpressionEvaluator.computeConstantExpression(field.getInitializer(), false) != null)) continue;
            }

            item.putUserData(LookupItem.FORCE_SHOW_SIGNATURE_ATTR, Boolean.TRUE);
            if (isArg) {
              item.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
            }
            result.addElement(item);
          }

          JavaCompletionContributor.addAllClasses(parameters, result, new InheritorsHolder(position, result));
        }
      }

      private LookupItem createLookupItem(final Object element) {
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

        return (LookupItem)LookupItemUtil.objectToLookupItem(element);
      }
    });
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
  public void fillCompletionVariants(final CompletionParameters parameters, final CompletionResultSet result) {

    PsiElement position = parameters.getPosition();
    if (PsiJavaPatterns.psiElement(JavaDocTokenType.DOC_COMMENT_DATA).accepts(position)) {
      final PsiParameter param = getDocTagParam(position.getParent());
      if (param != null) {
        suggestSimilarParameterDescriptions(result, position, param);
      }

      return;
    }

    super.fillCompletionVariants(parameters, result);
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
      result.addElement(LookupElementBuilder.create(description).withInsertHandler(new InsertHandler<LookupElement>() {
        @Override
        public void handleInsert(InsertionContext context, LookupElement item) {
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
        }
      }));
    }
  }

  private static class TagChooser extends CompletionProvider<CompletionParameters> {

    @Override
    protected void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
      final List<String> ret = new ArrayList<String>();
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
        if (tagName.equals(SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME)) continue;
        if (isInline != info.isInline()) continue;
        ret.add(tagName);
        addSpecialTags(ret, comment, tagName);
      }

      InspectionProfile inspectionProfile =
        InspectionProjectProfileManager.getInstance(position.getProject()).getInspectionProfile();
      JavaDocLocalInspection inspection =
        (JavaDocLocalInspection)inspectionProfile.getUnwrappedTool(JavaDocLocalInspection.SHORT_NAME, position);
      final StringTokenizer tokenizer = new StringTokenizer(inspection.myAdditionalJavadocTags, ", ");
      while (tokenizer.hasMoreTokens()) {
        ret.add(tokenizer.nextToken());
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
            if (!JavaDocLocalInspection.isFound(tags, param)) {
              result.add(tagName + " " + param.getName());
            }
          }
        }
        return;
      }

      if ("see".equals(tagName)) {
        PsiMember member = PsiTreeUtil.getParentOfType(comment, PsiMember.class);
        if (member instanceof PsiClass) {
          InheritanceUtil.processSupers((PsiClass)member, false, new Processor<PsiClass>() {
            @Override
            public boolean process(PsiClass psiClass) {
              String name = psiClass.getQualifiedName();
              if (StringUtil.isNotEmpty(name) && !CommonClassNames.JAVA_LANG_OBJECT.equals(name)) {
                result.add("see " + name);
              }
              return true;
            }
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

  private static class MethodSignatureInsertHandler implements InsertHandler<LookupItem> {
    @Override
    public void handleInsert(InsertionContext context, LookupItem item) {
      if (!(item.getObject() instanceof PsiMethod)) {
        return;
      }
      PsiDocumentManager.getInstance(context.getProject()).commitDocument(context.getEditor().getDocument());
      final Editor editor = context.getEditor();
      final PsiMethod method = (PsiMethod)item.getObject();

      final PsiParameter[] parameters = method.getParameterList().getParameters();
      final StringBuffer buffer = new StringBuffer();

      final CharSequence chars = editor.getDocument().getCharsSequence();
      int endOffset = editor.getCaretModel().getOffset();
      final Project project = context.getProject();
      int afterSharp = CharArrayUtil.shiftBackwardUntil(chars, endOffset - 1, "#") + 1;
      int signatureOffset = afterSharp;

      PsiElement element = context.getFile().findElementAt(signatureOffset - 1);
      final CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(element.getProject());
      PsiDocTag tag = PsiTreeUtil.getParentOfType(element, PsiDocTag.class);
      if (context.getCompletionChar() == Lookup.REPLACE_SELECT_CHAR) {
        final PsiDocTagValue valueElement = tag.getValueElement();
        endOffset = valueElement.getTextRange().getEndOffset();
        context.setTailOffset(endOffset);
      }
      editor.getDocument().deleteString(afterSharp, endOffset);
      editor.getCaretModel().moveToOffset(signatureOffset);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().removeSelection();
      buffer.append(method.getName() + "(");
      final int afterParenth = afterSharp + buffer.length();
      for (int i = 0; i < parameters.length; i++) {
        final PsiType type = TypeConversionUtil.erasure(parameters[i].getType());
        buffer.append(type.getCanonicalText());

        if (i < parameters.length - 1) {
          buffer.append(",");
          if (styleSettings.SPACE_AFTER_COMMA) buffer.append(" ");
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
