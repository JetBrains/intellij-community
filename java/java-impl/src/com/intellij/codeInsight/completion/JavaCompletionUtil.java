/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.codeInsight.daemon.impl.analysis.LambdaHighlightingUtil;
import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInspection.java15api.Java15APIUsageInspectionBase;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.impl.light.LightVariableBuilder;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NullableFunction;
import com.intellij.util.PairConsumer;
import com.intellij.util.PairFunction;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.SideEffectChecker;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.patterns.PlatformPatterns.psiElement;

public class JavaCompletionUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.JavaCompletionUtil");
  public static final Key<PairFunction<PsiExpression, CompletionParameters, PsiType>> DYNAMIC_TYPE_EVALUATOR = Key.create("DYNAMIC_TYPE_EVALUATOR");

  private static final Key<PsiType> QUALIFIER_TYPE_ATTR = Key.create("qualifierType"); // SmartPsiElementPointer to PsiType of "qualifier"
  public static final OffsetKey LPAREN_OFFSET = OffsetKey.create("lparen");
  public static final OffsetKey RPAREN_OFFSET = OffsetKey.create("rparen");
  public static final OffsetKey ARG_LIST_END_OFFSET = OffsetKey.create("argListEnd");
  static final NullableLazyKey<ExpectedTypeInfo[], CompletionLocation> EXPECTED_TYPES = NullableLazyKey.create("expectedTypes", new NullableFunction<CompletionLocation, ExpectedTypeInfo[]>() {
    @Override
    @Nullable
    public ExpectedTypeInfo[] fun(final CompletionLocation location) {
      if (PsiJavaPatterns.psiElement().beforeLeaf(PsiJavaPatterns.psiElement().withText("."))
        .accepts(location.getCompletionParameters().getPosition())) {
        return ExpectedTypeInfo.EMPTY_ARRAY;
      }

      return JavaSmartCompletionContributor.getExpectedTypes(location.getCompletionParameters());
    }
  });
  private static final ElementPattern<PsiElement> LEFT_PAREN = psiElement(JavaTokenType.LPARENTH).andOr(psiElement().withParent(
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
    final CamelHumpMatcher camelHumpMatcher = new CamelHumpMatcher(prefix);
    JavaMemberNameCompletionContributor.completeVariableNameForRefactoring(project, set, camelHumpMatcher, varType, varKind, true, false);
  }

  public static String[] completeVariableNameForRefactoring(JavaCodeStyleManager codeStyleManager, @Nullable final PsiType varType,
                                                               final VariableKind varKind,
                                                               SuggestedNameInfo suggestedNameInfo) {
    return JavaMemberNameCompletionContributor
      .completeVariableNameForRefactoring(codeStyleManager, new CamelHumpMatcher(""), varType, varKind, suggestedNameInfo, true, false);
  }

  public static boolean isInExcludedPackage(@NotNull final PsiMember member, boolean allowInstanceInnerClasses) {
    final String name = PsiUtil.getMemberQualifiedName(member);
    if (name == null) return false;

    if (!member.hasModifierProperty(PsiModifier.STATIC)) {
      if (member instanceof PsiMethod || member instanceof PsiField) {
        return false;
      }
      if (allowInstanceInnerClasses && member instanceof PsiClass && member.getContainingClass() != null) {
        return false;
      }
    }

    CodeInsightSettings cis = CodeInsightSettings.getInstance();
    for (String excluded : cis.EXCLUDED_PACKAGES) {
      if (name.equals(excluded) || name.startsWith(excluded + ".")) {
        return true;
      }
    }
    return false;
  }

  @SuppressWarnings({"unchecked"})
  @NotNull
  public static <T extends PsiType> T originalize(@NotNull T type) {
    if (!type.isValid()) {
      return type;
    }

    T result = new PsiTypeMapper() {
      private final Set<PsiClassType> myVisited = ContainerUtil.newIdentityTroveSet();
      
      @Override
      public PsiType visitClassType(final PsiClassType classType) {
        if (!myVisited.add(classType)) return classType;
        
        final PsiClassType.ClassResolveResult classResolveResult = classType.resolveGenerics();
        final PsiClass psiClass = classResolveResult.getElement();
        final PsiSubstitutor substitutor = classResolveResult.getSubstitutor();
        if (psiClass == null) return classType;

        return new PsiImmediateClassType(CompletionUtil.getOriginalOrSelf(psiClass), originalizeSubstitutor(substitutor));
      }

      private PsiSubstitutor originalizeSubstitutor(final PsiSubstitutor substitutor) {
        PsiSubstitutor originalSubstitutor = PsiSubstitutor.EMPTY;
        for (final Map.Entry<PsiTypeParameter, PsiType> entry : substitutor.getSubstitutionMap().entrySet()) {
          final PsiType value = entry.getValue();
          originalSubstitutor = originalSubstitutor.put(CompletionUtil.getOriginalOrSelf(entry.getKey()), 
                                                        value == null ? null : mapType(value));
        }
        return originalSubstitutor;
      }


      @Override
      public PsiType visitType(PsiType type) {
        return type;
      }
    }.mapType(type);
    if (result == null) {
      throw new AssertionError("Null result for type " + type + " of class " + type.getClass());
    }
    return result;
  }

  public static void initOffsets(final PsiFile file, final OffsetMap offsetMap) {
    int offset = Math.max(offsetMap.getOffset(CompletionInitializationContext.SELECTION_END_OFFSET),
                          offsetMap.getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET));

    PsiElement element = file.findElementAt(offset);
    if (element instanceof PsiWhiteSpace &&
        (!element.textContains('\n') ||
         CodeStyleSettingsManager.getSettings(file.getProject()).getCommonSettings(JavaLanguage.INSTANCE).METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE
        )) {
      element = file.findElementAt(element.getTextRange().getEndOffset());
    }
    if (element == null) return;

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
    if (o instanceof ResolveResult) {
      return getPsiType(((ResolveResult)o).getElement());
    }
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
      @Override
      public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
        if (element == member) {
          subst.set(state.get(PsiSubstitutor.KEY));
        }
        return true;
      }

      @Override
      public String getName(@NotNull ResolveState state) {
        return member.getName();
      }

      @Override
      public boolean shouldProcess(DeclarationKind kind) {
        return member instanceof PsiEnumConstant ? kind == DeclarationKind.ENUM_CONST :
               member instanceof PsiField ? kind == DeclarationKind.FIELD :
               kind == DeclarationKind.METHOD;
      }

      @Override
      public <T> T getHint(@NotNull Key<T> hintKey) {
        return hintKey == NameHint.KEY || hintKey == ElementClassHint.KEY ? (T)this : null;
      }
    }

    PsiScopesUtil.processTypeDeclarations(qualifierType, member, new MyProcessor());

    PsiType rawType = member instanceof PsiField ? ((PsiField) member).getType() :
                      member instanceof PsiMethod ? ((PsiMethod) member).getReturnType() :
                      JavaPsiFacade.getElementFactory(member.getProject()).createType((PsiClass)member);
    return subst.get().substitute(rawType);
  }

  public static Set<LookupElement> processJavaReference(PsiElement element, PsiJavaReference javaReference, ElementFilter elementFilter,
                                                        JavaCompletionProcessor.Options options,
                                                        final PrefixMatcher matcher, CompletionParameters parameters) {
    final Set<LookupElement> set = new LinkedHashSet<LookupElement>();
    final Condition<String> nameCondition = new Condition<String>() {
      @Override
      public boolean value(String s) {
        return matcher.prefixMatches(s);
      }
    };

    PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
    boolean checkInitialized = parameters.getInvocationCount() <= 1 && call != null && PsiKeyword.SUPER.equals(call.getMethodExpression().getText());

    final JavaCompletionProcessor processor = new JavaCompletionProcessor(element, elementFilter, options.withInitialized(checkInitialized), nameCondition);
    final PsiType plainQualifier = processor.getQualifierType();
    PsiType qualifierType = plainQualifier;

    PsiType runtimeQualifier = getQualifierCastType(javaReference, parameters);
    if (runtimeQualifier != null) {
      PsiType composite = qualifierType == null ? runtimeQualifier : PsiIntersectionType.createIntersection(qualifierType, runtimeQualifier);
      PsiElement ctx = createContextWithXxxVariable(element, composite);
      javaReference = (PsiReferenceExpression) JavaPsiFacade.getElementFactory(element.getProject()).createExpressionFromText("xxx.xxx", ctx);
      qualifierType = runtimeQualifier;
      processor.setQualifierType(qualifierType);
    }

    javaReference.processVariants(processor);

    final PsiTypeLookupItem castItem = runtimeQualifier == null ? null : PsiTypeLookupItem.createLookupItem(runtimeQualifier, (PsiReferenceExpression)javaReference);

    final boolean pkgContext = inSomePackage(element);

    final Set<PsiMember> mentioned = new THashSet<PsiMember>();
    for (CompletionElement completionElement : processor.getResults()) {
      for (LookupElement item : createLookupElements(completionElement, javaReference)) {
        item.putUserData(QUALIFIER_TYPE_ATTR, qualifierType);
        final Object o = item.getObject();
        if (o instanceof PsiClass && !isSourceLevelAccessible(element, (PsiClass)o, pkgContext)) {
          continue;
        }
        if (o instanceof PsiMember) {
          if (isInExcludedPackage((PsiMember)o, true)) {
            continue;
          }
          mentioned.add(CompletionUtil.getOriginalOrSelf((PsiMember)o));
        }
        set.add(highlightIfNeeded(qualifierType, castQualifier(item, castItem, plainQualifier, processor), o, element));
      }
    }

    if (javaReference instanceof PsiJavaCodeReferenceElement && !((PsiJavaCodeReferenceElement)javaReference).isQualified()) {
      final StaticMemberProcessor memberProcessor = new JavaStaticMemberProcessor(parameters);
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
  private static PsiType getQualifierCastType(PsiJavaReference javaReference, CompletionParameters parameters) {
    if (javaReference instanceof PsiReferenceExpression) {
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)javaReference;
      final PsiExpression qualifier = refExpr.getQualifierExpression();
      if (qualifier != null) {
        final Project project = qualifier.getProject();
        PsiType type = null;
        final PairFunction<PsiExpression, CompletionParameters, PsiType> evaluator = refExpr.getContainingFile().getCopyableUserData(DYNAMIC_TYPE_EVALUATOR);
        if (evaluator != null) {
          type = evaluator.fun(qualifier, parameters);
        }
        if (type == null) {
          type = GuessManager.getInstance(project).getControlFlowExpressionType(qualifier);
        }
        return type;
      }
    }
    return null;
  }

  @NotNull
  private static LookupElement castQualifier(@NotNull LookupElement item,
                                             @Nullable final PsiTypeLookupItem castTypeItem,
                                             @Nullable PsiType plainQualifier, JavaCompletionProcessor processor) {
    if (castTypeItem == null) {
      return item;
    }
    if (plainQualifier != null) {
      Object o = item.getObject();
      if (o instanceof PsiMethod) {
        PsiType castType = castTypeItem.getPsiType();
        if (plainQualifier instanceof PsiClassType && castType instanceof PsiClassType) {
          PsiMethod method = (PsiMethod)o;
          PsiClassType.ClassResolveResult plainResult = ((PsiClassType)plainQualifier).resolveGenerics();
          PsiClass plainClass = plainResult.getElement();
          if (plainClass != null && plainClass.findMethodBySignature(method, true) != null) {
            PsiClass castClass = ((PsiClassType)castType).resolveGenerics().getElement();

            if (castClass == null || !castClass.isInheritor(plainClass, true)) {
              return item;
            }

            PsiSubstitutor plainSub = plainResult.getSubstitutor();
            PsiSubstitutor castSub = TypeConversionUtil.getSuperClassSubstitutor(plainClass, (PsiClassType)castType);
            PsiType returnType = method.getReturnType();
            if (method.getSignature(plainSub).equals(method.getSignature(castSub))) {
              PsiType typeAfterCast = toRaw(castSub.substitute(returnType));
              PsiType typeDeclared = toRaw(plainSub.substitute(returnType));
              if (typeAfterCast != null && typeDeclared != null && 
                  typeAfterCast.isAssignableFrom(typeDeclared) &&
                  processor.isAccessible(plainClass.findMethodBySignature(method, true))
                ) {
                return item;
              }
            }
          }
        }
      } else if (containsMember(plainQualifier, o)) {
        return item;
      }
    }

    return LookupElementDecorator.withInsertHandler(item, new InsertHandlerDecorator<LookupElement>() {
      @Override
      public void handleInsert(InsertionContext context, LookupElementDecorator<LookupElement> item) {
        final Document document = context.getEditor().getDocument();
        context.commitDocument();
        final PsiFile file = context.getFile();
        final PsiJavaCodeReferenceElement ref =
          PsiTreeUtil.findElementOfClassAtOffset(file, context.getStartOffset(), PsiJavaCodeReferenceElement.class, false);
        if (ref != null) {
          final PsiElement qualifier = ref.getQualifier();
          if (qualifier != null) {
            final CommonCodeStyleSettings settings = context.getCodeStyleSettings();

            final String parenSpace = settings.SPACE_WITHIN_PARENTHESES ? " " : "";
            document.insertString(qualifier.getTextRange().getEndOffset(), parenSpace + ")");

            final String spaceWithin = settings.SPACE_WITHIN_CAST_PARENTHESES ? " " : "";
            final String prefix = "(" + parenSpace + "(" + spaceWithin;
            final String spaceAfter = settings.SPACE_AFTER_TYPE_CAST ? " " : "";
            final int exprStart = qualifier.getTextRange().getStartOffset();
            document.insertString(exprStart, prefix + spaceWithin + ")" + spaceAfter);

            CompletionUtil.emulateInsertion(context, exprStart + prefix.length(), castTypeItem);
            PsiDocumentManager.getInstance(file.getProject()).doPostponedOperationsAndUnblockDocument(document);
            context.getEditor().getCaretModel().moveToOffset(context.getTailOffset());
          }
        }

        item.getDelegate().handleInsert(context);
      }
    });
  }
  
  @Nullable 
  private static PsiType toRaw(@Nullable PsiType type) {
    return type instanceof PsiClassType ? ((PsiClassType)type).rawType() : type;
  }

  @NotNull
  public static LookupElement highlightIfNeeded(@Nullable PsiType qualifierType,
                                                @NotNull LookupElement item,
                                                @NotNull Object object,
                                                @NotNull PsiElement place) {
    if (object instanceof PsiMember &&
        Java15APIUsageInspectionBase.isForbiddenApiUsage((PsiMember)object, PsiUtil.getLanguageLevel(place))) {
      return LookupElementDecorator.withRenderer(item, new LookupElementRenderer<LookupElementDecorator<LookupElement>>() {
        @Override
        public void renderElement(LookupElementDecorator<LookupElement> element, LookupElementPresentation presentation) {
          element.getDelegate().renderElement(presentation);
          presentation.setItemTextForeground(JBColor.RED);
        }
      });
    }
    if (containsMember(qualifierType, object)) {
      LookupElementRenderer<LookupElementDecorator<LookupElement>> boldRenderer =
        new LookupElementRenderer<LookupElementDecorator<LookupElement>>() {
          @Override
          public void renderElement(LookupElementDecorator<LookupElement> element, LookupElementPresentation presentation) {
            element.getDelegate().renderElement(presentation);
            presentation.setItemTextBold(true);
          }
        };
      return PrioritizedLookupElement.withExplicitProximity(LookupElementDecorator.withRenderer(item, boldRenderer), 1);
    }
    return item;
  }

  public static boolean containsMember(@Nullable PsiType qualifierType, @NotNull Object object) {
    if (qualifierType instanceof PsiArrayType && object instanceof PsiMember) { //length and clone()
      PsiFile file = ((PsiMember)object).getContainingFile();
      if (file == null || file.getVirtualFile() == null) { //yes, they're a bit dummy
        return true;
      }
    }
    else if (qualifierType instanceof PsiClassType) {
      PsiClass qualifierClass = ((PsiClassType)qualifierType).resolve();
      if (qualifierClass == null) return false;
      if (object instanceof PsiMethod && qualifierClass.findMethodBySignature((PsiMethod)object, false) != null) {
        return true;
      }
      if (object instanceof PsiMember) {
        return qualifierClass.equals(((PsiMember)object).getContainingClass());
      }
    }
    return false;
  }

  private static LookupElement highlight(LookupElement decorator) {
    return PrioritizedLookupElement.withExplicitProximity(
      LookupElementDecorator.withRenderer(decorator, new LookupElementRenderer<LookupElementDecorator<LookupElement>>() {
        @Override
        public void renderElement(LookupElementDecorator<LookupElement> element, LookupElementPresentation presentation) {
          element.getDelegate().renderElement(presentation);
          presentation.setItemTextBold(true);
        }
      }), 1);
  }

  private static List<? extends LookupElement> createLookupElements(CompletionElement completionElement, PsiJavaReference reference) {
    Object completion = completionElement.getElement();
    assert !(completion instanceof LookupElement);

    if (reference instanceof PsiJavaCodeReferenceElement) {
      if (completion instanceof PsiMethod &&
          ((PsiJavaCodeReferenceElement)reference).getParent() instanceof PsiImportStaticStatement) {
        return Arrays.asList(JavaLookupElementBuilder.forMethod((PsiMethod)completion, PsiSubstitutor.EMPTY));
      }

      if (completion instanceof PsiClass) {
        return JavaClassNameCompletionContributor.createClassLookupItems((PsiClass)completion,
                                                                         JavaClassNameCompletionContributor.AFTER_NEW.accepts(reference),
                                                                         JavaClassNameInsertHandler.JAVA_CLASS_INSERT_HANDLER,
                                                                         Conditions.<PsiClass>alwaysTrue());
      }
    }
    
    if (reference instanceof PsiMethodReferenceExpression && completion instanceof PsiMethod && ((PsiMethod)completion).isConstructor()) {
      return Arrays.asList(JavaLookupElementBuilder.forMethod((PsiMethod)completion, "new", PsiSubstitutor.EMPTY, null));
    }

    LookupElement _ret = LookupItemUtil.objectToLookupItem(completion);
    if (_ret == null || !(_ret instanceof LookupItem)) return Collections.emptyList();

    final PsiSubstitutor substitutor = completionElement.getSubstitutor();
    if (substitutor != null) {
      ((LookupItem<?>)_ret).setAttribute(LookupItem.SUBSTITUTOR, substitutor);
    }

    return Arrays.asList(_ret);
  }

  public static boolean hasAccessibleConstructor(PsiType type) {
    if (type instanceof PsiArrayType) return true;

    final PsiClass psiClass = PsiUtil.resolveClassInType(type);
    if (psiClass == null || psiClass.isEnum() || psiClass.isAnnotationType()) return false;

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
    Set<String> allLookupStrings = ContainerUtil.newLinkedHashSet();
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
    ret.setAttribute(JavaPsiClassReferenceElement.PACKAGE_NAME, PsiFormatUtil.getPackageDisplayName((PsiClass)ret.getObject()));
    return ret;
  }

  public static boolean mayHaveSideEffects(@Nullable final PsiElement element) {
    return element instanceof PsiExpression && SideEffectChecker.mayHaveSideEffects((PsiExpression)element);
  }

  public static void insertClassReference(@NotNull PsiClass psiClass, @NotNull PsiFile file, int offset) {
    insertClassReference(psiClass, file, offset, offset);
  }

  public static int insertClassReference(PsiClass psiClass, PsiFile file, int startOffset, int endOffset) {
    final Project project = file.getProject();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    documentManager.commitAllDocuments();

    final PsiManager manager = file.getManager();

    final Document document = FileDocumentManager.getInstance().getDocument(file.getViewProvider().getVirtualFile());

    final PsiReference reference = file.findReferenceAt(startOffset);
    if (reference != null) {
      final PsiElement resolved = reference.resolve();
      if (resolved instanceof PsiClass) {
        if (((PsiClass)resolved).getQualifiedName() == null || manager.areElementsEquivalent(psiClass, resolved)) {
          return endOffset;
        }
      }
    }

    String name = psiClass.getName();
    if (name == null) {
      return endOffset;
    }

    assert document != null;
    document.replaceString(startOffset, endOffset, name);

    int newEndOffset = startOffset + name.length();
    final RangeMarker toDelete = insertTemporary(newEndOffset, document, " ");

    documentManager.commitAllDocuments();

    PsiElement element = file.findElementAt(startOffset);
    if (element instanceof PsiIdentifier) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement &&
          !((PsiJavaCodeReferenceElement)parent).isQualified() &&
          !(parent.getParent() instanceof PsiPackageStatement)) {
        PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)parent;

        if (psiClass.isValid() && !psiClass.getManager().areElementsEquivalent(psiClass, resolveReference(ref))) {
          final boolean staticImport = ref instanceof PsiImportStaticReferenceElement;
          PsiElement newElement = staticImport
                                  ? ((PsiImportStaticReferenceElement)ref).bindToTargetClass(psiClass)
                                  : ref.bindToElement(psiClass);

          final RangeMarker rangeMarker = document.createRangeMarker(newElement.getTextRange());
          documentManager.doPostponedOperationsAndUnblockDocument(document);
          documentManager.commitDocument(document);

          newElement = CodeInsightUtilCore.findElementInRange(file, rangeMarker.getStartOffset(), rangeMarker.getEndOffset(),
                                                              PsiJavaCodeReferenceElement.class,
                                                              JavaLanguage.INSTANCE);
          rangeMarker.dispose();
          if (newElement != null) {
            newEndOffset = newElement.getTextRange().getEndOffset();
            if (!(newElement instanceof PsiReferenceExpression)) {
              PsiReferenceParameterList parameterList = ((PsiJavaCodeReferenceElement)newElement).getParameterList();
              if (parameterList != null) {
                newEndOffset = parameterList.getTextRange().getStartOffset();
              }
            }

            if (!staticImport &&
                !psiClass.getManager().areElementsEquivalent(psiClass, resolveReference((PsiReference)newElement)) &&
                !PsiUtil.isInnerClass(psiClass)) {
              final String qName = psiClass.getQualifiedName();
              if (qName != null) {
                document.replaceString(newElement.getTextRange().getStartOffset(), newEndOffset, qName);
                newEndOffset = newElement.getTextRange().getStartOffset() + qName.length();
              }
            }
          }
        }
      }
    }

    if (toDelete.isValid()) {
      document.deleteString(toDelete.getStartOffset(), toDelete.getEndOffset());
    }

    return newEndOffset;
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

    final TailType tailType = completionChar == '(' ? TailType.NONE :
                              completionChar == ':' ? TailType.COND_EXPR_COLON :
                              LookupItem.handleCompletionChar(context.getEditor(), item, completionChar);
    final boolean hasTail = tailType != TailType.NONE && tailType != TailType.UNKNOWN;
    final boolean smart = completionChar == Lookup.COMPLETE_STATEMENT_SELECT_CHAR;

    if (completionChar == '(' || completionChar == '.' || completionChar == ',' || completionChar == ';' || completionChar == ':' || completionChar == ' ') {
      context.setAddCompletionChar(false);
    }

    if (hasTail) {
      hasParams = false;
    }
    final boolean needRightParenth = forceClosingParenthesis ||
                                     !smart && (CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET ||
                                                !hasParams && completionChar != '(');

    context.commitDocument();

    final CommonCodeStyleSettings styleSettings = context.getCodeStyleSettings();
    final PsiElement elementAt = file.findElementAt(context.getStartOffset());
    if (elementAt == null || !(elementAt.getParent() instanceof PsiMethodReferenceExpression)) {
      ParenthesesInsertHandler.getInstance(hasParams,
                                           styleSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES,
                                           styleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES && hasParams,
                                           needRightParenth,
                                           styleSettings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE
      ).handleInsert(context, item);
    }

    if (hasParams) {
      // Invoke parameters popup
      AutoPopupController.getInstance(file.getProject()).autoPopupParameterInfo(editor, overloadsMatter ? null : (PsiElement)item.getObject());
    }

    if (smart || !needRightParenth || !insertTail(context, item, tailType, hasTail)) {
      return;
    }

    if (completionChar == '.') {
      AutoPopupController.getInstance(file.getProject()).autoPopupMemberLookup(context.getEditor(), null);
    } else if (completionChar == ',') {
      AutoPopupController.getInstance(file.getProject()).autoPopupParameterInfo(context.getEditor(), null);
    }
  }

  public static boolean insertTail(InsertionContext context, LookupElement item, TailType tailType, boolean hasTail) {
    TailType toInsert = tailType;
    LookupItem<?> lookupItem = item.as(LookupItem.CLASS_CONDITION_KEY);
    if (lookupItem == null || lookupItem.getAttribute(LookupItem.TAIL_TYPE_ATTR) != TailType.UNKNOWN) {
      if (!hasTail && item.getObject() instanceof PsiMethod && ((PsiMethod)item.getObject()).getReturnType() == PsiType.VOID) {
        PsiDocumentManager.getInstance(context.getProject()).commitAllDocuments();
        if (psiElement().beforeLeaf(psiElement().withText(".")).accepts(context.getFile().findElementAt(context.getTailOffset() - 1))) {
          return false;
        }

        boolean insertAdditionalSemicolon = true;
        final PsiReferenceExpression referenceExpression = PsiTreeUtil.getTopmostParentOfType(context.getFile().findElementAt(context.getStartOffset()), PsiReferenceExpression.class);
        if (referenceExpression instanceof PsiMethodReferenceExpression && LambdaHighlightingUtil
          .insertSemicolon(referenceExpression.getParent())) {
          insertAdditionalSemicolon = false;
        } else if (referenceExpression != null) {
          PsiElement parent = referenceExpression.getParent();
          if (parent instanceof PsiMethodCallExpression) {
            parent = parent.getParent();
          }
          if (parent instanceof PsiLambdaExpression && !LambdaHighlightingUtil.insertSemicolonAfter((PsiLambdaExpression)parent)) {
            insertAdditionalSemicolon = false;
          }
        }
        if (insertAdditionalSemicolon) {
          toInsert = TailType.SEMICOLON;
        }

      }
    }
    toInsert.processTail(context.getEditor(), context.getTailOffset());
    return true;
  }

  //need to shorten references in type argument list
  public static void shortenReference(final PsiFile file, final int offset) throws IncorrectOperationException {
    Project project = file.getProject();
    final PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
    Document document = manager.getDocument(file);
    manager.commitDocument(document);
    final PsiReference ref = file.findReferenceAt(offset);
    if (ref != null) {
      PsiElement element = ref.getElement();
      if (element != null) {
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(element);
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
      }
    }
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
      PsiClass topLevel = PsiUtil.getTopLevelClass(psiClass);
      if (topLevel != null) {
        String fqName = topLevel.getQualifiedName();
        if (fqName != null && StringUtil.isEmpty(StringUtil.getPackageName(fqName))) {
          return false;
        }
      }
    }

    return true;
  }

  public static boolean promptTypeArgs(InsertionContext context, int offset) {
    if (offset < 0) {
      return false;
    }

    OffsetKey key = context.trackOffset(offset, false);
    PostprocessReformattingAspect.getInstance(context.getProject()).doPostponedFormatting();
    offset = context.getOffset(key);
    if (offset < 0) {
      return false;
    }

    String open = escapeXmlIfNeeded(context, "<");
    context.getDocument().insertString(offset, open);
    context.getEditor().getCaretModel().moveToOffset(offset + open.length());
    context.getDocument().insertString(offset + open.length(), escapeXmlIfNeeded(context, ">"));
    context.setAddCompletionChar(false);
    return true;
  }

  public static FakePsiElement createContextWithXxxVariable(final PsiElement place, final PsiType varType) {
    return new FakePsiElement() {
      @Override
      public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                         @NotNull ResolveState state,
                                         PsiElement lastParent,
                                         @NotNull PsiElement place) {
        return processor.execute(new LightVariableBuilder("xxx", varType, place), ResolveState.initial());
      }

      @Override
      public PsiElement getParent() {
        return place;
      }
    };
  }

  public static String escapeXmlIfNeeded(InsertionContext context, String generics) {
    if (context.getFile().getViewProvider().getBaseLanguage() == StdLanguages.JSPX) {
      return StringUtil.escapeXml(generics);
    }
    return generics;
  }
}
