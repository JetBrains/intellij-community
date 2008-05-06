/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Computable;
import static com.intellij.patterns.PlatformPatterns.psiElement;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.patterns.ElementPattern;
import static com.intellij.patterns.PsiJavaPatterns.psiClass;
import static com.intellij.patterns.StandardPatterns.or;
import static com.intellij.patterns.StandardPatterns.string;
import com.intellij.psi.*;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.filters.FilterPositionUtil;
import com.intellij.psi.filters.FilterUtil;
import com.intellij.psi.filters.GeneratorFilter;
import com.intellij.psi.filters.getters.ExpectedTypesGetter;
import com.intellij.psi.filters.getters.InheritorsGetter;
import com.intellij.psi.filters.getters.ThrowsListGetter;
import com.intellij.psi.filters.types.AssignableGroupFilter;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.statistics.JavaStatisticsManager;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import com.intellij.util.Processor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author peter
 */
public class JavaSmartCompletionContributor extends CompletionContributor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.JavaSmartCompletionContributor");
  private static final JavaSmartCompletionData SMART_DATA = new JavaSmartCompletionData();

  public boolean fillCompletionVariants(final CompletionParameters parameters, final CompletionResultSet result) {
    return super.fillCompletionVariants(parameters, result);
  }

  public JavaSmartCompletionContributor() {
    final DefaultInsertHandler defaultHandler = new DefaultInsertHandler();
    final GeneratorFilter filter = new GeneratorFilter(AssignableGroupFilter.class, new ExpectedTypesGetter());
    extend(CompletionType.SMART, PsiJavaPatterns.psiElement().afterLeaf(
        PsiJavaPatterns.psiElement().withText(".").afterLeaf(
            PsiJavaPatterns.psiElement().withParent(
                PsiJavaPatterns.psiElement().referencing(psiClass())))), new CompletionProvider<CompletionParameters>() {
      public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        try {
          final PsiElement element = parameters.getPosition();
          addKeyword(result, element, filter, PsiKeyword.CLASS, parameters);
          addKeyword(result, element, filter, PsiKeyword.THIS, parameters);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }

      private void addKeyword(final CompletionResultSet result, final PsiElement element, final GeneratorFilter filter, final String s, CompletionParameters parameters)
          throws IncorrectOperationException {
        final PsiKeyword keyword = JavaPsiFacade.getInstance(element.getProject()).getElementFactory().createKeyword(s);
        if (filter.isAcceptable(keyword, element)) {
          result.addElement(LookupItemUtil.objectToLookupItem(keyword).setAutoCompletionPolicy(AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE).setInsertHandler(new AnalyzingInsertHandler(parameters, defaultHandler)));
        }
      }

    });

    extend(CompletionType.SMART, PsiJavaPatterns.psiElement().inside(
        PsiJavaPatterns.psiElement(PsiDocTag.class).withName(
            string().oneOf(PsiKeyword.THROWS, JavaSmartCompletionData.EXCEPTION_TAG))), new CompletionProvider<CompletionParameters>() {
      public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final PsiElement element = parameters.getPosition();
        final CompletionContext completionContext = element.getUserData(CompletionContext.COMPLETION_CONTEXT_KEY);
        for (final Object object : new ThrowsListGetter().get(element, completionContext)) {
          result.addElement(LookupItemUtil.objectToLookupItem(object).setInsertHandler(new AnalyzingInsertHandler(parameters, defaultHandler)).setTailType(TailType.SPACE));
        }
      }
    });

    extend(CompletionType.SMART, PsiJavaPatterns.psiElement().withSuperParent(2,
                                                                              or(
                                                                                  PsiJavaPatterns.psiElement(PsiConditionalExpression.class).withParent(PsiReturnStatement.class),
                                                                                  PsiJavaPatterns.psiElement(PsiReturnStatement.class))), new CompletionProvider<CompletionParameters>() {
      public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final ContextGetter chooser = new JavaSmartCompletionData.EmptyCollectionGetter();
        final PsiElement element = parameters.getPosition();
        final CompletionContext completionContext = element.getUserData(CompletionContext.COMPLETION_CONTEXT_KEY);
        for (final Object object : chooser.get(element, completionContext)) {
          result.addElement(JavaAwareCompletionData.qualify(
              LookupItemUtil.objectToLookupItem(object).setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE).setTailType(
                  TailType.NONE)).setInsertHandler(new AnalyzingInsertHandler(parameters, defaultHandler)));
        }
      }
    });

    extend(CompletionType.SMART, or(
        PsiJavaPatterns.psiElement().withParent(PsiNameValuePair.class),
        PsiJavaPatterns.psiElement().withSuperParent(2, PsiNameValuePair.class)), new CompletionProvider<CompletionParameters>() {
      public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final ContextGetter chooser = new ContextGetter() {
          public Object[] get(PsiElement element, CompletionContext context) {
            final Set<Object> result = new THashSet<Object>();
            final ExpectedTypesGetter expectedTypesGetter = new ExpectedTypesGetter();
            final Object[] objects = expectedTypesGetter.get(element, context);

            if (objects != null) {
              for (final Object object : objects) {
                if (object instanceof PsiType) {
                  PsiType type = (PsiType)object;
                  if (type instanceof PsiArrayType) {
                    type = ((PsiArrayType)type).getComponentType();
                  }

                  if (type instanceof PsiClassType) {
                    final PsiClass psiClass = ((PsiClassType)type).resolve();
                    if (psiClass != null && psiClass.isAnnotationType()) {
                      result.add(psiClass);
                    }
                  }
                }
              }
            }
            return result.toArray(new Object[result.size()]);
          }
        };
        final PsiElement element = parameters.getPosition();
        final CompletionContext completionContext = element.getUserData(CompletionContext.COMPLETION_CONTEXT_KEY);
        final ElementPattern<? extends PsiElement> leftNeighbor = PsiJavaPatterns.psiElement().afterLeaf(PsiJavaPatterns.psiElement().withText("."));
        final boolean needQualify = leftNeighbor.accepts(element);

        for (final Object object : chooser.get(element, completionContext)) {
          final LookupItem item = LookupItemUtil.objectToLookupItem(object).setTailType(TailType.NONE);
          if (needQualify) JavaAwareCompletionData.qualify(item);
          result.addElement(item.setInsertHandler(new AnalyzingInsertHandler(parameters, defaultHandler)));
        }
      }
    });

    extend(CompletionType.SMART, psiElement(), new CompletionProvider<CompletionParameters>(true, false) {
      public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext matchingContext, @NotNull final CompletionResultSet result) {
        final Set<LookupItem> set = new LinkedHashSet<LookupItem>();
        final PsiElement identifierCopy = parameters.getPosition();
        final Set<CompletionVariant> keywordVariants = new HashSet<CompletionVariant>();
        result.setPrefixMatcher(SMART_DATA.findPrefix(identifierCopy, parameters.getOffset()));

        final PsiFile file = parameters.getOriginalFile();

        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            if (JavaSmartCompletionData.AFTER_NEW.accepts(identifierCopy) && !JavaSmartCompletionData.AFTER_THROW_NEW.accepts(identifierCopy)) {
              final PsiExpression expr = PsiTreeUtil.getContextOfType(parameters.getPosition(), PsiExpression.class, true);
              if (expr != null) {
                final ExpectedTypeInfo[] expectedInfos = ExpectedTypesProvider.getInstance(file.getProject()).getExpectedTypes(expr, true);
                for (final ExpectedTypeInfo info : expectedInfos) {
                  final PsiType type = info.getType();
                  if (type instanceof PsiClassType) {
                    addExpectedType(result, defaultHandler, expectedInfos, type);

                    final PsiType defaultType = info.getDefaultType();
                    if (!defaultType.equals(type)) {
                      addExpectedType(result, defaultHandler, expectedInfos, defaultType);
                    }

                    final PsiClassType.ClassResolveResult baseResult = JavaCompletionUtil.originalize((PsiClassType) type).resolveGenerics();
                    final PsiClass baseClass = baseResult.getElement();
                    final PsiSubstitutor baseSubstitutor = baseResult.getSubstitutor();


                    final THashSet<PsiType> statVariants = new THashSet<PsiType>();
                    final Processor<PsiClass> processor = CodeInsightUtil.createInheritorsProcessor(parameters.getPosition(),
                                                                                                    (PsiClassType)type, 0, false,
                                                                                                    statVariants, baseClass,
                                                                                                    baseSubstitutor);
                    final StatisticsInfo[] statisticsInfos =
                        StatisticsManager.getInstance().getAllValues(JavaStatisticsManager.getMemberUseKey1(type));
                    for (final StatisticsInfo statisticsInfo : statisticsInfos) {
                      final String value = statisticsInfo.getValue();
                      if (value.startsWith(JavaStatisticsManager.CLASS_PREFIX)) {
                        final String qname = value.substring(JavaStatisticsManager.CLASS_PREFIX.length());
                        for (final PsiClass psiClass : JavaPsiFacade.getInstance(file.getProject()).findClasses(qname, file.getResolveScope())) {
                          if (!PsiTreeUtil.isAncestor(file, psiClass, true) && !processor.process(psiClass)) break;
                        }
                      }
                    }

                    for (final PsiType variant : statVariants) {
                      addExpectedType(result, defaultHandler, expectedInfos, variant);
                    }
                  }

                }
              }
            }
          }
        });

        final PsiReference ref = ApplicationManager.getApplication().runReadAction(new Computable<PsiReference>() {
          public PsiReference compute() {
            return identifierCopy.getContainingFile().findReferenceAt(identifierCopy.getTextRange().getStartOffset());
          }
        });
        if (ref != null) {
          SMART_DATA.completeReference(ref, set, identifierCopy, result.getPrefixMatcher(), file, parameters.getOffset());
        }
        SMART_DATA.addKeywordVariants(keywordVariants, identifierCopy, file);
        SMART_DATA.completeKeywordsBySet(set, keywordVariants, identifierCopy, result.getPrefixMatcher(), file);
        JavaCompletionUtil.highlightMembersOfContainer(set);

        for (final LookupItem item : set) {
          final Object o = item.getObject();
          InsertHandler oldHandler = item.getInsertHandler();
          if (oldHandler == null) {
            oldHandler = defaultHandler;
          }
          item.setInsertHandler(new AnalyzingInsertHandler(parameters, oldHandler));
          result.addElement(item);
        }
      }
    });
  }

  private static void addExpectedType(final CompletionResultSet result, final DefaultInsertHandler defaultHandler,
                                      final ExpectedTypeInfo[] expectedInfos, final PsiType type) {
    if (!InheritorsGetter.hasAccessibleConstructor(type)) return;

    final PsiClass psiClass = PsiUtil.resolveClassInType(type);
    if (psiClass == null) return;


    final LookupItem item = LookupItemUtil.objectToLookupItem(JavaCompletionUtil.eliminateWildcards(type));
    item.setAttribute(LookupItem.DONT_CHECK_FOR_INNERS, "");
    JavaAwareCompletionData.setShowFQN(item);

    if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      item.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
      item.setAttribute(LookupItem.INDICATE_ANONYMOUS, "");
    }
    item.setInsertHandler(new AnalyzingInsertHandler(expectedInfos, defaultHandler));
    result.addElement(item);
  }

  private static boolean shouldInsertExplicitTypeParams(final PsiMethod method) {
    final PsiTypeParameter[] typeParameters = method.getTypeParameters();
    if (typeParameters.length == 0) return false;

    final Set<PsiTypeParameter> set = new THashSet<PsiTypeParameter>(Arrays.asList(typeParameters));
    final PsiTypeVisitor<Boolean> typeParamSearcher = new PsiTypeVisitor<Boolean>() {
      public Boolean visitType(final PsiType type) {
        return true;
      }

      public Boolean visitArrayType(final PsiArrayType arrayType) {
        return arrayType.getComponentType().accept(this);
      }

      public Boolean visitClassType(final PsiClassType classType) {
        final PsiClass aClass = classType.resolve();
        if (aClass instanceof PsiTypeParameter && set.contains(aClass)) return false;

        final PsiType[] types = classType.getParameters();
        for (final PsiType psiType : types) {
          if (!psiType.accept(this)) return false;
        }
        return true;
      }

      public Boolean visitWildcardType(final PsiWildcardType wildcardType) {
        final PsiType bound = wildcardType.getBound();
        return bound == null || bound.accept(this);
      }
    };

    for (final PsiParameter parameter : method.getParameterList().getParameters()) {
      if (!parameter.getType().accept(typeParamSearcher)) return false;
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
        PsiElement prevElement = FilterPositionUtil.searchNonSpaceNonCommentBack(position);
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
            }
          }
          if (flag) {
            item.setAttribute(LookupItem.NEW_OBJECT_ATTR, "");
          }
        }
      }
    }

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
          return;
        }
        if (parent instanceof PsiParenthesizedExpression) {
          context.setDummyIdentifier("xxx)yyy "); // to handle type cast
          return;
        }
      }
      context.setDummyIdentifier("xxx");
    }
  }

  private static class AnalyzingInsertHandler implements InsertHandler {
    private final ExpectedTypeInfo[] myExpectedInfos;
    private final InsertHandler myHandler;

    public AnalyzingInsertHandler(final CompletionParameters parameters, final InsertHandler handler) {
      myExpectedInfos = ApplicationManager.getApplication().runReadAction(new Computable<ExpectedTypeInfo[]>() {
          public ExpectedTypeInfo[] compute() {
            final PsiExpression expr = PsiTreeUtil.getContextOfType(parameters.getPosition(), PsiExpression.class, true);
            return expr != null ? ExpectedTypesProvider.getInstance(parameters.getPosition().getProject()).getExpectedTypes(expr, true) : null;
          }
        });
      myHandler = handler;
    }

    public AnalyzingInsertHandler(final ExpectedTypeInfo[] expectedInfos, final InsertHandler handler) {
      myExpectedInfos = expectedInfos;
      myHandler = handler;
    }

    public void handleInsert(final CompletionContext context, final int startOffset, final LookupData data, final LookupItem item,
                             final boolean signatureSelected,
                             final char completionChar) {
      analyzeItem(context, item, item.getObject(), context.file.findElementAt(context.getStartOffset() + item.getLookupString().length() - 1),
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
