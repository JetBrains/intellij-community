/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PlatformPatterns;
import static com.intellij.patterns.PlatformPatterns.psiElement;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.filters.FilterPositionUtil;
import com.intellij.psi.filters.getters.ExpectedTypesGetter;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author peter
 */
public class JavaCompletionContributor extends CompletionContributor{
  @NonNls public static final String JAVA_LEGACY = "JAVA_LEGACY";

  public void registerCompletionProviders(final CompletionRegistrar registrar) {
    registrar.extend(CompletionType.BASIC, psiElement().inFile(PlatformPatterns.psiFile().withLanguage(StdLanguages.JAVA)), new CompletionProvider<LookupElement, CompletionParameters>() {
      public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext matchingContext, @NotNull final CompletionResultSet<LookupElement> result) {
        result.stopHere();

        CompletionContext context = parameters.getPosition().getUserData(CompletionContext.COMPLETION_CONTEXT_KEY);
        final PsiFile file = parameters.getOriginalFile();
        final int startOffset = context.getStartOffset();
        final PsiElement lastElement = file.findElementAt(startOffset - 1);
        PsiElement insertedElement = parameters.getPosition();
        CompletionData completionData = CompletionUtil.getCompletionData(lastElement, file, startOffset, getCompletionDataByElementInner(lastElement));
        result.setPrefixMatcher(completionData.findPrefix(insertedElement, startOffset));

        final Set<LookupItem> lookupSet = new LinkedHashSet<LookupItem>();
        final PsiReference ref = insertedElement.getContainingFile().findReferenceAt(context.getStartOffset());
        if (ref != null) {
          completionData.completeReference(ref, lookupSet, insertedElement, result.getPrefixMatcher(), context.file, context.getStartOffset());
        }

        final Set<CompletionVariant> keywordVariants = new HashSet<CompletionVariant>();
        completionData.addKeywordVariants(keywordVariants, insertedElement, context.file);
        completionData.completeKeywordsBySet(lookupSet, keywordVariants, insertedElement, result.getPrefixMatcher(), context.file);
        for (final LookupItem item : lookupSet) {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              JavaCompletionUtil.highlightMemberOfContainer(item);
            }
          });

          if (item.getInsertHandler() == null) {
            item.setInsertHandler(new InsertHandler() {
              public void handleInsert(final CompletionContext context, final int startOffset, final LookupData data,
                                       final LookupItem item, final boolean signatureSelected, final char completionChar) {
                analyzeItem(context, item, item.getObject(), parameters.getPosition());
                new DefaultInsertHandler().handleInsert(context, startOffset, data, item, signatureSelected, completionChar);
              }
            });
          }

          result.addElement(item);
        }
      }

      private CompletionData getCompletionDataByElementInner(PsiElement element) {
        if (element != null && PsiTreeUtil.getParentOfType(element, PsiDocComment.class) != null) {
          return JavaCompletionUtil.ourJavaDocCompletionData.getValue();
        }
        return element != null && PsiUtil.getLanguageLevel(element).equals(LanguageLevel.JDK_1_5)
               ? JavaCompletionUtil.ourJava15CompletionData.getValue()
               : JavaCompletionUtil.ourJavaCompletionData.getValue();
      }
    });

    registrar.extend(psiElement().inFile(PlatformPatterns.psiFile().withLanguage(StdLanguages.JAVA)), new CompletionAdvertiser() {
      public String advertise(@NotNull final CompletionParameters parameters, final ProcessingContext context, final PrefixMatcher matcher) {
        if (parameters.getCompletionType() != CompletionType.SMART && shouldSuggestSmartCompletion(parameters.getPosition(), matcher.getPrefix())) {
          return CompletionBundle.message("completion.smart.hint", KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(
              IdeActions.ACTION_SMART_TYPE_COMPLETION)));
        }
        if (parameters.getCompletionType() != CompletionType.CLASS_NAME && shouldSuggestClassNameCompletion(parameters.getPosition(), matcher.getPrefix())) {
          return CompletionBundle.message("completion.class.name.hint", KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(
              IdeActions.ACTION_CLASS_NAME_COMPLETION)));
        }
        return null;
      }
    });
  }

  private static boolean shouldSuggestSmartCompletion(final PsiElement element, String prefix) {
    if (shouldSuggestClassNameCompletion(element, prefix)) return false;

    final PsiElement parent = element.getParent();
    if (parent instanceof PsiReferenceExpression && ((PsiReferenceExpression)parent).getQualifier() != null) return false;
    if (parent instanceof PsiReferenceExpression && parent.getParent() instanceof PsiReferenceExpression) return true;

    return new ExpectedTypesGetter().get(element, null).length > 0;
  }

  protected static boolean shouldSuggestClassNameCompletion(final PsiElement element, String prefix) {
    if (StringUtil.isEmpty(prefix)) return false;
    if (element == null) return false;
    final PsiElement parent = element.getParent();
    if (parent == null) return false;
    return parent.getParent() instanceof PsiTypeElement || parent.getParent() instanceof PsiExpressionStatement || parent.getParent() instanceof PsiReferenceList;
  }

  private static void analyzeItem(final CompletionContext context, final LookupItem item, final Object completion, final PsiElement position) {
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
      if(PsiKeyword.DEFAULT.equals(((PsiKeyword)completion).getText())){
        if (!(position.getParent() instanceof PsiAnnotationMethod)) {
          item.setTailType(TailType.CASE_COLON);
        }
      }
      if(PsiKeyword.SYNCHRONIZED.equals(((PsiKeyword)completion).getText())){
        if (PsiTreeUtil.getParentOfType(position, PsiMember.class, PsiCodeBlock.class) instanceof PsiCodeBlock){
          item.setTailType(TailTypes.SYNCHRONIZED_LPARENTH);
        }
      }
    }
    if (completion instanceof PsiClass) {
      final PsiElement prevElement = FilterPositionUtil.searchNonSpaceNonCommentBack(position);
      if (prevElement != null && prevElement.getParent() instanceof PsiNewExpression) {
        ExpectedTypeInfo[] infos = ExpectedTypesProvider.getInstance(context.project).getExpectedTypes((PsiExpression) prevElement.getParent(), true);
        boolean flag = true;
        PsiTypeParameter[] typeParameters = ((PsiClass)completion).getTypeParameters();
        for (ExpectedTypeInfo info : infos) {
          final PsiType type = info.getType();

          if (info.isArrayTypeInfo()) {
            flag = false;
            break;
          }
          if (typeParameters.length > 0 && type instanceof PsiClassType) {
            if (!((PsiClassType)type).isRaw()) {
              flag = false;
            }
          }
        }
        if (flag) {
          item.setAttribute(LookupItem.NEW_OBJECT_ATTR, "");
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
  }

}