/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Computable;
import static com.intellij.patterns.PlatformPatterns.psiElement;
import com.intellij.psi.*;
import com.intellij.psi.filters.FilterUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.statistics.JavaStatisticsManager;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author peter
 */
public class JavaSmartCompletionContributor extends CompletionContributor{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.JavaSmartCompletionContributor");
  private static final JavaSmartCompletionData SMART_DATA = new JavaSmartCompletionData();

  public void registerCompletionProviders(final CompletionRegistrar registrar) {

    registrar.extend(CompletionType.SMART, psiElement()).withId(JavaCompletionContributor.JAVA_LEGACY).withProvider(new CompletionProvider<LookupElement, CompletionParameters>() {
      public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext matchingContext, @NotNull final CompletionResultSet<LookupElement> result) {
        final Set<LookupItem> set = new LinkedHashSet<LookupItem>();
        final PsiElement identifierCopy = parameters.getPosition();
        final Set<CompletionVariant> keywordVariants = new HashSet<CompletionVariant>();
        final CompletionContext context = parameters.getPosition().getUserData(CompletionContext.COMPLETION_CONTEXT_KEY);
        context.setPrefix(identifierCopy, context.getStartOffset(), SMART_DATA);

        PsiFile file = parameters.getOriginalFile();
        final PsiReference ref = identifierCopy.getContainingFile().findReferenceAt(identifierCopy.getTextRange().getStartOffset());
        if (ref != null) {
          SMART_DATA.completeReference(ref, set, identifierCopy, result.getPrefixMatcher(), file, context.getStartOffset());
        }
        SMART_DATA.addKeywordVariants(keywordVariants, identifierCopy, file);
        SMART_DATA.completeKeywordsBySet(set, keywordVariants, identifierCopy, result.getPrefixMatcher(), file);
        JavaCompletionUtil.highlightMembersOfContainer(set);

        final PsiExpression expr = PsiTreeUtil.getContextOfType(parameters.getPosition(), PsiExpression.class, true);
        final ExpectedTypeInfo[] expectedInfos =
            expr != null ? ExpectedTypesProvider.getInstance(context.project).getExpectedTypes(expr, true) : null;

        final Set<PsiClass> classes =
            expectedInfos != null && expectedInfos.length > 0 ? getFirstClasses(expectedInfos) : Collections.<PsiClass>emptySet();

        final DefaultInsertHandler defaultHandler = new DefaultInsertHandler();
        for (final LookupItem item : set) {
          final Object o = item.getObject();
          if (classes.contains(o) && !shouldPrefer((PsiClass)o)) {
            item.setAttribute(LookupItem.DONT_PREFER, "");
          }
          InsertHandler oldHandler = item.getInsertHandler();
          if (oldHandler == null) {
            oldHandler = defaultHandler;
          }

          item.setInsertHandler(new AnalyzingInsertHandler(o, expectedInfos, oldHandler));

        }

        result.addAllElements(set);
      }
    });
  }

  public static boolean shouldInsertExplicitTypeParams(final PsiMethod method) {
    final PsiTypeParameter[] typeParameters = method.getTypeParameters();
    if (typeParameters.length == 0) return false;

    final LocalSearchScope scope = new LocalSearchScope(method.getParameterList());
    for (final PsiTypeParameter typeParameter : typeParameters) {
      if (ReferencesSearch.search(typeParameter, scope).findFirst() != null) {
        return false;
      }
    }
    return true;
  }


  private static void analyzeItem(final CompletionContext context, final LookupItem item, final Object completion, PsiElement position, ExpectedTypeInfo[] expectedTypes) {
    final PsiFile file = position.getContainingFile();

    if (completion instanceof PsiMethod && expectedTypes != null) {
      final PsiMethod method = (PsiMethod)completion;
      for (final ExpectedTypeInfo type : expectedTypes) {
        if (shouldInsertExplicitTypeParams(method)) {
          if (type.isInsertExplicitTypeParams()) {
            item.setAttribute(LookupItem.INSERT_TYPE_PARAMS, "");
          }
          PsiResolveHelper helper = JavaPsiFacade.getInstance(method.getProject()).getResolveHelper();
          final PsiTypeParameter[] typeParameters = method.getTypeParameters();
          PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
          for (PsiTypeParameter typeParameter : typeParameters) {
            PsiType substitution = helper.getSubstitutionForTypeParameter(typeParameter, method.getReturnType(), type.getType(),
                                                                                false, PsiUtil.getLanguageLevel(file));
            if (substitution == PsiType.NULL) {
              substitution = TypeConversionUtil.typeParameterErasure(typeParameter);
            }

            substitutor = substitutor.put(typeParameter, substitution);
          }

          item.setAttribute(LookupItem.SUBSTITUTOR, substitutor);
          break;
        }
      }
    }

    if (position instanceof PsiJavaToken && ">".equals(position.getText())) {
      // In case of generics class
      position = position.getParent().getParent();
    }

    final int startOffset = position.getTextRange().getStartOffset();
    PsiReference ref = position.getContainingFile().findReferenceAt(startOffset);

    final Object selectedObject = item.getObject();

    setTailType(position, item, expectedTypes);

    if (ref!=null && selectedObject instanceof PsiNamedElement) {
      if (selectedObject instanceof PsiMethod || selectedObject instanceof PsiField) {
        final PsiMember member = (PsiMember)selectedObject;
        if (item.getAttribute(LookupItem.FORCE_QUALIFY) != null
            && member.hasModifierProperty(PsiModifier.STATIC)
            && PsiUtil.isAccessible(member, position, null)) {
          final PsiClass containingClass = member.getContainingClass();
          if (containingClass != null) {
            final String refText = ref.getElement().getText();
            final Document document = context.editor.getDocument();
            document.insertString(context.editor.getCaretModel().getOffset(), " ");
            final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(context.project);
            psiDocumentManager.commitDocument(document);
            LOG.assertTrue(!psiDocumentManager.isUncommited(psiDocumentManager.getDocument(file)));
            final PsiReference finalRef = file.findReferenceAt(startOffset);
            if (finalRef == null) {
              final String text = document.getText();
              LOG.error("startOffset=" + startOffset + "\n" +
                        "caretOffset=" + context.editor.getCaretModel().getOffset() + "\n" +
                        "ref.getText()=" + refText + "\n" +
                        "file=" + file + "\n" +
                        "documentPart=" + text.substring(Math.max(startOffset - 100, 0), Math.min(startOffset + 100, text.length())));
            }
            final String name = member.getName();
            final PsiElement psiElement = file.getManager().performActionWithFormatterDisabled(new Computable<PsiElement>() {
              public PsiElement compute() {
                try {
                  return finalRef.bindToElement(containingClass);
                }
                catch (IncorrectOperationException e) {
                  LOG.error(e);
                }
                return null;
              }
            });
            final PsiElement element = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(psiElement);
            int whereToInsert = element.getTextRange().getEndOffset();
            final String insertString = "." + name;
            document.insertString(whereToInsert, insertString);
            final int endOffset = whereToInsert + insertString.length();
            context.editor.getCaretModel().moveToOffset(endOffset);
            context.getOffsetMap().addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, endOffset);
            context.setStartOffset(endOffset);
            context.setSelectionEndOffset(endOffset);
            context.setPrefix(name);
            item.setLookupString(name);
            document.deleteString(endOffset, endOffset + 1);
          }
        }
      }
      else if (completion instanceof PsiClass || completion instanceof PsiClassType) {
        final PsiClass psiClass;

        if (completion instanceof PsiClass) {
          psiClass = (PsiClass)completion;
        }
        else {
          psiClass = ((PsiClassType)completion).resolve();
        }

        PsiElement prevElement = FilterUtil.searchNonSpaceNonCommentBack(position);
        boolean overwriteTypeCast = position instanceof PsiParenthesizedExpression ||
                                    prevElement != null
                                    && prevElement.getText().equals("(")
                                    && prevElement.getParent() instanceof PsiTypeCastExpression;
        if (overwriteTypeCast) {
          item.setAttribute(LookupItem.OVERWRITE_ON_AUTOCOMPLETE_ATTR, "");
        }
        else if (prevElement != null && prevElement.getParent() instanceof PsiNewExpression) {
          boolean flag = true;
          for (ExpectedTypeInfo myType : expectedTypes) {
            PsiType type = myType.getType();
            if (type instanceof PsiArrayType) {
              flag = false;
              type = ((PsiArrayType)type).getComponentType();
            }
            if (!(type instanceof PsiClassType)) continue;
            final PsiClass typeClass = ((PsiClassType)type).resolve();

            if (InheritanceUtil.isInheritorOrSelf(psiClass, typeClass, true)) {
              StatisticsManager.getInstance().incUseCount(JavaStatisticsManager.createInfo(type, psiClass));
            }
          }
          if (flag) {
            item.setAttribute(LookupItem.NEW_OBJECT_ATTR, "");
          }
        }
      }
    }

  }


  public static THashSet<PsiClass> getFirstClasses(final ExpectedTypeInfo[] expectedInfos) {
    final THashSet<PsiClass> set = new THashSet<PsiClass>();
    for (final ExpectedTypeInfo info : expectedInfos) {
      addFirstPsiType(set, info.getType());
      addFirstPsiType(set, info.getDefaultType());
    }
    return set;
  }

  private static void addFirstPsiType(final THashSet<PsiClass> set, final PsiType type) {
    if (type instanceof PsiClassType) {
      final PsiClass psiClass = ((PsiClassType)type).resolve();
      if (psiClass != null) {
        set.add(psiClass);
      }
    }
  }

  private static boolean shouldPrefer(final PsiClass psiClass) {
    int toImplement = OverrideImplementUtil.getMethodSignaturesToImplement(psiClass).size();
    if (toImplement > 2) return false;

    for (final PsiMethod method : psiClass.getMethods()) {
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        toImplement++;
        if (toImplement > 2) return false;
      }
    }

    if (toImplement > 0) return true;

    if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) return false;
    if (CommonClassNames.JAVA_LANG_STRING.equals(psiClass.getQualifiedName())) return false;
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(psiClass.getQualifiedName())) return false;
    return true;
  }

  private static void setTailType(PsiElement position, LookupItem item, ExpectedTypeInfo[] expectedTypeInfos) {
    final PsiExpression enclosing = PsiTreeUtil.getContextOfType(position, PsiExpression.class, true);
    final PsiReferenceParameterList contextOfType = PsiTreeUtil.getContextOfType(position, PsiReferenceParameterList.class, false);
    if(contextOfType != null && item.getObject() instanceof PsiClass){
      final PsiTypeElement typeElement = PsiTreeUtil.getContextOfType(position, PsiTypeElement.class, false);
      final PsiTypeElement[] elements = contextOfType.getTypeParameterElements();
      int index = 0;
      while(index < elements.length) {
        if(typeElement == elements[index++]) break;
      }
      if(index > 0){
        final PsiClass psiClass = (PsiClass)((PsiReference)contextOfType.getParent()).resolve();

        if(psiClass != null && psiClass.getTypeParameters().length > index)
          item.setTailType(TailType.COMMA);
        else
          item.setTailType(TailType.createSimpleTailType('>'));
        return;
      }
    }

    if(item.getObject() instanceof PsiClass
      && item.getAttribute(LookupItem.BRACKETS_COUNT_ATTR) == null
      && enclosing instanceof PsiNewExpression
      && !(position instanceof PsiParenthesizedExpression)
       ){
      final PsiAnonymousClass anonymousClass = PsiTreeUtil.getParentOfType(position, PsiAnonymousClass.class);
      if (anonymousClass == null || anonymousClass.getParent() != enclosing) {

        final PsiClass psiClass = (PsiClass)item.getObject();

        item.setAttribute(LookupItem.NEW_OBJECT_ATTR, "");
        if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT) || psiClass.isInterface()) {
          item.setAttribute(LookupItem.GENERATE_ANONYMOUS_BODY_ATTR, "");
          FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.smarttype.anonymous");
        }
        else {
          FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.smarttype.afternew");
        }
      }
    }
    if (item.getTailType() == TailType.UNKNOWN || item.getTailType() == TailType.NONE) {
      if (enclosing != null && item.getObject() instanceof PsiElement) {
        final ExpectedTypeInfo[] infos = expectedTypeInfos;
        if (infos != null) {
          final PsiType type;
          if (item.getAttribute(LookupItem.TYPE) != null) {
            type = (PsiType)item.getAttribute(LookupItem.TYPE);
          }
          else {
            final PsiSubstitutor substitutor = (PsiSubstitutor)item.getAttribute(LookupItem.SUBSTITUTOR);
            if(substitutor != null)
              type = substitutor.substitute(FilterUtil.getTypeByElement((PsiElement)item.getObject(), position));
            else
              type = FilterUtil.getTypeByElement((PsiElement)item.getObject(), position);
          }
          TailType cached = item.getTailType();
          int cachedPrior = 0;
          if (type != null && type.isValid()) {
            for (ExpectedTypeInfo info : infos) {
              final PsiType infoType = info.getType();
              if (infoType.equals(type) && cachedPrior < 2) {
                cachedPrior = 2;
                cached = info.getTailType();
              }
              else if (cachedPrior == 2 && cached != info.getTailType()) {
                cachedPrior = 3;
                cached = item.getTailType();
              }
              else if (((infoType.isAssignableFrom(type) && info.getKind() == ExpectedTypeInfo.TYPE_OR_SUBTYPE)
                        || (type.isAssignableFrom(infoType) && info.getKind() == ExpectedTypeInfo.TYPE_OR_SUPERTYPE))
                       && cachedPrior < 1) {
                cachedPrior = 1;
                cached = info.getTailType();
              }
              else if (cachedPrior == 1 && cached != info.getTailType()) {
                cached = item.getTailType();
              }
            }
          }
          else {
            if (infos.length == 1) {
              cached = infos[0].getTailType();
            }
          }
          item.setTailType(cached);
        }
      }
      if (item.getTailType() == TailType.UNKNOWN) {
        item.setTailType(TailType.NONE);
      }
    }
  }

  public void beforeCompletion(@NotNull final CompletionInitializationContext context) {
    if (context.getCompletionType() == CompletionType.SMART) {
      PsiElement lastElement = context.getFile().findElementAt(context.getStartOffset() - 1);
      if (lastElement != null && lastElement.getText().equals("(")) {
        final PsiElement parent = lastElement.getParent();
        if (parent instanceof PsiTypeCastExpression) {
          context.setDummyIdentifier("");
        }
        else if (parent instanceof PsiParenthesizedExpression) {
          context.setDummyIdentifier("xxx)yyy "); // to handle type cast
        }
      }
    }
  }

  private static class AnalyzingInsertHandler implements InsertHandler {
    private final Object myO;
    private final ExpectedTypeInfo[] myExpectedInfos;
    private final InsertHandler myHandler;

    public AnalyzingInsertHandler(final Object o, final ExpectedTypeInfo[] expectedInfos, final InsertHandler handler) {
      myO = o;
      myExpectedInfos = expectedInfos;
      myHandler = handler;
    }

    public void handleInsert(final CompletionContext context, final int startOffset, final LookupData data, final LookupItem item,
                             final boolean signatureSelected,
                             final char completionChar) {
      analyzeItem(context, item, myO, context.file.findElementAt(context.getStartOffset() + item.getLookupString().length() - 1),
                  myExpectedInfos);
      myHandler.handleInsert(context, startOffset, data, item, signatureSelected, completionChar);
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof AnalyzingInsertHandler)) return false;

      final AnalyzingInsertHandler that = (AnalyzingInsertHandler)o;

      if (!myHandler.equals(that.myHandler)) return false;

      return true;
    }

    public int hashCode() {
      return myHandler.hashCode();
    }
  }
}