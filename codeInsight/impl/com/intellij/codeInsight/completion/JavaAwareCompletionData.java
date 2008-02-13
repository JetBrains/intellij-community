/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.scope.CompletionElement;
import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import static com.intellij.patterns.StandardPatterns.character;
import static com.intellij.patterns.StandardPatterns.not;
import com.intellij.psi.*;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * @author peter
 */
public class JavaAwareCompletionData extends CompletionData{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.JavaAwareCompletionData");

  protected void completeReference(final PsiReference reference, final PsiElement position, final Set<LookupItem> set, final TailType tailType,
                                            final PrefixMatcher matcher,
                                            final PsiFile file,
                                            final ElementFilter filter,
                                            final CompletionVariant variant) {
    final JavaCompletionProcessor processor = new JavaCompletionProcessor(matcher, position, filter);

    if (reference instanceof PsiMultiReference) {
      int javaReferenceStart = -1;

      PsiReference[] references = getReferences((PsiMultiReference)reference);

      for (PsiReference ref : references) {
        if (ref instanceof PsiJavaReference) {
          int newStart = ref.getElement().getTextRange().getStartOffset() + ref.getRangeInElement().getStartOffset();
          if (javaReferenceStart == -1) {
            javaReferenceStart = newStart;
          } else {
            if (newStart == javaReferenceStart) continue;
          }
        }
        completeReference(ref, position, set, tailType, matcher, file, filter, variant);
      }
    }
    else if(reference instanceof PsiJavaReference){
      ((PsiJavaReference)reference).processVariants(processor);
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
          if (filter.isClassAcceptable(psiElement.getClass()) && filter.isAcceptable(new CandidateInfo(psiElement, PsiSubstitutor.EMPTY), position)) {
            processor.execute(psiElement, ResolveState.initial());
          }
        }
        else if (completion instanceof CandidateInfo) {
          final CandidateInfo info = (CandidateInfo)completion;
          if (info.isValidResult() && filter.isAcceptable(info, position)) {
            processor.execute(info.getElement(), ResolveState.initial());
          }
        }
        else {
          if (completion instanceof LookupItem) {
            final Object o = ((LookupItem)completion).getObject();
            if (o instanceof PsiElement && (!filter.isClassAcceptable(o.getClass()) ||
                                            !filter.isAcceptable(new CandidateInfo((PsiElement)o, PsiSubstitutor.EMPTY), position))) {
              continue;
            }
          }
          addLookupItem(set, tailType, completion, matcher, file, variant);
        }
      }
    }

    Collection<CompletionElement> results = processor.getResults();
    if (results != null) {
      for (CompletionElement element : results) {
        final LookupItem lookupItem = addLookupItem(set, tailType, element.getElement(), matcher, file, variant);
        if (lookupItem != null) {
          lookupItem.setAttribute(LookupItem.SUBSTITUTOR, element.getSubstitutor());
          if (element.getQualifier() != null){
            JavaCompletionUtil.setQualifierType(lookupItem, element.getQualifier());
          }
        }
      }
    }
  }

  protected PsiReference[] getReferences(final PsiMultiReference multiReference) {
    final PsiElement element = multiReference.getElement();
    if (element instanceof PsiNameValuePair) {
      final PsiNameValuePair psiNameValuePair = (PsiNameValuePair)element;
      if (psiNameValuePair.getName() == null) {
        return new PsiReference[]{psiNameValuePair.getReference()};
      }
    }

    return super.getReferences(multiReference);
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass"})
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

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass"})
  public static String findPrefixStatic(PsiElement insertedElement, int offsetInFile) {
    if(insertedElement == null) return "";

    final String prefix = getReferencePrefix(insertedElement, offsetInFile);
    if (prefix != null) return prefix;

    if (insertedElement instanceof PsiPlainText) return "";

    return findPrefixDefault(insertedElement, offsetInFile, not(character().javaIdentifierPart()));
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
    else if (file instanceof PsiJavaCodeReferenceCodeFragment) {
      PsiJavaCodeReferenceCodeFragment fragment = (PsiJavaCodeReferenceCodeFragment)file;
      if (!fragment.isClassesAccepted() && completion instanceof PsiPackage) {
        ret.setTailType(TailType.NONE);
      }
    }

    final Map<Object, Serializable> itemProperties = variant.getItemProperties();
    for (final Object key : itemProperties.keySet()) {
      if (key == LookupItem.FORCE_SHOW_FQN_ATTR && ret.getObject() instanceof PsiClass) {
        setShowFQN(ret);
      }
      else {
        if (completion instanceof PsiMember && key == LookupItem.FORCE_QUALIFY) {
          final PsiMember completionElement = (PsiMember)completion;
          final PsiClass containingClass = completionElement.getContainingClass();
          if (containingClass != null) {
            final String className = containingClass.getName();
            ret.setLookupString(className + "." + ret.getLookupString());
            ret.setAttribute(key, itemProperties.get(key));
          }
        }
        ret.setAttribute(key, itemProperties.get(key));
      }
    }
    if(matcher.prefixMatches(ret)){
      set.add(ret);
      return ret;
    }

    return null;
  }


  protected void addKeywords(final Set<LookupItem> set, final PsiElement position, final PrefixMatcher matcher, final PsiFile file,
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
          addLookupItem(set, tailType, element, matcher, file, variant);
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

  private void addKeyword(PsiElementFactory factory, Set<LookupItem> set, final TailType tailType, final Object comp, final PrefixMatcher matcher,
                                final PsiFile file,
                                final CompletionVariant variant) {
    for (final LookupItem item : set) {
      if (item.getObject().toString().equals(comp.toString())) {
        return;
      }
    }
    if(factory == null){
      addLookupItem(set, tailType, comp, matcher, file, variant);
    }
    else{
      try{
        final PsiKeyword keyword = factory.createKeyword((String)comp);
        addLookupItem(set, tailType, keyword, matcher, file, variant);
      }
      catch(IncorrectOperationException e){
        addLookupItem(set, tailType, comp, matcher, file, variant);
      }
    }
  }

  public static LookupItem<PsiClass> setShowFQN(final LookupItem<PsiClass> ret) {
    @NonNls String packageName = ret.getObject().getQualifiedName();
    if (packageName != null && packageName.lastIndexOf('.') > 0) {
      packageName = packageName.substring(0, packageName.lastIndexOf('.'));
    }
    else {
      packageName = "";
    }
    if (packageName.length() == 0) {
      packageName = "default package";
    }

    ret.setAttribute(LookupItem.TAIL_TEXT_ATTR, " (" + packageName + ")");
    ret.setAttribute(LookupItem.TAIL_TEXT_SMALL_ATTR, "");
    return ret;
  }
}
