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
import com.intellij.lang.LangBundle;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.Computable;
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

import java.util.*;

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
        CompletionData completionData = ApplicationManager.getApplication().runReadAction(new Computable<CompletionData>() {
          public CompletionData compute() {
            return CompletionUtil.getCompletionData(lastElement, file, startOffset, getCompletionDataByElementInner(lastElement));
          }
        });
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
      public String advertise(@NotNull final CompletionParameters parameters, final ProcessingContext context) {
        if (Calendar.getInstance().get(Calendar.MONTH) == Calendar.APRIL && Calendar.getInstance().get(Calendar.DATE) == 1) {
          final int i = new Random().nextInt(data.length * 5);
          if (i < data.length) {
            return new String(data[i]);
          }
        }
        if (parameters.getPosition().getTextLength() > 42) {
          return new String(data[new Random().nextInt(data.length)]);
        }

        return _advertise(parameters);
      }

      public String handleEmptyLookup(@NotNull final CompletionParameters parameters, final ProcessingContext context) {
        final String ad = _advertise(parameters);
        final String suffix = ad == null ? "" : "; " + StringUtil.decapitalize(ad);
        if (parameters.getCompletionType() == CompletionType.SMART) {
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
    });
  }

  private static String _advertise(final CompletionParameters parameters) {
    if (parameters.getCompletionType() != CompletionType.SMART && shouldSuggestSmartCompletion(parameters.getPosition())) {
      return CompletionBundle.message("completion.smart.hint", KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(
          IdeActions.ACTION_SMART_TYPE_COMPLETION)));
    }
    if (parameters.getCompletionType() != CompletionType.CLASS_NAME && shouldSuggestClassNameCompletion(parameters.getPosition())) {
      return CompletionBundle.message("completion.class.name.hint", KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(
          IdeActions.ACTION_CLASS_NAME_COMPLETION)));
    }
    return null;
  }

  private static boolean shouldSuggestSmartCompletion(final PsiElement element) {
    if (shouldSuggestClassNameCompletion(element)) return false;

    final PsiElement parent = element.getParent();
    if (parent instanceof PsiReferenceExpression && ((PsiReferenceExpression)parent).getQualifier() != null) return false;
    if (parent instanceof PsiReferenceExpression && parent.getParent() instanceof PsiReferenceExpression) return true;

    return new ExpectedTypesGetter().get(element, null).length > 0;
  }

  protected static boolean shouldSuggestClassNameCompletion(final PsiElement element) {
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

  // So. You've managed to find this and now you can rather easily figure out what's written here. But is it necessary? Won't it be
  // a bit more interesting not to see everything at once, but encounter these values incrementally? It's completely up to you.
  private static final byte[][] data = {{66, 114, 111, 117, 103, 104, 116, 32, 116, 111, 32, 121, 111, 117, 32, 98, 121, 32, 99, 114, 101, 97, 116, 111, 114, 115, 32, 111, 102, 32, 70, 97, 98, 114, 105, 113, 117, 101},
     {74, 117, 115, 116, 32, 114, 101, 108, 97, 120},
     {69, 110, 108, 97, 114, 103, 101, 32, 121, 111, 117, 114, 32, 112, 101, 114, 102, 111, 114, 109, 97, 110, 99, 101},
     {83, 111, 117, 110, 100, 116, 114, 97, 99, 107, 32, 97, 118, 97, 105, 108, 97, 98, 108, 101, 32, 111, 110, 32, 70, 68},
     {39, 73, 116, 39, 115, 32, 102, 97, 110, 116, 97, 115, 116, 105, 99, 33, 39, 32, 32, 78, 101, 119, 32, 89, 111, 114, 107, 32, 84, 105, 109, 101, 115},
     {78, 111, 32, 114, 97, 98, 98, 105, 116, 115, 32, 119, 101, 114, 101, 32, 104, 97, 114, 109, 101, 100},
     {65, 110, 100, 32, 119, 104, 121, 32, 100, 105, 100, 32, 121, 111, 117, 32, 100, 111, 32, 116, 104, 97, 116, 63},
     {74, 101, 116, 66, 114, 97, 105, 110, 115, 32, 65, 100, 87, 111, 114, 100, 115, 32, 45, 32, 118, 105, 115, 105, 116, 32, 111, 117, 114, 32, 115, 105, 116, 101, 32, 102, 111, 114, 32, 109, 111, 114, 101, 32, 100, 101, 116, 97, 105, 108, 115},
     {68, 111, 110, 39, 116, 32, 112, 97, 110, 105, 99, 33},
     {72, 105, 32, 116, 104, 101, 114, 101, 33, 32, 73, 39, 109, 32, 73, 68, 69, 65, 46, 32, 72, 111, 119, 32, 100, 111, 32, 121, 111, 117, 32, 102, 101, 101, 108, 32, 116, 111, 100, 97, 121, 63},
     {67, 111, 110, 103, 114, 97, 116, 117, 108, 97, 116, 105, 111, 110, 115, 33, 32, 89, 111, 117, 32, 97, 114, 101, 32, 116, 104, 101, 32, 49, 48, 48, 48, 48, 48, 116, 104, 32, 112, 101, 114, 115, 111, 110, 32, 116, 111, 32, 115, 101, 101, 32, 116, 104, 105, 115, 32, 116, 101, 120, 116},
     {74, 101, 116, 122, 116, 32, 97, 117, 102, 32, 68, 101, 117, 116, 115, 99, 104, 33},
     {84, 104, 101, 32, 116, 114, 117, 116, 104, 32, 105, 115, 32, 111, 117, 116, 32, 116, 104, 101, 114, 101},
     {77, 114, 46, 32, 87, 111, 108, 102, 32, 119, 105, 108, 108, 32, 115, 111, 108, 118, 101, 32, 97, 108, 108, 32, 121, 111, 117, 114, 32, 112, 114, 111, 98, 108, 101, 109, 115, 44, 32, 99, 97, 108, 108, 32, 78, 79, 87},
     {84, 104, 97, 110, 107, 115, 32, 102, 111, 114, 32, 97, 108, 108, 32, 116, 104, 101, 32, 102, 105, 115, 104, 33},
     {65, 110, 121, 32, 99, 111, 105, 110, 99, 105, 100, 101, 110, 99, 101, 32, 116, 111, 32, 114, 101, 97, 108, 32, 99, 111, 109, 112, 108, 101, 116, 105, 111, 110, 32, 119, 111, 117, 108, 100, 32, 98, 101, 32, 103, 114, 101, 97, 116},
     {80, 108, 101, 97, 115, 101, 32, 116, 121, 112, 101, 32, 115, 108, 111, 119, 101, 114, 44, 32, 73, 32, 99, 97, 110, 39, 116, 32, 107, 101, 101, 112, 32, 117, 112, 32, 119, 105, 116, 104, 32, 121, 111, 117},
     {87, 104, 97, 116, 32, 100, 105, 100, 32, 121, 111, 117, 32, 101, 120, 112, 101, 99, 116, 32, 116, 111, 32, 115, 101, 101, 32, 104, 101, 114, 101, 63},
     {65, 114, 101, 32, 121, 111, 117, 32, 115, 117, 114, 101, 63},
     {67, 111, 100, 101, 32, 99, 111, 109, 112, 108, 101, 116, 105, 111, 110, 32, 109, 105, 103, 104, 116, 32, 98, 101, 32, 105, 110, 115, 117, 105, 116, 97, 98, 108, 101, 32, 102, 111, 114, 32, 99, 104, 105, 108, 100, 114, 101, 110, 32, 117, 110, 100, 101, 114, 32, 55},
     {67, 111, 109, 112, 108, 101, 116, 105, 111, 110, 32, 99, 97, 110, 39, 116, 32, 115, 111, 108, 118, 101, 32, 101, 118, 101, 114, 121, 32, 104, 117, 109, 97, 110, 32, 112, 114, 111, 98, 108, 101, 109, 44, 32, 105, 116, 39, 115, 32, 77, 114, 46, 32, 87, 111, 108, 102, 32, 119, 104, 111, 32, 100, 111, 101, 115},
     {66, 101, 119, 97, 114, 101, 32, 111, 102, 32, 116, 104, 101, 32, 76, 101, 111, 112, 97, 114, 100},
     {80, 108, 101, 97, 115, 101, 32, 100, 111, 110, 39, 116, 32, 100, 111, 32, 105, 116, 32, 97, 103, 97, 105, 110},
     {84, 104, 101, 114, 101, 39, 115, 32, 110, 111, 32, 98, 117, 115, 105, 110, 101, 115, 115, 32, 108, 105, 107, 101, 32, 115, 104, 111, 119, 40, 41, 32, 98, 117, 115, 105, 110, 101, 115, 115},
     {73, 102, 32, 121, 111, 117, 32, 99, 97, 110, 39, 116, 32, 114, 101, 97, 100, 32, 116, 104, 105, 115, 32, 116, 101, 120, 116, 44, 32, 100, 111, 110, 39, 116, 32, 104, 101, 115, 105, 116, 97, 116, 101, 32, 116, 111, 32, 97, 115, 107, 32, 116, 104, 101, 32, 115, 117, 112, 112, 111, 114, 116},
     {80, 114, 111, 116, 101, 99, 116, 101, 100, 32, 98, 121, 32, 104, 117, 103, 101, 32, 109, 105, 108, 105, 116, 97, 114, 121, 32, 104, 117, 109, 97, 110, 45, 108, 105, 107, 101, 32, 114, 111, 98, 111, 116, 115},
     {80, 108, 101, 97, 115, 101, 32, 115, 116, 97, 110, 100, 32, 117, 112, 32, 119, 104, 105, 108, 101, 32, 99, 111, 109, 112, 108, 101, 116, 105, 111, 110, 32, 108, 105, 115, 116, 32, 105, 115, 32, 111, 112, 101, 110},
     {84, 104, 105, 110, 107, 105, 110, 103, 44, 32, 112, 108, 101, 97, 115, 101, 32, 100, 111, 110, 39, 116, 32, 105, 110, 116, 101, 114, 114, 117, 112, 116},
     {87, 104, 97, 116, 32, 103, 111, 111, 100, 32, 105, 115, 32, 115, 105, 116, 116, 105, 110, 103, 32, 104, 101, 114, 101, 32, 99, 111, 100, 105, 110, 103, 44, 32, 99, 111, 109, 101, 32, 101, 110, 106, 111, 121, 32, 89, 79, 85, 82, 32, 108, 105, 102, 101},
     {71, 101, 116, 32, 97, 32, 115, 99, 114, 101, 101, 110, 115, 104, 111, 116, 32, 110, 111, 119, 58, 32, 121, 111, 117, 39, 108, 108, 32, 115, 104, 111, 119, 32, 105, 116, 32, 116, 111, 32, 121, 111, 117, 114, 32, 103, 114, 97, 110, 100, 99, 104, 105, 108, 100, 114, 101, 110},
     {84, 104, 101, 32, 109, 111, 115, 116, 32, 105, 109, 112, 111, 114, 116, 97, 110, 116, 32, 109, 101, 115, 115, 97, 103, 101, 115, 32, 97, 114, 101, 32, 100, 105, 115, 112, 108, 97, 121, 101, 100, 32, 104, 101, 114, 101, 32, 105, 110, 32, 105, 110, 118, 105, 115, 105, 98, 108, 101, 32, 99, 111, 108, 111, 114},
     {75, 101, 101, 112, 32, 99, 111, 109, 112, 108, 101, 116, 105, 110, 103, 32, 97, 110, 100, 32, 111, 110, 101, 32, 100, 97, 121, 32, 121, 111, 117, 39, 108, 108, 32, 119, 105, 110},
     {82, 111, 97, 100, 32, 116, 111, 32, 104, 101, 108, 108, 32, 105, 115, 32, 112, 97, 118, 101, 100, 32, 119, 105, 116, 104, 32, 103, 111, 111, 100, 32, 105, 110, 116, 101, 110, 116, 105, 111, 110, 115},
     {67, 111, 108, 108, 101, 99, 116, 32, 49, 48, 48, 32, 73, 68, 69, 65, 32, 108, 111, 103, 111, 115, 32, 97, 110, 100, 32, 103, 101, 116, 32, 97, 32, 109, 111, 117, 115, 101},
     {69, 118, 101, 114, 121, 32, 116, 105, 109, 101, 32, 121, 111, 117, 32, 99, 111, 109, 112, 108, 101, 116, 101, 32, 99, 111, 100, 101, 44, 32, 97, 32, 99, 104, 105, 108, 100, 32, 105, 115, 32, 98, 111, 114, 110},
     {73, 68, 69, 65, 58, 32, 76, 101, 118, 101, 108, 32, 49, 32, 99, 111, 109, 112, 108, 101, 116, 101},
     {73, 102, 32, 121, 111, 117, 32, 100, 111, 110, 39, 116, 32, 119, 114, 105, 116, 101, 32, 73, 68, 69, 65, 44, 32, 116, 104, 101, 110, 32, 73, 68, 69, 65, 32, 119, 105, 108, 108, 32, 119, 114, 105, 116, 101, 32, 121, 111, 117},
     {84, 104, 101, 32, 109, 97, 116, 114, 105, 120, 32, 104, 97, 115, 32, 121, 111, 117},
     {72, 97, 112, 112, 121, 32, 110, 101, 119, 32, 109, 111, 110, 116, 104, 33},
     {67, 111, 109, 112, 108, 101, 116, 105, 111, 110, 39, 115, 32, 97, 108, 108, 32, 97, 114, 111, 117, 110, 100, 32, 121, 111, 117},
     {83, 111, 32, 119, 104, 97, 116, 63},
     {68, 111, 110, 39, 116, 32, 98, 101, 32, 108, 97, 122, 121, 44, 32, 116, 121, 112, 101, 32, 105, 116, 32, 121, 111, 117, 114, 115, 101, 108, 102},
     {68, 111, 32, 121, 111, 117, 32, 117, 110, 100, 101, 114, 115, 116, 97, 110, 100, 32, 104, 111, 119, 32, 104, 97, 114, 100, 32, 105, 116, 32, 105, 115, 32, 116, 111, 32, 102, 105, 110, 100, 32, 97, 108, 108, 32, 116, 104, 101, 115, 101, 32, 118, 97, 114, 105, 97, 110, 116, 115, 63},
     {84, 104, 105, 110, 107, 32, 116, 119, 105, 99, 101, 32, 98, 101, 102, 111, 114, 101, 32, 115, 101, 108, 101, 99, 116, 105, 110, 103, 32, 97, 110, 32, 105, 116, 101, 109, 44, 32, 121, 111, 117, 114, 32, 102, 117, 116, 117, 114, 101, 32, 100, 101, 112, 101, 110, 100, 115, 32, 111, 110, 32, 105, 116},
     {78, 111, 116, 32, 115, 111, 32, 102, 97, 115, 116, 33},
     {67, 111, 109, 112, 108, 101, 116, 105, 111, 110, 32, 105, 115, 32, 115, 109, 97, 114, 116, 101, 114, 32, 116, 104, 97, 110, 32, 121, 111, 117, 32, 116, 104, 105, 110, 107},
     {84, 104, 105, 110, 107, 32, 100, 105, 102, 102, 101, 114, 101, 110, 116},
     {89, 111, 117, 32, 116, 114, 121, 32, 109, 121, 32, 112, 97, 116, 105, 101, 110, 99, 101, 44, 32, 109, 97, 107, 101, 32, 121, 111, 117, 114, 32, 99, 104, 111, 105, 99, 101},
     {83, 104, 111, 117, 108, 100, 32, 99, 111, 109, 112, 108, 101, 116, 105, 111, 110, 32, 98, 101, 32, 99, 97, 110, 99, 101, 108, 108, 101, 100, 44, 32, 97, 32, 100, 105, 115, 97, 115, 116, 101, 114, 32, 98, 101, 121, 111, 110, 100, 32, 121, 111, 117, 114, 32, 105, 109, 97, 103, 105, 110, 97, 116, 105, 111, 110, 32, 119, 105, 108, 108, 32, 111, 99, 99, 117, 114},
     {80, 108, 101, 97, 115, 101, 44, 32, 100, 111, 110, 39, 116, 32, 99, 108, 111, 115, 101, 32, 109, 101, 44, 32, 108, 105, 102, 101, 32, 105, 115, 32, 115, 111, 32, 115, 104, 111, 114, 116, 32, 97, 110, 100, 32, 116, 104, 101, 114, 101, 39, 115, 32, 115, 111, 32, 109, 117, 99, 104, 32, 121, 101, 116, 32, 116, 111, 32, 98, 101, 32, 100, 111, 110, 101, 33},
     {68, 111, 110, 39, 116, 32, 100, 111, 32, 101, 118, 105, 108},
     {65, 112, 112, 108, 97, 117, 115, 101, 33},
     {89, 111, 117, 32, 109, 117, 115, 116, 32, 98, 101, 32, 109, 105, 115, 116, 97, 107, 101, 110, 44, 32, 112, 114, 101, 115, 115, 32, 69, 83, 67},
     {67, 111, 109, 112, 108, 101, 116, 105, 111, 110, 58, 32, 110, 111, 119, 32, 109, 97, 100, 101, 32, 111, 102, 32, 98, 97, 110, 97, 110, 97, 115},
     {80, 108, 101, 97, 115, 101, 32, 114, 101, 97, 100, 32, 99, 111, 109, 112, 108, 101, 116, 105, 111, 110, 32, 108, 105, 99, 101, 110, 115, 101, 32, 97, 103, 114, 101, 101, 109, 101, 110, 116, 32, 99, 97, 114, 101, 102, 117, 108, 108, 121},
     {69, 108, 101, 99, 116, 114, 111, 110, 105, 99, 97, 108, 108, 121, 32, 116, 101, 115, 116, 101, 100},
     {67, 111, 109, 105, 110, 103, 32, 115, 111, 111, 110, 58, 32, 112, 114, 101, 45, 116, 101, 115, 116, 101, 100, 32, 99, 111, 109, 112, 108, 101, 116, 105, 111, 110},
     {80, 108, 101, 97, 115, 101, 32, 102, 97, 115, 116, 101, 110, 32, 121, 111, 117, 114, 32, 115, 101, 97, 116, 98, 101, 108, 116, 32, 119, 104, 105, 108, 101, 32, 99, 111, 109, 112, 108, 101, 116, 105, 110, 103},
     {84, 114, 105, 97, 108, 32, 99, 111, 109, 112, 108, 101, 116, 105, 111, 110, 32, 118, 101, 114, 115, 105, 111, 110, 44, 32, 52, 50, 32, 105, 110, 118, 111, 99, 97, 116, 105, 111, 110, 115, 32, 114, 101, 109, 97, 105, 110, 105, 110, 103},
     {67, 111, 109, 46, 112, 108, 101, 116, 46, 105, 111, 110, 32, 50, 46, 48, 58, 32, 115, 104, 97, 114, 101, 32, 121, 111, 117, 114, 32, 102, 97, 118, 111, 117, 114, 105, 116, 101, 32, 105, 116, 101, 109, 115, 32, 119, 105, 116, 104, 32, 102, 114, 105, 101, 110, 100, 115},
     {79, 102, 102, 105, 99, 105, 97, 108, 108, 121, 32, 98, 97, 110, 110, 101, 100, 32, 98, 121, 32, 82, 117, 115, 115, 105, 97, 110, 32, 79, 114, 116, 104, 111, 100, 111, 120, 32, 67, 104, 117, 114, 99, 104},
     {80, 97, 114, 105, 115, 32, 72, 105, 108, 116, 111, 110, 32, 99, 104, 111, 111, 115, 101, 115, 32, 115, 109, 97, 114, 116, 32, 99, 111, 109, 112, 108, 101, 116, 105, 111, 110},
     {78, 101, 119, 32, 114, 111, 117, 110, 100, 32, 108, 111, 111, 107, 117, 112, 32, 108, 105, 115, 116, 32, 109, 97, 107, 101, 115, 32, 105, 116, 32, 101, 118, 101, 110, 32, 101, 97, 115, 105, 101, 114},
     {84, 104, 101, 114, 101, 39, 115, 32, 110, 111, 32, 99, 111, 109, 112, 108, 101, 116, 105, 111, 110, 32, 105, 110, 32, 116, 104, 105, 115, 32, 119, 111, 114, 108, 100},
     {83, 104, 97, 109, 101, 32, 111, 110, 32, 97, 110, 116, 105, 45, 99, 111, 109, 112, 108, 101, 116, 105, 111, 110, 105, 115, 116, 115}};

  public void beforeCompletion(@NotNull final CompletionInitializationContext context) {
    final PsiFile file = context.getFile();
    final Project project = context.getProject();

    JavaCompletionUtil.initOffsets(file, project, context.getOffsetMap());
  }

}