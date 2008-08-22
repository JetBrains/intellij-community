/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.hint.ShowParameterInfoHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.lang.LangBundle;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import static com.intellij.patterns.PsiJavaPatterns.*;
import com.intellij.psi.*;
import com.intellij.psi.filters.getters.ExpectedTypesGetter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author peter
 */
public class JavaCompletionContributor extends CompletionContributor {
  private static final ElementPattern<PsiElement> INSIDE_METHOD_TYPE_ELEMENT = psiElement().inside(
      psiElement(PsiTypeElement.class).withParent(or(psiMethod(), psiElement(PsiVariable.class))));
  private static final ElementPattern<PsiElement> METHOD_START = or(
      psiElement(TokenType.WHITE_SPACE).afterLeaf(INSIDE_METHOD_TYPE_ELEMENT),
      INSIDE_METHOD_TYPE_ELEMENT);

  public boolean fillCompletionVariants(final CompletionParameters parameters, final CompletionResultSet _result) {
    if (parameters.getCompletionType() != CompletionType.BASIC) return true;

    if (parameters.getPosition().getContainingFile().getLanguage() == StdLanguages.JAVA) {
      final PsiFile file = parameters.getOriginalFile();
      final int startOffset = parameters.getOffset();
      final PsiElement lastElement = file.findElementAt(startOffset - 1);
      final PsiElement insertedElement = parameters.getPosition();
      final CompletionData completionData = ApplicationManager.getApplication().runReadAction(new Computable<CompletionData>() {
        public CompletionData compute() {
          return getCompletionDataByElementInner(lastElement);
        }
      });
      final CompletionResultSet result = _result.withPrefixMatcher(completionData.findPrefix(insertedElement, startOffset));

      final Set<LookupElement> lookupSet = new LinkedHashSet<LookupElement>();
      final PsiReference ref = ApplicationManager.getApplication().runReadAction(new Computable<PsiReference>() {
        public PsiReference compute() {
          return insertedElement.getContainingFile().findReferenceAt(startOffset);
        }
      });
      if (ref != null) {
        completionData.completeReference(ref, lookupSet, insertedElement, parameters.getOriginalFile(), startOffset);
      }

      final Set<CompletionVariant> keywordVariants = new HashSet<CompletionVariant>();
      completionData.addKeywordVariants(keywordVariants, insertedElement, parameters.getOriginalFile());
      completionData.completeKeywordsBySet(lookupSet, keywordVariants, insertedElement, result.getPrefixMatcher(), parameters.getOriginalFile());

      for (final LookupElement item : lookupSet) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            JavaCompletionUtil.highlightMemberOfContainer((LookupItem)item);
          }
        });

        if (item.getInsertHandler() == null) {
          ((LookupItem)item).setInsertHandler(new InsertHandler() {
            public void handleInsert(final InsertionContext context, final LookupElement item) {
              analyzeItem((LookupItem)item, item.getObject(), parameters.getPosition());
              new DefaultInsertHandler().handleInsert(context, item);
            }
          });
        }

        result.addElement(item);
      }
      return false;
    }

    return true;
  }

  private static CompletionData getCompletionDataByElementInner(PsiElement element) {
    return element != null && PsiUtil.isLanguageLevel5OrHigher(element)
           ? JavaCompletionUtil.ourJava15CompletionData.getValue()
           : JavaCompletionUtil.ourJavaCompletionData.getValue();
  }


  public String advertise(@NotNull final CompletionParameters parameters) {
    if (!(parameters.getOriginalFile() instanceof PsiJavaFile)) return null;

    if (parameters.getCompletionType() == CompletionType.CLASS_NAME && parameters.getInvocationCount() == 1 && parameters.getOriginalFile().getLanguage() ==
                                                                                                               StdLanguages.JAVA) {
      if (CompletionUtil.shouldShowFeature(parameters, CodeCompletionFeatures.SECOND_CLASS_NAME_COMPLETION)) {
        final String shortcut = getActionShortcut(IdeActions.ACTION_CLASS_NAME_COMPLETION);
        if (shortcut != null) {
          return CompletionBundle.message("completion.class.name.hint.2", shortcut);
        }
      }
    }

    if (parameters.getCompletionType() != CompletionType.SMART && shouldSuggestSmartCompletion(parameters.getPosition())) {
      if (CompletionUtil.shouldShowFeature(parameters, CodeCompletionFeatures.EDITING_COMPLETION_SMARTTYPE_GENERAL)) {
        final String shortcut = getActionShortcut(IdeActions.ACTION_SMART_TYPE_COMPLETION);
        if (shortcut != null) {
          return CompletionBundle.message("completion.smart.hint", shortcut);
        }
      }
    }

    if (parameters.getCompletionType() != CompletionType.CLASS_NAME && shouldSuggestClassNameCompletion(parameters.getPosition())) {
      if (CompletionUtil.shouldShowFeature(parameters, CodeCompletionFeatures.EDITING_COMPLETION_CLASSNAME)) {
        final String shortcut = getActionShortcut(IdeActions.ACTION_CLASS_NAME_COMPLETION);
        if (shortcut != null) {
          return CompletionBundle.message("completion.class.name.hint", shortcut);
        }
      }
    }

    if (parameters.getCompletionType() == CompletionType.SMART && parameters.getInvocationCount() == 1) {
      final PsiType[] psiTypes = ExpectedTypesGetter.getExpectedTypes(parameters.getPosition(), true);
      if (psiTypes.length > 0) {
        if (CompletionUtil.shouldShowFeature(parameters, JavaCompletionFeatures.SECOND_SMART_COMPLETION_TOAR)) {
          final String shortcut = getActionShortcut(IdeActions.ACTION_SMART_TYPE_COMPLETION);
          if (shortcut != null) {
            for (final PsiType psiType : psiTypes) {
              final PsiType type = PsiUtil.extractIterableTypeParameter(psiType, false);
              if (type != null) {
                return CompletionBundle.message("completion.smart.aslist.hint", shortcut, type.getPresentableText());
              }
            }
          }
        }
        if (CompletionUtil.shouldShowFeature(parameters, JavaCompletionFeatures.SECOND_SMART_COMPLETION_ASLIST)) {
          final String shortcut = getActionShortcut(IdeActions.ACTION_SMART_TYPE_COMPLETION);
          if (shortcut != null) {
            for (final PsiType psiType : psiTypes) {
              if (psiType instanceof PsiArrayType) {
                final PsiType componentType = ((PsiArrayType)psiType).getComponentType();
                if (!(componentType instanceof PsiPrimitiveType)) {
                  return CompletionBundle.message("completion.smart.toar.hint", shortcut, componentType.getPresentableText());
                }
              }
            }
          }
        }

        if (CompletionUtil.shouldShowFeature(parameters, JavaCompletionFeatures.SECOND_SMART_COMPLETION_CHAIN)) {
          final String shortcut = getActionShortcut(IdeActions.ACTION_SMART_TYPE_COMPLETION);
          if (shortcut != null) {
            return CompletionBundle.message("completion.smart.chain.hint", shortcut);
          }
        }
      }
    }
    return null;
  }

  public String handleEmptyLookup(@NotNull final CompletionParameters parameters, final Editor editor) {
    if (!(parameters.getOriginalFile() instanceof PsiJavaFile)) return null;

    final String ad = advertise(parameters);
    final String suffix = ad == null ? "" : "; " + StringUtil.decapitalize(ad);
    if (parameters.getCompletionType() == CompletionType.SMART) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {

        final Project project = parameters.getPosition().getProject();
        final PsiFile file = parameters.getOriginalFile();

        PsiExpression expression = PsiTreeUtil.getContextOfType(parameters.getPosition(), PsiExpression.class, true);
        if (expression != null && expression.getParent() instanceof PsiExpressionList) {
          int lbraceOffset = expression.getParent().getTextRange().getStartOffset();
          new ShowParameterInfoHandler().invoke(project, editor, file, lbraceOffset, null);
        }

        if (expression instanceof PsiLiteralExpression) {
          return LangBundle.message("completion.no.suggestions") + suffix;
        }

        if (expression instanceof PsiInstanceOfExpression) {
          final PsiInstanceOfExpression instanceOfExpression = (PsiInstanceOfExpression)expression;
          if (PsiTreeUtil.isAncestor(instanceOfExpression.getCheckType(), parameters.getPosition(), false)) {
            return LangBundle.message("completion.no.suggestions") + suffix;
          }
        }
      }

      final ExpectedTypeInfo[] expectedTypes = JavaCompletionUtil.getExpectedTypes(parameters);
      if (expectedTypes != null) {
        PsiType type = expectedTypes.length == 1 ? expectedTypes[0].getType() : null;
        if (type != null) {
          final PsiType deepComponentType = type.getDeepComponentType();
          if (deepComponentType instanceof PsiClassType) {
            if (((PsiClassType)deepComponentType).resolve() != null) {
              return CompletionBundle.message("completion.no.suggestions.of.type", type.getPresentableText()) + suffix;
            }
            return CompletionBundle.message("completion.unknown.type", type.getPresentableText()) + suffix;
          }
          if (!PsiType.NULL.equals(type)) {
            return CompletionBundle.message("completion.no.suggestions.of.type", type.getPresentableText()) + suffix;
          }
        }
      }
    }
    return LangBundle.message("completion.no.suggestions") + suffix;
  }

  private static boolean shouldSuggestSmartCompletion(final PsiElement element) {
    if (shouldSuggestClassNameCompletion(element)) return false;

    final PsiElement parent = element.getParent();
    if (parent instanceof PsiReferenceExpression && ((PsiReferenceExpression)parent).getQualifier() != null) return false;
    if (parent instanceof PsiReferenceExpression && parent.getParent() instanceof PsiReferenceExpression) return true;

    return new ExpectedTypesGetter().get(element, null).length > 0;
  }

  private static boolean shouldSuggestClassNameCompletion(final PsiElement element) {
    if (element == null) return false;
    final PsiElement parent = element.getParent();
    if (parent == null) return false;
    return parent.getParent() instanceof PsiTypeElement || parent.getParent() instanceof PsiExpressionStatement || parent.getParent() instanceof PsiReferenceList;
  }

  public static void analyzeItem(final LookupItem item, final Object completion, final PsiElement position) {
    if(completion instanceof PsiKeyword){
      if(PsiKeyword.BREAK.equals(((PsiKeyword)completion).getText())
         || PsiKeyword.CONTINUE.equals(((PsiKeyword)completion).getText())){
        PsiElement scope = position;
        while(true){
          if (scope instanceof PsiFile
              || scope instanceof PsiMethod
              || scope instanceof PsiClassInitializer){
            item.setTailType(TailType.SEMICOLON);
            break;
          }
          else if (scope instanceof PsiLabeledStatement){
            item.setTailType(TailType.NONE);
            break;
          }
          scope = scope.getParent();
        }
      }
      if(PsiKeyword.RETURN.equals(((PsiKeyword)completion).getText())){
        PsiElement scope = position;
        while(true){
          if (scope instanceof PsiFile
              || scope instanceof PsiClassInitializer){
            item.setTailType(TailType.NONE);
            break;
          }
          else if (scope instanceof PsiMethod){
            final PsiMethod method = (PsiMethod)scope;
            if(method.isConstructor() || PsiType.VOID == method.getReturnType()) {
              item.setTailType(TailType.SEMICOLON);
            }
            else item.setTailType(TailType.SPACE);

            break;
          }
          scope = scope.getParent();
        }
      }
      if(PsiKeyword.SYNCHRONIZED.equals(((PsiKeyword)completion).getText())){
        if (PsiTreeUtil.getParentOfType(position, PsiMember.class, PsiCodeBlock.class) instanceof PsiCodeBlock){
          item.setTailType(TailTypes.SYNCHRONIZED_LPARENTH);
        }
      }
    }

    if (completion instanceof PsiElement &&
        JavaCompletionUtil.isCompletionOfAnnotationMethod((PsiElement)completion, position)) {
      item.setTailType(TailType.EQ);
    }
  }

  public void beforeCompletion(@NotNull final CompletionInitializationContext context) {
    final PsiFile file = context.getFile();
    final Project project = context.getProject();

    JavaCompletionUtil.initOffsets(file, project, context.getOffsetMap());

    if (context.getCompletionType() == CompletionType.BASIC && file instanceof PsiJavaFile) {
      final PsiElement element = file.findElementAt(context.getStartOffset());
      if (METHOD_START.accepts(element)) {
        PsiElement decl = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
        if (decl == null) decl = PsiTreeUtil.getParentOfType(element, PsiVariable.class);
        if (decl != null) {
          PsiElement sibling = decl.getPrevSibling();
          while (sibling != null && (sibling instanceof PsiWhiteSpace || sibling instanceof PsiErrorElement)) {
            sibling = sibling.getPrevSibling();
          }
          if (sibling instanceof PsiClassInitializer && ((PsiClassInitializer) sibling).getBody().getRBrace() == null ||
              sibling instanceof PsiMethod && ((PsiMethod) sibling).getBody() != null && ((PsiMethod) sibling).getBody().getRBrace() == null) {
            final int textOffset = decl.getTextOffset();
            context.setFileCopyPatcher(new FileCopyPatcher() {
              public void patchFileCopy(@NotNull final PsiFile fileCopy, @NotNull final Document document, @NotNull final OffsetMap map) {
                document.replaceString(map.getOffset(CompletionInitializationContext.START_OFFSET),
                                       Math.max(map.getOffset(CompletionInitializationContext.SELECTION_END_OFFSET), textOffset),
                                       CompletionInitializationContext.DUMMY_IDENTIFIER.trim());
              }
            });
            return;
          }
        }
      }

      if (psiElement(PsiIdentifier.class).withParent(PsiMethod.class).accepts(element)) {
        return;
      }

      context.setFileCopyPatcher(new DummyIdentifierPatcher(CompletionInitializationContext.DUMMY_IDENTIFIER.trim()));
    }
  }

}
