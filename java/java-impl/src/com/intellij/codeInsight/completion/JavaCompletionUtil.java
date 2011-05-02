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

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.completion.scope.CompletionElement;
import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler;
import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NullableLazyKey;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.filters.AndFilter;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.element.ExcludeDeclaredFilter;
import com.intellij.psi.filters.element.ExcludeSillyAssignment;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.ElementClassFilter;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NullableFunction;
import com.intellij.util.PairConsumer;
import com.intellij.util.PairFunction;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.patterns.PlatformPatterns.psiElement;

public class JavaCompletionUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.JavaCompletionUtil");
  public static final Key<PairFunction<PsiExpression, CompletionParameters, PsiType>> DYNAMIC_TYPE_EVALUATOR = Key.create("DYNAMIC_TYPE_EVALUATOR");

  static final Key<PsiType> QUALIFIER_TYPE_ATTR = Key.create("qualifierType"); // SmartPsiElementPointer to PsiType of "qualifier"
  @NonNls
  public static final String GET_PREFIX = "get";
  @NonNls
  public static final String SET_PREFIX = "set";
  @NonNls
  public static final String IS_PREFIX = "is";
  public static final OffsetKey LPAREN_OFFSET = OffsetKey.create("lparen");
  public static final OffsetKey RPAREN_OFFSET = OffsetKey.create("rparen");
  public static final OffsetKey ARG_LIST_END_OFFSET = OffsetKey.create("argListEnd");
  static final NullableLazyKey<ExpectedTypeInfo[], CompletionLocation> EXPECTED_TYPES = NullableLazyKey.create("expectedTypes", new NullableFunction<CompletionLocation, ExpectedTypeInfo[]>() {
    @Nullable
    public ExpectedTypeInfo[] fun(final CompletionLocation location) {
      if (PsiJavaPatterns.psiElement().beforeLeaf(PsiJavaPatterns.psiElement().withText("."))
        .accepts(location.getCompletionParameters().getPosition())) {
        return new ExpectedTypeInfo[0];
      }

      return JavaSmartCompletionContributor.getExpectedTypes(location.getCompletionParameters());
    }
  });
  private static final PsiElementPattern.Capture<PsiElement> LEFT_PAREN = psiElement(JavaTokenType.LPARENTH).andOr(psiElement().withParent(
      PsiExpressionList.class), psiElement().afterLeaf(".", PsiKeyword.NEW));

  public static final Key<Boolean> SUPER_METHOD_PARAMETERS = Key.create("SUPER_METHOD_PARAMETERS");

  @Nullable
  public static Set<PsiType> getExpectedTypes(final CompletionParameters parameters) {
    final PsiExpression expr = PsiTreeUtil.getContextOfType(parameters.getPosition(), PsiExpression.class, true);
    if (expr != null) {
      final Set<PsiType> set = new THashSet<PsiType>();
      for (final ExpectedTypeInfo expectedInfo : JavaSmartCompletionContributor.getExpectedTypes(parameters)) {
        set.add(expectedInfo.getType());
      }
      return set;
    }
    return null;
  }

  public static final Key<List<PsiMethod>> ALL_METHODS_ATTRIBUTE = Key.create("allMethods");

  public static PsiType getQualifierType(LookupItem item) {
    return item.getUserData(QUALIFIER_TYPE_ATTR);
  }

  public static void completeVariableNameForRefactoring(Project project, Set<LookupElement> set, String prefix, PsiType varType, VariableKind varKind) {
    JavaMemberNameCompletionContributor.completeVariableNameForRefactoring(project, set, new CamelHumpMatcher(prefix), varType, varKind,
                                                                           true);
  }

  public static String[] completeVariableNameForRefactoring(JavaCodeStyleManager codeStyleManager, final PsiType varType,
                                                               final VariableKind varKind,
                                                               SuggestedNameInfo suggestedNameInfo) {
    return JavaMemberNameCompletionContributor
      .completeVariableNameForRefactoring(codeStyleManager, new CamelHumpMatcher(""), varType, varKind, suggestedNameInfo, true);
  }

  public static PsiType eliminateWildcards(PsiType type) {
    return eliminateWildcardsInner(type, true);
  }

  static PsiType eliminateWildcardsInner(PsiType type, final boolean eliminateInTypeArguments) {
    if (eliminateInTypeArguments && type instanceof PsiClassType) {
      PsiClassType classType = ((PsiClassType)type);
      JavaResolveResult resolveResult = classType.resolveGenerics();
      PsiClass aClass = (PsiClass)resolveResult.getElement();
      if (aClass != null) {
        PsiManager manager = aClass.getManager();
        PsiTypeParameter[] typeParams = aClass.getTypeParameters();
        Map<PsiTypeParameter, PsiType> map = new HashMap<PsiTypeParameter, PsiType>();
        for (PsiTypeParameter typeParam : typeParams) {
          PsiType substituted = resolveResult.getSubstitutor().substitute(typeParam);
          if (substituted instanceof PsiWildcardType) {
            substituted = ((PsiWildcardType)substituted).getBound();
            if (substituted == null) substituted = PsiType.getJavaLangObject(manager, aClass.getResolveScope());
          }
          map.put(typeParam, substituted);
        }

        PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
        PsiSubstitutor substitutor = factory.createSubstitutor(map);
        type = factory.createType(aClass, substitutor);
      }
    }
    else if (type instanceof PsiArrayType) {
      return eliminateWildcardsInner(((PsiArrayType)type).getComponentType(), false).createArrayType();
    }
    else if (type instanceof PsiWildcardType) {
      return ((PsiWildcardType)type).getExtendsBound();
    }
    return type;
  }

  public static boolean isInExcludedPackage(@NotNull final PsiClass psiClass) {
    final String name = psiClass.getQualifiedName();
    if (name == null) return false;
    CodeInsightSettings cis = CodeInsightSettings.getInstance();
    for (String excluded : cis.EXCLUDED_PACKAGES) {
      if (name.equals(excluded) || name.startsWith(excluded + ".")) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public static PsiField getOriginalElement(@NotNull PsiField field) {
    final PsiClass cls = field.getContainingClass();
    if (cls != null) {
      final PsiClass newParent = getOriginalElement(cls);
      if (newParent != cls) {
        final PsiField original = newParent.findFieldByName(field.getName(), false);
        if (original != null) {
          return original;
        }
      }
    }
    return field;
  }

  @NotNull
  public static PsiTypeParameter getOriginalElement(@NotNull PsiTypeParameter param) {
    final PsiClass parent = PsiTreeUtil.getParentOfType(param, PsiClass.class, true, PsiMethod.class);
    if (parent != null) {
      final PsiClass newParent = getOriginalElement(parent);
      if (newParent != parent) {
        for (PsiTypeParameter parameter : newParent.getTypeParameters()) {
          if (parameter.getName().equals(param.getName())) {
            return parameter;
          }
        }
      }
    }
    return param;
  }

  @NotNull
  public static PsiClass getOriginalElement(@NotNull PsiClass cls) {
    final PsiClass containingClass = cls.getContainingClass();
    if (containingClass != null) {
      final PsiClass newParent = getOriginalElement(containingClass);
      if (newParent != containingClass) {
        return findClassByName(cls, newParent.getInnerClasses());
      }
    }

    final PsiFile containingFile = cls.getContainingFile();
    if (containingFile instanceof PsiClassOwner) {
      return findClassByName(cls, ((PsiClassOwner)containingFile.getOriginalFile()).getClasses());
    }

    return cls;
  }

  private static PsiClass findClassByName(PsiClass defResult, PsiClass[] classes) {
    String name = defResult.getName();
    if (name == null) return defResult;

    for (PsiClass candidate : classes) {
      if (name.equals(candidate.getName())) {
        return candidate;
      }
    }
    return defResult;
  }

  @SuppressWarnings({"unchecked"})
  @NotNull
  public static <T extends PsiType> T originalize(@NotNull T type) {
    if (!type.isValid()) {
      return type;
    }

    return (T)type.accept(new PsiTypeVisitor<PsiType>() {

      public PsiType visitArrayType(final PsiArrayType arrayType) {
        return new PsiArrayType(originalize(arrayType.getComponentType()));
      }

      public PsiType visitCapturedWildcardType(final PsiCapturedWildcardType capturedWildcardType) {
        return PsiCapturedWildcardType.create(originalize(capturedWildcardType.getWildcard()), capturedWildcardType.getContext());
      }

      public PsiType visitClassType(final PsiClassType classType) {
        final PsiClassType.ClassResolveResult classResolveResult = classType.resolveGenerics();
        final PsiClass psiClass = classResolveResult.getElement();
        final PsiSubstitutor substitutor = classResolveResult.getSubstitutor();
        if (psiClass == null) return classType;

        LOG.assertTrue(psiClass.isValid());

        return new PsiImmediateClassType(getOriginalElement(psiClass), originalize(substitutor));
      }

      public PsiType visitEllipsisType(final PsiEllipsisType ellipsisType) {
        return new PsiEllipsisType(originalize(ellipsisType.getComponentType()));
      }

      public PsiType visitPrimitiveType(final PsiPrimitiveType primitiveType) {
        return primitiveType;
      }

      public PsiType visitType(final PsiType type) {
        return type;
      }

      public PsiType visitWildcardType(final PsiWildcardType wildcardType) {
        final PsiType bound = wildcardType.getBound();
        final PsiManager manager = wildcardType.getManager();
        if (bound == null) return PsiWildcardType.createUnbounded(manager);
        return wildcardType.isExtends() ? PsiWildcardType.createExtends(manager, bound) : PsiWildcardType.createSuper(manager, bound);
      }
    });
  }

  @Nullable
  private static PsiSubstitutor originalize(@Nullable final PsiSubstitutor substitutor) {
    if (substitutor == null) return null;

    PsiSubstitutor originalSubstitutor = PsiSubstitutor.EMPTY;
    for (final Map.Entry<PsiTypeParameter, PsiType> entry : substitutor.getSubstitutionMap().entrySet()) {
      final PsiType value = entry.getValue();
      originalSubstitutor = originalSubstitutor.put(getOriginalElement(entry.getKey()), value == null ? null : originalize(value));
    }
    return originalSubstitutor;
  }

  public static void initOffsets(final PsiFile file, final Project project, final OffsetMap offsetMap){
    int selectionEndOffset = offsetMap.getOffset(CompletionInitializationContext.SELECTION_END_OFFSET);

    PsiElement element = file.findElementAt(selectionEndOffset);
    if (element == null) return;

    if (LEFT_PAREN.accepts(element)) {
      selectionEndOffset--;
      element = file.findElementAt(selectionEndOffset);
      if (element == null) return;
    }

    final PsiReference reference = file.findReferenceAt(selectionEndOffset);
    if(reference != null) {
      offsetMap.addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET,
                          reference.getElement().getTextRange().getStartOffset() + reference.getRangeInElement().getEndOffset());

      element = file.findElementAt(offsetMap.getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET));
    }
    else if (isWord(element)){
      offsetMap.addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, element.getTextRange().getEndOffset());

      element = file.findElementAt(offsetMap.getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET));
      if (element == null) return;
    }

    if (element instanceof PsiWhiteSpace &&
        ( !element.textContains('\n') ||
          CodeStyleSettingsManager.getInstance(project).getCurrentSettings().METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE
        )
       ){
      element = file.findElementAt(element.getTextRange().getEndOffset());
    }

    if (LEFT_PAREN.accepts(element)) {
      offsetMap.addOffset(LPAREN_OFFSET, element.getTextRange().getStartOffset());
      PsiElement list = element.getParent();
      PsiElement last = list.getLastChild();
      if (last instanceof PsiJavaToken && ((PsiJavaToken)last).getTokenType() == JavaTokenType.RPARENTH) {
        offsetMap.addOffset(RPAREN_OFFSET, last.getTextRange().getStartOffset());
      }


      offsetMap.addOffset(ARG_LIST_END_OFFSET, list.getTextRange().getEndOffset());
    }

  }

  static boolean isWord(PsiElement element) {
    if (element instanceof PsiIdentifier || element instanceof PsiKeyword) {
      return true;
    }
    else if (element instanceof PsiJavaToken) {
      final String text = element.getText();
      if (PsiKeyword.TRUE.equals(text)) return true;
      if (PsiKeyword.FALSE.equals(text)) return true;
      if (PsiKeyword.NULL.equals(text)) return true;
      return false;
    }
    else if (element instanceof PsiDocToken) {
      IElementType tokenType = ((PsiDocToken)element).getTokenType();
      return tokenType == JavaDocTokenType.DOC_TAG_VALUE_TOKEN || tokenType == JavaDocTokenType.DOC_TAG_NAME;
    }
    else if (element instanceof XmlToken) {
      IElementType tokenType = ((XmlToken)element).getTokenType();
      return tokenType == XmlTokenType.XML_TAG_NAME ||
             tokenType == XmlTokenType.XML_NAME ||
             tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN ||
             // html data chars contains whitespaces
             (tokenType == XmlTokenType.XML_DATA_CHARACTERS && !(element.getParent() instanceof HtmlTag));
    }
    else {
      return false;
    }
  }

  public static void resetParensInfo(final OffsetMap offsetMap) {
    offsetMap.removeOffset(LPAREN_OFFSET);
    offsetMap.removeOffset(RPAREN_OFFSET);
    offsetMap.removeOffset(ARG_LIST_END_OFFSET);
    offsetMap.removeOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET);
  }

  @Nullable
  public static List<? extends PsiElement> getAllPsiElements(final LookupElement item) {
    List<PsiMethod> allMethods = item.getUserData(ALL_METHODS_ATTRIBUTE);
    if (allMethods != null) return allMethods;
    if (item.getObject() instanceof PsiElement) return Arrays.asList((PsiElement)item.getObject());
    return null;
  }

  @Nullable
  private static PsiType getPsiType(final Object o) {
    if (o instanceof PsiVariable) {
      return ((PsiVariable)o).getType();
    }
    else if (o instanceof PsiMethod) {
      return ((PsiMethod)o).getReturnType();
    }
    else if (o instanceof PsiClass) {
      final PsiClass psiClass = (PsiClass)o;
      return JavaPsiFacade.getInstance(psiClass.getProject()).getElementFactory().createType(psiClass);
    }
    else if (o instanceof PsiExpression) {
      return ((PsiExpression)o).getType();
    }
    return null;
  }

  @Nullable
  public static PsiType getLookupElementType(final LookupElement element) {
    TypedLookupItem typed = element.as(TypedLookupItem.CLASS_CONDITION_KEY);
    if (typed != null) {
      return typed.getType();
    }

    final PsiType qualifierType = getPsiType(element.getObject());
    final LookupItem lookupItem = element.as(LookupItem.CLASS_CONDITION_KEY);
    if (lookupItem != null) {
      final Object o = lookupItem.getAttribute(LookupItem.TYPE);
      if (o instanceof PsiType) {
        return (PsiType)o;
      }

      final PsiSubstitutor substitutor = (PsiSubstitutor)lookupItem.getAttribute(LookupItem.SUBSTITUTOR);
      if (substitutor != null) {
        return substitutor.substitute(qualifierType);
      }
    }
    return qualifierType;
  }

  @Nullable
  public static PsiType getQualifiedMemberReferenceType(@Nullable PsiType qualifierType, @NotNull final PsiMember member) {
    final Ref<PsiSubstitutor> subst = Ref.create(PsiSubstitutor.EMPTY);
    class MyProcessor extends BaseScopeProcessor implements NameHint, ElementClassHint {
      public boolean execute(PsiElement element, ResolveState state) {
        if (element == member) {
          subst.set(state.get(PsiSubstitutor.KEY));
        }
        return true;
      }

      public String getName(ResolveState state) {
        return member.getName();
      }

      public boolean shouldProcess(DeclarationKind kind) {
        return member instanceof PsiEnumConstant ? kind == DeclarationKind.ENUM_CONST :
               member instanceof PsiField ? kind == DeclarationKind.FIELD :
               kind == DeclarationKind.METHOD;
      }

      @Override
      public <T> T getHint(Key<T> hintKey) {
        return hintKey == NameHint.KEY || hintKey == ElementClassHint.KEY ? (T)this : null;
      }
    }

    PsiScopesUtil.processTypeDeclarations(qualifierType, member, new MyProcessor());

    PsiType rawType = member instanceof PsiField ? ((PsiField) member).getType() : ((PsiMethod) member).getReturnType();
    return subst.get().substitute(rawType);
  }

  public static Set<LookupElement> processJavaReference(PsiElement element, PsiJavaReference javaReference, ElementFilter elementFilter,
                                                        final boolean checkAccess, @Nullable final PrefixMatcher matcher, CompletionParameters parameters) {
    final THashSet<LookupElement> set = new THashSet<LookupElement>();
    final Condition<String> nameCondition = matcher == null ? null : new Condition<String>() {
      public boolean value(String s) {
        return matcher.prefixMatches(s);
      }
    };
    final JavaCompletionProcessor processor = new JavaCompletionProcessor(element, elementFilter, checkAccess, parameters.getInvocationCount() <= 1, nameCondition);
    javaReference.processVariants(processor);
    final Collection<CompletionElement> plainResults = processor.getResults();

    final PsiType qualifierType = processor.getQualifierType();
    PsiType castedQualifierType = addQualifierCastingVariants(javaReference, processor, set, parameters);

    boolean mayHighlight = qualifierType != null && (castedQualifierType == null || !qualifierType.isAssignableFrom(castedQualifierType));

    final boolean pkgContext = inSomePackage(element);

    final Set<PsiMember> mentioned = new THashSet<PsiMember>();
    for (CompletionElement completionElement : plainResults) {
      LookupElement item = createLookupElement(completionElement, qualifierType);
      if (item != null) {
        final Object o = item.getObject();
        if (o instanceof PsiClass) {
          if (!isSourceLevelAccessible(element, (PsiClass)o, pkgContext)) {
            continue;
          }
        }
        if (o instanceof PsiMember) {
          mentioned.add((PsiMember)o);
        }
        set.add(mayHighlight ? highlightIfNeeded(qualifierType, item, o) : item);
      }
    }

    if (javaReference instanceof PsiReferenceExpression && !((PsiReferenceExpression)javaReference).isQualified()) {
      final StaticMemberProcessor memberProcessor = JavaGlobalMemberNameCompletionContributor.completeStaticMembers(element);
      memberProcessor.processMembersOfRegisteredClasses(matcher, new PairConsumer<PsiMember, PsiClass>() {
        @Override
        public void consume(PsiMember member, PsiClass psiClass) {
          if (!mentioned.contains(member) && processor.satisfies(member, ResolveState.initial())) {
            set.add(memberProcessor.createLookupElement(member, psiClass, true));
          }
        }
      });
    }

    return set;
  }

  @Nullable
  private static PsiType addQualifierCastingVariants(PsiJavaReference javaReference, JavaCompletionProcessor processor, THashSet<LookupElement> set, CompletionParameters parameters) {
    if (javaReference instanceof PsiReferenceExpression) {
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)javaReference;
      final PsiExpression qualifier = refExpr.getQualifierExpression();
      if (qualifier != null) {
        final Project project = qualifier.getProject();
        final PairFunction<PsiExpression, CompletionParameters, PsiType> evaluator = refExpr.getContainingFile().getCopyableUserData(DYNAMIC_TYPE_EVALUATOR);
        PsiReferenceExpression context = refExpr;
        PsiType type = null;
        if (evaluator != null) {
          type = evaluator.fun(qualifier, parameters);
          context = null;
        }
        if (type == null) {
          type = GuessManager.getInstance(project).getControlFlowExpressionType(qualifier);
          context = refExpr;
        }
        if (type != null) {
          processor.clear();

          return addQualifierCastingVariants(processor, refExpr, type, set, context);
        }
      }
    }
    return null;
  }

  private static PsiType addQualifierCastingVariants(JavaCompletionProcessor processor, PsiReferenceExpression refExpr,
                                                     PsiType castTo,
                                                     THashSet<LookupElement> set, final PsiReferenceExpression context) {
    Project project = refExpr.getProject();

    PsiExpression qualifier = refExpr.getQualifierExpression();
    assert qualifier != null;
    final String newText = "((" + castTo.getCanonicalText() + ") " + qualifier.getText() + ")." + refExpr.getReferenceName();
    final PsiExpression newRef = JavaPsiFacade.getElementFactory(project).createExpressionFromText(newText, context);
    ((PsiReferenceExpression)newRef).processVariants(processor);

    final LookupElement castItem = PsiTypeLookupItem.createLookupItem(castTo, refExpr);

    for (CompletionElement completionElement : processor.getResults()) {
      final LookupElement item = createLookupElement(completionElement, castTo);
      if (item != null) {
        LookupElement item1 = castQualifier(project, item, castItem);
        set.add(highlightIfNeeded(castTo, item1, item1.getObject()));
      }
    }
    return castTo;
  }

  private static LookupElementDecorator<LookupElement> castQualifier(final Project project, LookupElement item, final LookupElement to) {
    return LookupElementDecorator.withInsertHandler(item, new InsertHandlerDecorator<LookupElement>() {
      public void handleInsert(InsertionContext context, LookupElementDecorator<LookupElement> item) {
        final Document document = context.getEditor().getDocument();
        PsiDocumentManager.getInstance(project).commitDocument(document);
        final PsiFile file = context.getFile();
        final PsiJavaCodeReferenceElement ref =
          PsiTreeUtil.findElementOfClassAtOffset(file, context.getStartOffset(), PsiJavaCodeReferenceElement.class, false);
        if (ref != null) {
          final PsiElement qualifier = ref.getQualifier();
          if (qualifier != null) {
            final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project);

            final String parenSpace = settings.SPACE_WITHIN_PARENTHESES ? " " : "";
            document.insertString(qualifier.getTextRange().getEndOffset(), parenSpace + ")");

            final String spaceWithin = settings.SPACE_WITHIN_CAST_PARENTHESES ? " " : "";
            final String prefix = "(" + parenSpace + "(" + spaceWithin;
            final String spaceAfter = settings.SPACE_AFTER_TYPE_CAST ? " " : "";
            final int exprStart = qualifier.getTextRange().getStartOffset();
            document.insertString(exprStart, prefix + spaceWithin + ")" + spaceAfter);

            CompletionUtil.emulateInsertion(context, exprStart + prefix.length(), to);
            context.getEditor().getCaretModel().moveToOffset(context.getTailOffset());
          }
        }

        item.getDelegate().handleInsert(context);
      }
    });
  }

  public static LookupElement highlightIfNeeded(PsiType qualifierType, LookupElement item, Object object) {
    if (qualifierType instanceof PsiArrayType) {
      if (object instanceof PsiField || object instanceof PsiMethod) { //length and clone()
        PsiElement parent = ((PsiElement)object).getParent();
        if (parent instanceof PsiClass && parent.getContainingFile().getVirtualFile() == null) { //yes, they're a bit dummy
          return highlight(item);
        }
      }
    }
    else if (qualifierType instanceof PsiClassType) {
      PsiClass qualifierClass = ((PsiClassType)qualifierType).resolve();
      if (object instanceof PsiField || object instanceof PsiMethod || object instanceof PsiClass) {
        PsiClass parent = ((PsiMember)object).getContainingClass();
        if (parent != null && parent.equals(qualifierClass)) {
          return highlight(item);
        }
      }
    }
    return item;
  }

  private static LookupElement highlight(LookupElement decorator) {
    return PrioritizedLookupElement.withGrouping(
      LookupElementDecorator.withRenderer(decorator, new LookupElementRenderer<LookupElementDecorator<LookupElement>>() {
        @Override
        public void renderElement(LookupElementDecorator<LookupElement> element, LookupElementPresentation presentation) {
          element.getDelegate().renderElement(presentation);
          presentation.setItemTextBold(true);
        }
      }), 1);
  }

  private static LookupItem<?> createLookupElement(CompletionElement completionElement, PsiType qualifierType) {
    Object completion = completionElement.getElement();
    assert !(completion instanceof LookupElement);


    LookupElement _ret = LookupItemUtil.objectToLookupItem(completion);
    if (_ret == null || !(_ret instanceof LookupItem)) return null;

    LookupItem<?> ret = (LookupItem<?>)_ret;
    final PsiSubstitutor substitutor = completionElement.getSubstitutor();
    if (substitutor != null) {
      ret.setAttribute(LookupItem.SUBSTITUTOR, substitutor);
    }
    ret.putUserData(QUALIFIER_TYPE_ATTR, qualifierType);

    return ret;
  }

  public static boolean hasAccessibleConstructor(PsiType type) {
    if (type instanceof PsiArrayType) return true;

    final PsiClass psiClass = PsiUtil.resolveClassInType(type);
    if (psiClass == null) return false;

    if (!(psiClass instanceof PsiCompiledElement)) return true;

    final PsiMethod[] methods = psiClass.getConstructors();
    if (methods.length == 0) return true;

    for (final PsiMethod method : methods) {
      if (!method.hasModifierProperty(PsiModifier.PRIVATE)) return true;
    }
    return false;
  }

  public static LookupItem qualify(final LookupItem ret) {
    return ret.forceQualify();
  }

  public static Set<String> getAllLookupStrings(@NotNull PsiMember member) {
    Set<String> allLookupStrings = CollectionFactory.linkedHashSet();
    String name = member.getName();
    allLookupStrings.add(name);
    PsiClass containingClass = member.getContainingClass();
    while (containingClass != null) {
      final String className = containingClass.getName();
      if (className == null) {
        break;
      }
      name = className + "." + name;
      allLookupStrings.add(name);
      final PsiElement parent = containingClass.getParent();
      if (!(parent instanceof PsiClass)) {
        break;
      }
      containingClass = (PsiClass)parent;
    }
    return allLookupStrings;
  }

  public static LookupItem setShowFQN(final LookupItem ret) {
    final PsiClass psiClass = (PsiClass)ret.getObject();
    @NonNls String packageName = PsiFormatUtil.getPackageDisplayName(psiClass);

    final String tailText = (String)ret.getAttribute(LookupItem.TAIL_TEXT_ATTR);
    ret.setAttribute(LookupItem.TAIL_TEXT_ATTR, StringUtil.notNullize(tailText) + " (" + packageName + ")");
    ret.setAttribute(LookupItem.TAIL_TEXT_SMALL_ATTR, "");
    return ret;
  }

  @Nullable
  static PsiElement getQualifier(final PsiElement element) {
    return element instanceof PsiJavaCodeReferenceElement ? ((PsiJavaCodeReferenceElement)element).getQualifier() : null;
  }

  public static boolean containsMethodCalls(@Nullable final PsiElement qualifier) {
    if (qualifier == null) return false;
    if (qualifier instanceof PsiMethodCallExpression) return true;
    if (qualifier instanceof PsiArrayAccessExpression) {
      return containsMethodCalls(((PsiArrayAccessExpression)qualifier).getArrayExpression());
    }
    return containsMethodCalls(getQualifier(qualifier));
  }

  @Nullable
  static ElementFilter recursionFilter(PsiElement element) {
    if (PsiJavaPatterns.psiElement().afterLeaf(PsiKeyword.RETURN).inside(PsiReturnStatement.class).accepts(element)) {
      return new ExcludeDeclaredFilter(ElementClassFilter.METHOD);
    }

    if (PsiJavaPatterns.psiElement().inside(
        PsiJavaPatterns.or(
            PsiJavaPatterns.psiElement(PsiAssignmentExpression.class),
            PsiJavaPatterns.psiElement(PsiVariable.class))).
        andNot(PsiJavaPatterns.psiElement().afterLeaf(".")).accepts(element)) {
      return new AndFilter(new ExcludeSillyAssignment(),
                                                   new ExcludeDeclaredFilter(new ClassFilter(PsiVariable.class)));
    }
    return null;
  }

  public static int insertClassReference(@NotNull PsiClass psiClass, @NotNull PsiFile file, int offset) {
    return insertClassReference(psiClass, file, offset, offset);
  }

  public static int insertClassReference(PsiClass psiClass, PsiFile file, int startOffset, int endOffset) {
    final Project project = file.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final PsiManager manager = file.getManager();

    final Document document = FileDocumentManager.getInstance().getDocument(file.getViewProvider().getVirtualFile());

    final PsiReference reference = file.findReferenceAt(startOffset);
    if (reference != null) {
      final PsiElement resolved = reference.resolve();
      if (resolved instanceof PsiClass) {
        if (((PsiClass)resolved).getQualifiedName() == null || manager.areElementsEquivalent(psiClass, resolved)) {
          return startOffset;
        }
      }
    }

    String name = psiClass.getName();
    if (name == null) {
      return startOffset;
    }

    assert document != null;
    document.replaceString(startOffset, endOffset, name);

    final RangeMarker toDelete = insertTemporary(startOffset + name.length(), document, "#");

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    int newStartOffset = startOffset;
    PsiElement element = file.findElementAt(startOffset);
    if (element instanceof PsiIdentifier) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement && !((PsiJavaCodeReferenceElement)parent).isQualified() && !(parent.getParent() instanceof PsiPackageStatement)) {
        PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)parent;

        if (!psiClass.getManager().areElementsEquivalent(psiClass, resolveReference(ref))) {
          final boolean staticImport = ref instanceof PsiImportStaticReferenceElement;
          PsiElement newElement = staticImport
                                  ? ((PsiImportStaticReferenceElement)ref).bindToTargetClass(psiClass)
                                  : ref.bindToElement(psiClass);

          RangeMarker marker = document.createRangeMarker(newElement.getTextRange());
          newElement = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(newElement);
          newStartOffset = marker.getStartOffset();

          if (!staticImport &&
              newElement instanceof PsiJavaCodeReferenceElement &&
              !psiClass.getManager().areElementsEquivalent(psiClass, resolveReference((PsiReference)newElement))) {
            final String qName = psiClass.getQualifiedName();
            if (qName != null) {
              document.replaceString(newStartOffset, newElement.getTextRange().getEndOffset(), qName);
            }
          }
        }
      }
    }

    if (toDelete.isValid()) {
      document.deleteString(toDelete.getStartOffset(), toDelete.getEndOffset());
    }

    return newStartOffset;
  }

  @Nullable
  static PsiElement resolveReference(final PsiReference psiReference) {
    if (psiReference instanceof PsiPolyVariantReference) {
      final ResolveResult[] results = ((PsiPolyVariantReference)psiReference).multiResolve(true);
      if (results.length == 1) return results[0].getElement();
    }
    return psiReference.resolve();
  }

  public static RangeMarker insertTemporary(final int endOffset, final Document document, final String temporary) {
    final CharSequence chars = document.getCharsSequence();
    final int length = chars.length();
    final RangeMarker toDelete;
    if (endOffset < length && Character.isJavaIdentifierPart(chars.charAt(endOffset))){
      document.insertString(endOffset, temporary);
      toDelete = document.createRangeMarker(endOffset, endOffset + 1);
    } else if (endOffset >= length) {
      toDelete = document.createRangeMarker(length, length);
    }
    else {
      toDelete = document.createRangeMarker(endOffset, endOffset);
    }
    toDelete.setGreedyToLeft(true);
    toDelete.setGreedyToRight(true);
    return toDelete;
  }

  public static void insertParentheses(final InsertionContext context,
                                       final LookupElement item,
                                       boolean overloadsMatter,
                                       boolean hasParams) {
    insertParentheses(context, item, overloadsMatter, hasParams, false);
  }

  public static void insertParentheses(final InsertionContext context,
                                       final LookupElement item,
                                       boolean overloadsMatter,
                                       boolean hasParams,
                                       final boolean forceClosingParenthesis) {
    final Editor editor = context.getEditor();
    final char completionChar = context.getCompletionChar();
    final PsiFile file = context.getFile();

    final TailType tailType = completionChar == '(' ? TailType.NONE : completionChar == ':' ? TailType.COND_EXPR_COLON : LookupItem.handleCompletionChar(context.getEditor(), item, completionChar);
    final boolean hasTail = tailType != TailType.NONE && tailType != TailType.UNKNOWN;
    final boolean smart = completionChar == Lookup.COMPLETE_STATEMENT_SELECT_CHAR;

    if (completionChar == '(' || completionChar == '.' || completionChar == ',' || completionChar == ';' || completionChar == ':') {
      context.setAddCompletionChar(false);
    }

    final boolean needRightParenth = forceClosingParenthesis || !smart && (CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET || hasTail);
    if (hasTail) {
      hasParams = false;
    }

    final CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(context.getProject());
    ParenthesesInsertHandler.getInstance(hasParams,
                                         styleSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES,
                                         styleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES && hasParams,
                                         needRightParenth,
                                         styleSettings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE
    ).handleInsert(context, item);

    if (hasParams) {
      // Invoke parameters popup
      AutoPopupController.getInstance(file.getProject()).autoPopupParameterInfo(editor, overloadsMatter ? null : (PsiElement)item.getObject());
    }

    if (smart || needRightParenth) {
      TailType toInsert = tailType;
      LookupItem lookupItem = item.as(LookupItem.CLASS_CONDITION_KEY);
      if (lookupItem == null || lookupItem.getAttribute(LookupItem.TAIL_TYPE_ATTR) != TailType.UNKNOWN) {
        if (!hasTail && item.getObject() instanceof PsiMethod && ((PsiMethod)item.getObject()).getReturnType() == PsiType.VOID) {
          PsiDocumentManager.getInstance(file.getProject()).commitAllDocuments();
          if (psiElement().beforeLeaf(psiElement().withText(".")).accepts(file.findElementAt(context.getTailOffset() - 1))) {
            return;
          }
          toInsert = TailType.SEMICOLON;
        }
      }
      toInsert.processTail(editor, context.getTailOffset());

      if (completionChar == '.') {
        AutoPopupController.getInstance(file.getProject()).autoPopupMemberLookup(context.getEditor(), null);
      }
    }
  }

  //need to shorten references in type argument list
  public static void shortenReference(final PsiFile file, final int offset) throws IncorrectOperationException {
    final PsiDocumentManager manager = PsiDocumentManager.getInstance(file.getProject());
    final Document document = manager.getDocument(file);
    assert document != null;
    manager.commitDocument(document);
    final PsiReference ref = file.findReferenceAt(offset);
    if (ref instanceof PsiJavaCodeReferenceElement) {
      JavaCodeStyleManager.getInstance(file.getProject()).shortenClassReferences((PsiJavaCodeReferenceElement)ref);
    }
  }

  public static boolean hasAccessibleInnerClass(@NotNull PsiClass psiClass, @NotNull PsiElement position) {
    final PsiClass[] inners = psiClass.getInnerClasses();
    if (inners.length > 0) {
      PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(position.getProject()).getResolveHelper();
      for (PsiClass inner : inners) {
        if (inner.hasModifierProperty(PsiModifier.STATIC) && resolveHelper.isAccessible(inner, position, null)) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean inSomePackage(PsiElement context) {
    PsiFile contextFile = context.getContainingFile();
    return contextFile instanceof PsiClassOwner && StringUtil.isNotEmpty(((PsiClassOwner)contextFile).getPackageName());
  }

  public static boolean isSourceLevelAccessible(PsiElement context, PsiClass psiClass, final boolean pkgContext) {
    if (!JavaPsiFacade.getInstance(psiClass.getProject()).getResolveHelper().isAccessible(psiClass, context, null)) {
      return false;
    }

    if (pkgContext) {
      PsiFile classFile = psiClass.getContainingFile();
      if (classFile instanceof PsiClassOwner && StringUtil.isEmpty(((PsiClassOwner)classFile).getPackageName())) {
        return false;
      }
    }

    return true;
  }

  public static boolean isDefinitelyExpected(PsiClass psiClass, Set<PsiType> expectedTypes, PsiElement position) {
    final PsiClassType classType = JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass);
    for (PsiType expectedType : expectedTypes) {
      if (expectedType instanceof PsiArrayType) return false;
    }
    for (PsiType type : expectedTypes) {
      if (type instanceof PsiClassType && ((PsiClassType)type).rawType().isAssignableFrom(classType)) {
        return true;
      }
    }
    return !hasAccessibleInnerClass(psiClass, position);
  }
}
