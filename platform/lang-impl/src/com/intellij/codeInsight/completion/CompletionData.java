/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.template.Template;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.paths.PsiDynaReference;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.ReflectionCache;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.patterns.StandardPatterns.character;
import static com.intellij.patterns.StandardPatterns.not;

/**
 * @deprecated see {@link com.intellij.codeInsight.completion.CompletionContributor}
 */
public class CompletionData {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.CompletionData");
  private final Set<Class> myFinalScopes = new HashSet<Class>();
  private final List<CompletionVariant> myCompletionVariants = new ArrayList<CompletionVariant>();

  protected CompletionData(){ }

  protected final void declareFinalScope(Class scopeClass){
    myFinalScopes.add(scopeClass);
  }

  protected boolean isScopeFinal(Class scopeClass){
    if(myFinalScopes.contains(scopeClass))
      return true;

    for (final Class myFinalScope : myFinalScopes) {
      if (ReflectionCache.isAssignable(myFinalScope, scopeClass)) {
        return true;
      }
    }
    return false;
  }

  private boolean isScopeAcceptable(PsiElement scope){

    for (final CompletionVariant variant : myCompletionVariants) {
      if (variant.isScopeAcceptable(scope)) {
        return true;
      }
    }
    return false;
  }

  protected void defineScopeEquivalence(Class scopeClass, Class equivClass){
    final Iterator<CompletionVariant> iter = myCompletionVariants.iterator();
    if(isScopeFinal(scopeClass)){
      declareFinalScope(equivClass);
    }

    while(iter.hasNext()){
      final CompletionVariant variant = iter.next();
      if(variant.isScopeClassAcceptable(scopeClass)){
        variant.includeScopeClass(equivClass, variant.isScopeClassFinal(scopeClass));
      }
    }
  }

  /**
   * @deprecated 
   * @see com.intellij.codeInsight.completion.CompletionContributor
   */
  protected void registerVariant(CompletionVariant variant){
    myCompletionVariants.add(variant);
  }

  public void completeReference(final PsiReference reference, final Set<LookupElement> set, @NotNull final PsiElement position, final PsiFile file,
                                final int offset){
    final CompletionVariant[] variants = findVariants(position, file);
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        boolean hasApplicableVariants = false;
        for (CompletionVariant variant : variants) {
          if (variant.hasReferenceFilter()) {
            variant.addReferenceCompletions(reference, position, set, file, CompletionData.this);
            hasApplicableVariants = true;
          }
        }

        if (!hasApplicableVariants) {
          myGenericVariant.addReferenceCompletions(reference, position, set, file, CompletionData.this);
        }
      }
    });
  }

  public void addKeywordVariants(Set<CompletionVariant> set, PsiElement position, final PsiFile file) {
    ContainerUtil.addAll(set, findVariants(position, file));
  }

  public void completeKeywordsBySet(final Set<LookupElement> set, Set<CompletionVariant> variants, final PsiElement position,
                                    final PrefixMatcher matcher,
                                    final PsiFile file){
    for (final CompletionVariant variant : variants) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          variant.addKeywords(set, position, matcher, file, CompletionData.this);
        }
      });
    }
  }

  public String findPrefix(PsiElement insertedElement, int offsetInFile){
    return findPrefixStatic(insertedElement, offsetInFile);
  }

  public CompletionVariant[] findVariants(final PsiElement position, final PsiFile file){
    return ApplicationManager.getApplication().runReadAction(new Computable<CompletionVariant[]>() {
      public CompletionVariant[] compute() {
        final List<CompletionVariant> variants = new ArrayList<CompletionVariant>();
        PsiElement scope = position;
        if(scope == null){
          scope = file;
        }
        while (scope != null) {
          boolean breakFlag = false;
          if (isScopeAcceptable(scope)){

            for (final CompletionVariant variant : myCompletionVariants) {
              if (variant.isVariantApplicable(position, scope) && !variants.contains(variant)) {
                variants.add(variant);
                if (variant.isScopeFinal(scope)) {
                  breakFlag = true;
                }
              }
            }
          }
          if(breakFlag || isScopeFinal(scope.getClass()))
            break;
          scope = scope.getContext();
          if (scope instanceof PsiDirectory) break;
        }
        return variants.toArray(new CompletionVariant[variants.size()]);
      }
    });
  }

  protected final CompletionVariant myGenericVariant = new CompletionVariant() {
    public void addReferenceCompletions(PsiReference reference, PsiElement position, Set<LookupElement> set, final PsiFile file,
                                        final CompletionData completionData) {
      completeReference(reference, position, set, TailType.NONE, file, TrueFilter.INSTANCE, this);
    }
  };

  @Nullable
  public static String getReferencePrefix(@NotNull PsiElement insertedElement, int offsetInFile) {
    final PsiReference ref = insertedElement.getContainingFile().findReferenceAt(offsetInFile);
    if(ref != null) {
      final List<TextRange> ranges = ReferenceRange.getRanges(ref);
      final PsiElement element = ref.getElement();
      final int elementStart = element.getTextRange().getStartOffset();
      for (TextRange refRange : ranges) {
        if (refRange.contains(offsetInFile - elementStart)) {
          final int endIndex = offsetInFile - elementStart;
          final int beginIndex = refRange.getStartOffset();
          if (beginIndex > endIndex) {
            LOG.error("Inconsistent reference (found at offset not included in its range): ref=" + ref + " element=" + element + " text=" + element.getText());
          }
          if (beginIndex < 0) {
            LOG.error("Inconsistent reference (begin < 0): ref=" + ref + " element=" + element + "; begin=" + beginIndex + " text=" + element.getText());
          }
          LOG.assertTrue(endIndex >= 0);
          return element.getText().substring(beginIndex, endIndex);
        }
      }
    }
    return null;
  }

  public static String findPrefixStatic(final PsiElement insertedElement, final int offsetInFile) {
    if(insertedElement == null) return "";

    final String prefix = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        if (!insertedElement.isValid()) {
          return "";
        }
        return getReferencePrefix(insertedElement, offsetInFile);
      }
    });
    if (prefix != null) return prefix;

    if (insertedElement instanceof PsiPlainText || insertedElement instanceof PsiComment) {
      return CompletionUtil.findJavaIdentifierPrefix(insertedElement, offsetInFile);
    }

    return findPrefixDefault(insertedElement, offsetInFile, not(character().javaIdentifierPart()));
  }

  protected static String findPrefixDefault(final PsiElement insertedElement, final int offset, @NotNull final ElementPattern trimStart) {
    String substr = insertedElement.getText().substring(0, offset - insertedElement.getTextRange().getStartOffset());
    if (substr.length() == 0 || Character.isWhitespace(substr.charAt(substr.length() - 1))) return "";

    substr = substr.trim();

    int i = 0;
    while (substr.length() > i && trimStart.accepts(substr.charAt(i))) i++;
    return substr.substring(i).trim();
  }

  public static LookupElement objectToLookupItem(Object object) {
    if (object instanceof LookupElement) return (LookupElement)object;

    String s = null;
    TailType tailType = TailType.NONE;
    if (object instanceof PsiElement){
      s = PsiUtilBase.getName((PsiElement) object);
    }
    else if (object instanceof PsiMetaData) {
      s = ((PsiMetaData)object).getName();
    }
    else if (object instanceof String) {
      s = (String)object;
    }
    else if (object instanceof Template) {
      s = ((Template) object).getKey();
    }
    else if (object instanceof PresentableLookupValue) {
      s = ((PresentableLookupValue)object).getPresentation();
    }
    else {
      LOG.error("Null string for object: " + object + " of class " + (object != null ? object.getClass() : null));
    }

    LookupItem item = new LookupItem(object, s);

    if (object instanceof LookupValueWithUIHint && ((LookupValueWithUIHint) object).isBold()) {
      item.setBold();
    }
    if (object instanceof LookupValueWithTail) {
      item.setAttribute(LookupItem.TAIL_TEXT_ATTR, " " + ((LookupValueWithTail)object).getTailText());
    }
    item.setAttribute(CompletionUtil.TAIL_TYPE_ATTR, tailType);
    return item;
  }


  protected void addLookupItem(Set<LookupElement> set, TailType tailType, @NotNull Object completion, final PsiFile file,
                                     final CompletionVariant variant) {
    LookupElement ret = objectToLookupItem(completion);
    if (ret == null) return;
    if (!(ret instanceof LookupItem)) {
      set.add(ret);
      return;
    }

    LookupItem item = (LookupItem)ret;

    final InsertHandler insertHandler = variant.getInsertHandler();
    if(insertHandler != null && item.getInsertHandler() == null) {
      item.setInsertHandler(insertHandler);
      item.setTailType(TailType.UNKNOWN);
    }
    else if (tailType != TailType.NONE) {
      item.setTailType(tailType);
    }
    final Map<Object, Object> itemProperties = variant.getItemProperties();
    for (final Object key : itemProperties.keySet()) {
      item.setAttribute(key, itemProperties.get(key));
    }

    set.add(ret);
  }

  protected void completeReference(final PsiReference reference, final PsiElement position, final Set<LookupElement> set, final TailType tailType,
                                   final PsiFile file,
                                   final ElementFilter filter,
                                   final CompletionVariant variant) {
    if (reference instanceof PsiMultiReference) {
      for (PsiReference ref : getReferences((PsiMultiReference)reference)) {
        completeReference(ref, position, set, tailType, file, filter, variant);
      }
    }
    else if (reference instanceof PsiDynaReference) {
      for (PsiReference ref : ((PsiDynaReference<?>)reference).getReferences()) {
        completeReference(ref, position, set, tailType, file, filter, variant);
      }
    }
    else{
      final Object[] completions = reference.getVariants();
      if(completions == null) return;

      for (Object completion : completions) {
        if (completion == null) {
          LOG.error("Position=" + position + "\n;Reference=" + reference + "\n;variants=" + Arrays.toString(completions));
        }
        if (completion instanceof PsiElement) {
          final PsiElement psiElement = (PsiElement)completion;
          if (filter.isClassAcceptable(psiElement.getClass()) && filter.isAcceptable(psiElement, position)) {
            addLookupItem(set, tailType, completion, file, variant);
          }
        }
        else {
          if (completion instanceof LookupItem) {
            final Object o = ((LookupItem)completion).getObject();
            if (o instanceof PsiElement) {
              if (!filter.isClassAcceptable(o.getClass()) || !filter.isAcceptable(o, position)) continue;
            }
          }
          addLookupItem(set, tailType, completion, file, variant);
        }
      }
    }
  }

  protected PsiReference[] getReferences(final PsiMultiReference multiReference) {
    final PsiReference[] references = multiReference.getReferences();
    final List<PsiReference> hard = ContainerUtil.findAll(references, new Condition<PsiReference>() {
      public boolean value(final PsiReference object) {
        return !object.isSoft();
      }
    });
    if (!hard.isEmpty()) {
      return hard.toArray(new PsiReference[hard.size()]);
    }
    return references;
  }

  protected void addKeywords(final Set<LookupElement> set, final PsiElement position, final PrefixMatcher matcher, final PsiFile file,
                             final CompletionVariant variant, final Object comp, final TailType tailType) {
    if (comp instanceof String) {
      addKeyword(set, tailType, comp, matcher, file, variant);
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
          addKeyword(set, tailType, keyword, matcher, file, variant);
        }
      }
    }
  }

  private void addKeyword(Set<LookupElement> set, final TailType tailType, final Object comp, final PrefixMatcher matcher,
                          final PsiFile file,
                          final CompletionVariant variant) {
    for (final LookupElement item : set) {
      if (item.getObject().toString().equals(comp.toString())) {
        return;
      }
    }
    addLookupItem(set, tailType, comp, file, variant);
  }
}
