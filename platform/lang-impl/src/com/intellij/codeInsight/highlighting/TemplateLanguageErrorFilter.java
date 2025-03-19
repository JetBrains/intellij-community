// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Dennis.Ushakov
 */
public abstract class TemplateLanguageErrorFilter extends HighlightErrorFilter {
  private final @NotNull NotNullLazyValue<TokenSet> myTemplateExpressionEdgeTokens;
  private final @NotNull Class<?> myTemplateFileViewProviderClass;

  private final Set<String> knownLanguageIdSet = new HashSet<>();

  private static final Key<FileViewProvider> TOP_LEVEL_VIEW_PROVIDER = Key.create("TOP_LEVEL_VIEW_PROVIDER");

  // this redundant ctr is here because ExtensionComponentAdapter.getComponentInstance() is not aware of varargs
  protected TemplateLanguageErrorFilter(
    @NotNull TokenSet templateExpressionEdgeTokens,
    @NotNull Class<?> templateFileViewProviderClass)
  {
    this(templateExpressionEdgeTokens, templateFileViewProviderClass, ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  protected TemplateLanguageErrorFilter(
    @NotNull NotNullLazyValue<TokenSet> templateExpressionEdgeTokens,
    @NotNull Class<?> templateFileViewProviderClass)
  {
    this(templateExpressionEdgeTokens, templateFileViewProviderClass, ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  protected TemplateLanguageErrorFilter(@NotNull TokenSet templateExpressionEdgeTokens,
                                        @NotNull Class<?> templateFileViewProviderClass,
                                        String @NotNull ... knownSubLanguageNames) {
    this(NotNullLazyValue.createConstantValue(TokenSet.create(templateExpressionEdgeTokens.getTypes())),
         templateFileViewProviderClass, knownSubLanguageNames);
  }

  protected TemplateLanguageErrorFilter(@NotNull NotNullLazyValue<TokenSet> templateExpressionEdgeTokensHolder,
                                        @NotNull Class<?> templateFileViewProviderClass,
                                        String @NotNull ... knownSubLanguageNames) {
    myTemplateExpressionEdgeTokens = templateExpressionEdgeTokensHolder;
    myTemplateFileViewProviderClass = templateFileViewProviderClass;

    Collections.addAll(knownLanguageIdSet, knownSubLanguageNames);
    knownLanguageIdSet.add("JavaScript");
    knownLanguageIdSet.add("CSS");
  }

  @Override
  public boolean shouldHighlightErrorElement(@NotNull PsiErrorElement element) {
    if (isKnownSubLanguage(element.getParent().getLanguage())) {
      //
      // Immediately discard filters with non-matching template view provider if already known
      //
      FileViewProvider viewProvider = element.getUserData(TOP_LEVEL_VIEW_PROVIDER);
      if (viewProvider == null) {
        viewProvider = InjectedLanguageManager.getInstance(element.getProject()).getTopLevelFile(element).getViewProvider();
        element.putUserData(TOP_LEVEL_VIEW_PROVIDER, viewProvider);
      }
      if (!isTemplateViewProvider(viewProvider)) return true;

      PsiFile psiFile = element.getContainingFile();
      TextRange range = element.getTextRange();

      //
      // An error can occur after template element or before it. Check both.
      //
      if (isNearTemplateExpressions(psiFile, range.getStartOffset(), range.getEndOffset()) ||
          hasErrorElementsBeforeAndUp(element) ||
          PsiTreeUtil.findChildOfType(element, OuterLanguageElement.class) != null) {
        return false;
      }
    }
    return true;
  }

  protected boolean isTemplateViewProvider(FileViewProvider viewProvider) {
    return viewProvider.getClass() == myTemplateFileViewProviderClass;
  }

  private static boolean hasErrorElementsBeforeAndUp(@NotNull PsiElement element) {
    JBIterable<PsiErrorElement> previousErrors = JBIterable
      .generate(element, e -> ObjectUtils.coalesce(e.getPrevSibling(), e.getParent()))
      .skip(1)
      .filter(PsiErrorElement.class);
    return previousErrors.isNotEmpty();
  }

  protected final boolean isNearTemplateExpressions(@NotNull PsiFile file, int start, int end) {
    FileViewProvider viewProvider = file.getViewProvider();
    if (isTemplateViewProvider(viewProvider) && file.getLanguage() != viewProvider.getBaseLanguage()) {
      CharSequence fileText = viewProvider.getContents();
      PsiElement beforeWs = findBaseLanguageElement(viewProvider, CharArrayUtil.shiftBackward(fileText, start - 1, " \t\n"));
      PsiElement afterWs = findBaseLanguageElement(viewProvider, CharArrayUtil.shiftForward(fileText, end, " \t\n"));
      if (isTemplateEdge(afterWs) || isTemplateEdge(beforeWs) || hasTemplateInside(start, end, viewProvider)) {
        return true;
      }
    }

    InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(file.getProject());
    PsiElement host = injectedLanguageManager.getInjectionHost(file);
    if (host != null) {
      start = injectedLanguageManager.injectedToHost(file, start);
      end = injectedLanguageManager.injectedToHost(file, end);
      if (start <= end) {
        return isNearTemplateExpressions(host.getContainingFile(), start, end);
      }
    }

    return false;
  }

  private boolean hasTemplateInside(int start, int end, FileViewProvider viewProvider) {
    PsiElement data = findBaseLanguageElement(viewProvider, start);
    if (data != null) {
      int dataEnd = data.getTextRange().getEndOffset();
      if (dataEnd < end && isTemplateEdge(findBaseLanguageElement(viewProvider, dataEnd))) {
        return true;
      }
    }
    return false;
  }

  private static @Nullable PsiElement findBaseLanguageElement(FileViewProvider viewProvider, int offset) {
    return viewProvider.findElementAt(offset, viewProvider.getBaseLanguage());
  }

  private boolean isTemplateEdge(PsiElement e) {
    return myTemplateExpressionEdgeTokens.get().contains(PsiUtilCore.getElementType(e));
  }

  /**
   * @return whether errors in PSI with the given language should be considered for suppression
   */
  protected boolean isKnownSubLanguage(final @NotNull Language language) {
    for (String knownLanguageId : knownLanguageIdSet) {
      Language knownLanguage = Language.findLanguageByID(knownLanguageId);
      if (knownLanguage != null && (language.is(knownLanguage) || knownLanguage.getDialects().contains(language))) {
        return true;
      }
    }
    return false;
  }
}
