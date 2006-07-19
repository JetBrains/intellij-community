package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemPreferencePolicy;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.impl.source.codeStyle.CodeStyleManagerEx;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import org.jdom.Namespace;
import org.jetbrains.annotations.NonNls;

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
      if (myFinalScope.isAssignableFrom(scopeClass)) {
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

  public LookupItemPreferencePolicy completeLocalVariableName(Set<LookupItem> set, CompletionContext context, PsiVariable var){
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.variable.name");
    final CodeStyleManagerEx codeStyleManager = (CodeStyleManagerEx) CodeStyleManager.getInstance(context.project);
    final VariableKind variableKind = codeStyleManager.getVariableKind(var);
    SuggestedNameInfo suggestedNameInfo = codeStyleManager.suggestVariableName(variableKind, null, null, var.getType());
    final String[] suggestedNames = suggestedNameInfo.names;
    LookupItemUtil.addLookupItems(set, suggestedNames, context.getPrefix());

    if (set.isEmpty()) {
      suggestedNameInfo = new SuggestedNameInfo(CompletionUtil.getOverlappedNameVersions(context.getPrefix(), suggestedNames, "")) {
        public void nameChoosen(String name) {
        }
      };

      LookupItemUtil.addLookupItems(set, suggestedNameInfo.names, context.getPrefix());
    }
    PsiElement parent = PsiTreeUtil.getParentOfType(var, PsiCodeBlock.class);
    if(parent == null) parent = PsiTreeUtil.getParentOfType(var, PsiMethod.class);
    LookupItemUtil.addLookupItems(set, CompletionUtil.getUnresolvedReferences(parent, false), context.getPrefix());
    LookupItemUtil.addLookupItems(set, StatisticsManager.getInstance().getNameSuggestions(var.getType(), StatisticsManager.getContext(var),
                                                                                          context.getPrefix()), context.getPrefix());

    return new NamePreferencePolicy(suggestedNameInfo);
  }

  public LookupItemPreferencePolicy completeFieldName(Set<LookupItem> set, CompletionContext context, PsiVariable var){
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.variable.name");

    CodeStyleManagerEx codeStyleManager = (CodeStyleManagerEx) CodeStyleManager.getInstance(context.project);
    final VariableKind variableKind = CodeStyleManager.getInstance(var.getProject()).getVariableKind(var);
    final String prefix = context.getPrefix();

    if (var.getType() == PsiType.VOID ||
        prefix.startsWith(CompletionUtil.IS_PREFIX) ||
        prefix.startsWith(CompletionUtil.GET_PREFIX) ||
        prefix.startsWith(CompletionUtil.SET_PREFIX)) {
      return CompletionUtil.completeVariableNameForRefactoring(var.getProject(), set, prefix, var.getType(), variableKind);
    }

    SuggestedNameInfo suggestedNameInfo = codeStyleManager.suggestVariableName(variableKind, null, null, var.getType());
    final String[] suggestedNames = suggestedNameInfo.names;
    LookupItemUtil.addLookupItems(set, suggestedNames, prefix);

    if (set.isEmpty()) {
      // use suggested names as suffixes
      final String requiredSuffix = codeStyleManager.getSuffixByVariableKind(variableKind);
      if(variableKind != VariableKind.STATIC_FINAL_FIELD){
        for (int i = 0; i < suggestedNames.length; i++)
          suggestedNames[i] = codeStyleManager.variableNameToPropertyName(suggestedNames[i], variableKind);
      }


        suggestedNameInfo = new SuggestedNameInfo(CompletionUtil.getOverlappedNameVersions(prefix, suggestedNames, requiredSuffix)) {
        public void nameChoosen(String name) {
        }
      };

      LookupItemUtil.addLookupItems(set, suggestedNameInfo.names, prefix);
    }

    LookupItemUtil.addLookupItems(set, StatisticsManager.getInstance().getNameSuggestions(var.getType(), StatisticsManager.getContext(var), prefix), prefix);
    LookupItemUtil.addLookupItems(set, CompletionUtil.getUnresolvedReferences(var.getParent(), false), context.getPrefix());

    return new NamePreferencePolicy(suggestedNameInfo);
  }

  public LookupItemPreferencePolicy completeMethodName(Set<LookupItem> set, CompletionContext context, PsiElement element){
    if(element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      if (method.isConstructor()) {
        final PsiClass containingClass = method.getContainingClass();
        LookupItemUtil.addLookupItem(set, containingClass.getName(), context.getPrefix());
        return null;
      }
    }

    LookupItemUtil.addLookupItems(set, CompletionUtil.getUnresolvedReferences(element.getParent(), true), context.getPrefix());
    if(!((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.PRIVATE)){
      LookupItemUtil.addLookupItems(set, CompletionUtil.getOverides((PsiClass)element.getParent(), PsiUtil.getTypeByPsiElement(element)),
                                    context.getPrefix());
      LookupItemUtil.addLookupItems(set, CompletionUtil.getImplements((PsiClass)element.getParent(), PsiUtil.getTypeByPsiElement(element)),
                                    context.getPrefix());
    }
    LookupItemUtil.addLookupItems(set, CompletionUtil.getPropertiesHandlersNames(
      (PsiClass)element.getParent(),
      ((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC),
      PsiUtil.getTypeByPsiElement(element), element), context.getPrefix());
    return null;
  }

  public LookupItemPreferencePolicy completeClassName(Set<LookupItem> set, CompletionContext context, PsiClass aClass){
    return null;
  }

  public String findPrefix(PsiElement insertedElement, int offset){
    return findPrefixStatic(insertedElement, offset);
  }

  protected CompletionVariant[] findVariants(final PsiElement position, final CompletionContext context){
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

  protected static final CompletionVariant ourGenericVariant = new CompletionVariant(){
    public void addReferenceCompletions(PsiReference reference, PsiElement position, Set<LookupItem> set, CompletionContext prefix){
      addReferenceCompletions(reference, position, set, prefix, new CompletionVariantItem(TrueFilter.INSTANCE, TailType.NONE));
    }
  };

  public static String findPrefixStatic(PsiElement insertedElement, int offset) {
    if(insertedElement == null) return "";
    int offsetInElement = offset - insertedElement.getTextRange().getStartOffset();
    //final PsiReference ref = insertedElement.findReferenceAt(offsetInElement);
    final PsiReference ref = insertedElement.getContainingFile().findReferenceAt(insertedElement.getTextRange().getStartOffset() + offsetInElement);

    // TODO: need to add more separators here. Such as #, $ etc... Think on centralizing their location.
    final String text = insertedElement.getText();
    if(ref == null && (StringUtil.endsWithChar(text, '#') ||
                       StringUtil.endsWithChar(text, '.') && !(insertedElement instanceof Property))){
      return "";
    }

    if(ref instanceof PsiJavaCodeReferenceElement) {
      final PsiElement name = ((PsiJavaCodeReferenceElement)ref).getReferenceNameElement();
      if(name != null){
        offsetInElement = offset - name.getTextRange().getStartOffset();
        return name.getText().substring(0, offsetInElement);
      }
      return "";
    }
    else if(ref != null) {
      offsetInElement = offset - ref.getElement().getTextRange().getStartOffset();

      String result = ref.getElement().getText().substring(ref.getRangeInElement().getStartOffset(), offsetInElement);
      if(result.indexOf('(') > 0){
        result = result.substring(0, result.indexOf('('));
      }
      
      if (ref.getElement() instanceof PsiNameValuePair && StringUtil.startsWithChar(result,'{')) {
        result = result.substring(1); // PsiNameValuePair reference without name span all content of the element
      }
      return result;
    }

    if (insertedElement instanceof PsiIdentifier || insertedElement instanceof PsiKeyword
        || PsiKeyword.NULL.equals(text)
        || PsiKeyword.TRUE.equals(text)
        || PsiKeyword.FALSE.equals(text)
        || (insertedElement instanceof XmlToken
            && (((XmlToken)insertedElement).getTokenType() == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN ||
                ((XmlToken)insertedElement).getTokenType() == XmlTokenType.XML_DATA_CHARACTERS ||
                ((XmlToken)insertedElement).getTokenType() == XmlTokenType.XML_NAME))
        ){
      return text.substring(0, offsetInElement).trim();
    }

    if (insertedElement instanceof PsiDocToken) {
      final PsiDocToken token = (PsiDocToken)insertedElement;
      if (token.getTokenType() == JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
        return text.substring(0, offsetInElement).trim();
      }
      else if (token.getTokenType() == JavaDocTokenType.DOC_TAG_NAME) {
        return text.substring(1, offsetInElement).trim();
      }
    }

    return "";
  }
}
