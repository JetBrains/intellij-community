/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.psi.*;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

/**
 * @author peter
 */
public class JavaAwareCompletionData extends CompletionData{

  protected void addLookupItem(Set<LookupElement> set, TailType tailType, @NotNull Object completion, final PsiFile file, final CompletionVariant variant) {
    if (completion instanceof LookupElement && !(completion instanceof LookupItem)) {
      set.add((LookupElement)completion);
      return;
    }

    LookupItem ret = LookupItemUtil.objectToLookupItem(completion);
    if(ret == null) return;

    final InsertHandler insertHandler = variant.getInsertHandler();
    if(insertHandler != null && ret.getInsertHandler() == null) {
      ret.setInsertHandler(insertHandler);
      ret.setTailType(TailType.UNKNOWN);
    }
    else if (tailType != TailType.NONE) {
      ret.setTailType(tailType);
    }

    final Map<Object, Object> itemProperties = variant.getItemProperties();
    for (final Object key : itemProperties.keySet()) {
      ret.setAttribute(key, itemProperties.get(key));
    }
    set.add(ret);
  }

  protected void addKeywords(final Set<LookupElement> set, final PsiElement position, final PrefixMatcher matcher, final PsiFile file,
                                  final CompletionVariant variant, final Object comp, final TailType tailType) {
    final PsiElementFactory factory = JavaPsiFacade.getInstance(file.getProject()).getElementFactory();
    if (comp instanceof String) {
      addKeyword(factory, set, tailType, comp, matcher, file, variant);
    }
    else {
      final CompletionContext context = position.getUserData(CompletionContext.COMPLETION_CONTEXT_KEY);
      if (comp instanceof ContextGetter) {
        final Object[] elements = ((ContextGetter)comp).get(position, context);
        for (Object element : elements) {
          addLookupItem(set, tailType, element, file, variant);
        }
      }
      // TODO: KeywordChooser -> ContextGetter
      else if (comp instanceof KeywordChooser) {
        final String[] keywords = ((KeywordChooser)comp).getKeywords(context, position);
        for (String keyword : keywords) {
          addKeyword(factory, set, tailType, keyword, matcher, file, variant);
        }
      }
    }
  }

  private void addKeyword(PsiElementFactory factory, Set<LookupElement> set, final TailType tailType, final Object comp, final PrefixMatcher matcher,
                                final PsiFile file,
                                final CompletionVariant variant) {
    for (final LookupElement item : set) {
      if (item.getObject().toString().equals(comp.toString())) {
        return;
      }
    }
    if(factory == null){
      addLookupItem(set, tailType, comp, file, variant);
    }
    else{
      try{
        final PsiKeyword keyword = factory.createKeyword((String)comp);
        addLookupItem(set, tailType, keyword, file, variant);
      }
      catch(IncorrectOperationException e){
        addLookupItem(set, tailType, comp, file, variant);
      }
    }
  }

  public void fillCompletions(CompletionParameters parameters, CompletionResultSet result) {
  }
}
