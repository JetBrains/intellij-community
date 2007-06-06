package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.impl.Pattern;
import static com.intellij.patterns.impl.StandardPatterns.character;
import static com.intellij.patterns.impl.StandardPatterns.not;
import com.intellij.psi.*;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.util.ReflectionCache;
import org.jdom.Namespace;
import org.jetbrains.annotations.NonNls;
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
  public static final @NonNls Namespace COMPLETION_NS = Namespace.getNamespace("http://www.intellij.net/data/completion");
  private final Set<Class> myFinalScopes = new HashSet<Class>();
  private final List<CompletionVariant> myCompletionVariants = new ArrayList<CompletionVariant>();

  protected CompletionData(){ }

  protected final void declareFinalScope(Class scopeClass){
    myFinalScopes.add(scopeClass);
  }

  protected final boolean isScopeFinal(Class scopeClass){
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

  public void completeReference(PsiReference reference, Set<LookupItem> set, CompletionContext context, PsiElement position){
    final CompletionVariant[] variants = findVariants(position, context);
    boolean hasApplicableVariants = false;

    for (CompletionVariant variant : variants) {
      if (variant.hasReferenceFilter()) {
        variant.addReferenceCompletions(reference, position, set, context);
        hasApplicableVariants = true;
      }
    }

    if(!hasApplicableVariants){
      ourGenericVariant.addReferenceCompletions(reference, position, set, context);
    }
  }

  public void addKeywordVariants(Set<CompletionVariant> set, CompletionContext context, PsiElement position){
    CompletionVariant[] variants = findVariants(position, context);
    for (CompletionVariant variant : variants) {
      if (!set.contains(variant)) {
        set.add(variant);
      }
    }
  }

  public static void completeKeywordsBySet(Set<LookupItem> set, Set<CompletionVariant> variants, CompletionContext context, PsiElement position){
    for (final CompletionVariant variant : variants) {
      variant.addKeywords(context.file.getManager().getElementFactory(), set, context, position);
    }
  }

  public String findPrefix(PsiElement insertedElement, int offsetInFile){
    return findPrefixStatic(insertedElement, offsetInFile);
  }

  public CompletionVariant[] findVariants(final PsiElement position, final CompletionContext context){
    final List<CompletionVariant> variants = new ArrayList<CompletionVariant>();
    PsiElement scope = position;
    if(scope == null){
      scope = context.file;
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

  protected static final CompletionVariant ourGenericVariant = new CompletionVariant() {
    public void addReferenceCompletions(PsiReference reference, PsiElement position, Set<LookupItem> set, CompletionContext prefix) {
      addReferenceCompletions(reference, position, set, prefix, new CompletionVariantItem(TrueFilter.INSTANCE, TailType.NONE));
    }
  };

  @Nullable
  public static String getReferencePrefix(@NotNull PsiElement insertedElement, int offsetInFile) {
    int offsetInElement = offsetInFile - insertedElement.getTextRange().getStartOffset();
    //final PsiReference ref = insertedElement.findReferenceAt(offsetInElement);
    final PsiReference ref = insertedElement.getContainingFile().findReferenceAt(insertedElement.getTextRange().getStartOffset() + offsetInElement);
    if(ref instanceof PsiJavaCodeReferenceElement) {
      final PsiElement name = ((PsiJavaCodeReferenceElement)ref).getReferenceNameElement();
      if(name != null){
        offsetInElement = offsetInFile - name.getTextRange().getStartOffset();
        return name.getText().substring(0, offsetInElement);
      }
      return "";
    }
    else if(ref != null) {
      offsetInElement = offsetInFile - ref.getElement().getTextRange().getStartOffset();

      String result = ref.getElement().getText().substring(ref.getRangeInElement().getStartOffset(), offsetInElement);
      if(result.indexOf('(') > 0){
        result = result.substring(0, result.indexOf('('));
      }

      if (ref.getElement() instanceof PsiNameValuePair && StringUtil.startsWithChar(result,'{')) {
        result = result.substring(1); // PsiNameValuePair reference without name span all content of the element
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

  protected static String findPrefixDefault(final PsiElement insertedElement, final int offset, @NotNull final Pattern<Character, ?> trimStart) {
    final String substr = insertedElement.getText().substring(0, offset - insertedElement.getTextRange().getStartOffset()).trim();
    int i = 0;
    while (substr.length() > i && trimStart.accepts(substr.charAt(i))) i++;
    return substr.substring(i).trim();
  }

}
