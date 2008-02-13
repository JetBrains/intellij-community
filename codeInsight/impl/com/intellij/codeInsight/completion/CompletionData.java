package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.patterns.ElementPattern;
import static com.intellij.patterns.StandardPatterns.character;
import static com.intellij.patterns.StandardPatterns.not;
import com.intellij.psi.*;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.util.ReflectionCache;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 30.01.2003
 * Time: 16:58:32
 * To change this template use Options | File Templates.
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

  protected void registerVariant(CompletionVariant variant){
    myCompletionVariants.add(variant);
  }

  public void completeReference(PsiReference reference, Set<LookupItem> set, @NotNull PsiElement position,
                                final PrefixMatcher matcher, final PsiFile file, final int offset){
    final CompletionVariant[] variants = findVariants(position, file);
    boolean hasApplicableVariants = false;

    for (CompletionVariant variant : variants) {
      if (variant.hasReferenceFilter()) {
        variant.addReferenceCompletions(reference, position, set, matcher, file, this);
        hasApplicableVariants = true;
      }
    }

    if(!hasApplicableVariants){
      myGenericVariant.addReferenceCompletions(reference, position, set, matcher, file, this);
    }
  }

  public void addKeywordVariants(Set<CompletionVariant> set, PsiElement position, final PsiFile file){
    set.addAll(Arrays.asList(findVariants(position, file)));
  }

  public void completeKeywordsBySet(Set<LookupItem> set, Set<CompletionVariant> variants, PsiElement position,
                                           final PrefixMatcher matcher,
                                           final PsiFile file){
    for (final CompletionVariant variant : variants) {
      variant.addKeywords(set, position, matcher, file, this);
    }
  }

  public String findPrefix(PsiElement insertedElement, int offsetInFile){
    return findPrefixStatic(insertedElement, offsetInFile);
  }

  public CompletionVariant[] findVariants(final PsiElement position, final PsiFile file){
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

  protected final CompletionVariant myGenericVariant = new CompletionVariant() {
    public void addReferenceCompletions(PsiReference reference, PsiElement position, Set<LookupItem> set, final PrefixMatcher matcher, final PsiFile file,
                                        final CompletionData completionData) {
      completeReference(reference, position, set, TailType.NONE, matcher, file, TrueFilter.INSTANCE, this);
    }
  };

  @Nullable
  public static String getReferencePrefix(@NotNull PsiElement insertedElement, int offsetInFile) {
    int offsetInElement = offsetInFile - insertedElement.getTextRange().getStartOffset();
    //final PsiReference ref = insertedElement.findReferenceAt(offsetInElement);
    final PsiReference ref = insertedElement.getContainingFile().findReferenceAt(insertedElement.getTextRange().getStartOffset() + offsetInElement);
    if(ref != null) {
      offsetInElement = offsetInFile - ref.getElement().getTextRange().getStartOffset();

      String result = ref.getElement().getText().substring(ref.getRangeInElement().getStartOffset(), offsetInElement);
      if(result.indexOf('(') > 0){
        result = result.substring(0, result.indexOf('('));
      }

      return result;
    }

    return null;
  }

  public static String findPrefixStatic(PsiElement insertedElement, int offsetInFile) {
    if(insertedElement == null) return "";

    final String prefix = getReferencePrefix(insertedElement, offsetInFile);
    if (prefix != null) return prefix;

    if (insertedElement instanceof PsiPlainText) return "";

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

  @Nullable
  protected LookupItem addLookupItem(Set<LookupItem> set, TailType tailType, @NotNull Object completion, PrefixMatcher matcher, final PsiFile file,
                                         final CompletionVariant variant) {
    LookupItem ret = LookupItemUtil.objectToLookupItem(completion);
    if(ret == null) return null;

    final InsertHandler insertHandler = variant.getInsertHandler();
    if(insertHandler != null && ret.getInsertHandler() == null) {
      ret.setAttribute(LookupItem.INSERT_HANDLER_ATTR, insertHandler);
      ret.setTailType(TailType.UNKNOWN);
    }
    else if (tailType != TailType.NONE) {
      ret.setTailType(tailType);
    }

    if(matcher.prefixMatches(ret)){
      set.add(ret);
      return ret;
    }

    return null;
  }

  protected void completeReference(final PsiReference reference, final PsiElement position, final Set<LookupItem> set, final TailType tailType,
                                            final PrefixMatcher matcher,
                                            final PsiFile file,
                                            final ElementFilter filter,
                                            final CompletionVariant variant) {
    if (reference instanceof PsiMultiReference) {
      for (PsiReference ref : getReferences((PsiMultiReference)reference)) {
        completeReference(ref, position, set, tailType, matcher, file, filter, variant);
      }
    }
    else{
      final Object[] completions = reference.getVariants();
      if(completions == null) return;

      for (Object completion : completions) {
        if (completion == null) {
          LOG.assertTrue(false, "Position=" + position + "\n;Reference=" + reference + "\n;variants=" + Arrays.toString(completions));
        }
        if (completion instanceof PsiElement) {
          final PsiElement psiElement = (PsiElement)completion;
          if (filter.isClassAcceptable(psiElement.getClass()) && filter.isAcceptable(psiElement, position)) {
            addLookupItem(set, tailType, completion, matcher, file, variant);
          }
        }
        else {
          if (completion instanceof LookupItem) {
            final Object o = ((LookupItem)completion).getObject();
            if (o instanceof PsiElement) {
              if (!filter.isClassAcceptable(o.getClass()) || !filter.isAcceptable(o, position)) continue;
            }
          }
          addLookupItem(set, tailType, completion, matcher, file, variant);
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

  protected void addKeywords(final Set<LookupItem> set, final PsiElement position, final PrefixMatcher matcher, final PsiFile file,
                                  final CompletionVariant variant, final Object comp, final TailType tailType) {
    if (comp instanceof String) {
      addKeyword(set, tailType, comp, matcher, file, variant);
    }
    else {
      final CompletionContext context = position.getUserData(CompletionContext.COMPLETION_CONTEXT_KEY);
      if (comp instanceof ContextGetter) {
        final Object[] elements = ((ContextGetter)comp).get(position, context);
        for (Object element : elements) {
          addLookupItem(set, tailType, element, matcher, file, variant);
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

  private void addKeyword(Set<LookupItem> set, final TailType tailType, final Object comp, final PrefixMatcher matcher,
                                final PsiFile file,
                                final CompletionVariant variant) {
    for (final LookupItem item : set) {
      if (item.getObject().toString().equals(comp.toString())) {
        return;
      }
    }
    addLookupItem(set, tailType, comp, matcher, file, variant);
  }
}
