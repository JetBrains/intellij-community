// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.impl.light.LightVariableBuilder;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.*;
import com.intellij.psi.util.proximity.ReferenceListWeigher;
import com.intellij.ui.JBColor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PairFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.siyeh.ig.psiutils.SideEffectChecker;
import gnu.trove.THashSet;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInsight.completion.ReferenceExpressionCompletionContributor.findConstantsUsedInSwitch;
import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.psi.util.proximity.ReferenceListWeigher.ReferenceListApplicability.inapplicable;

public class JavaCompletionUtil {
  public static final Key<Boolean> FORCE_SHOW_SIGNATURE_ATTR = Key.create("forceShowSignature");
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.JavaCompletionUtil");
  public static final Key<PairFunction<PsiExpression, CompletionParameters, PsiType>> DYNAMIC_TYPE_EVALUATOR = Key.create("DYNAMIC_TYPE_EVALUATOR");

  private static final Key<PsiType> QUALIFIER_TYPE_ATTR = Key.create("qualifierType"); // SmartPsiElementPointer to PsiType of "qualifier"
  static final NullableLazyKey<ExpectedTypeInfo[], CompletionLocation> EXPECTED_TYPES = NullableLazyKey.create("expectedTypes",
                                                                                                               location -> {
                                                                                                                 if (PsiJavaPatterns.psiElement().beforeLeaf(PsiJavaPatterns.psiElement().withText("."))
                                                                                                                   .accepts(location.getCompletionParameters().getPosition())) {
                                                                                                                   return ExpectedTypeInfo.EMPTY_ARRAY;
                                                                                                                 }

                                                                                                                 return JavaSmartCompletionContributor.getExpectedTypes(location.getCompletionParameters());
                                                                                                               });

  public static final Key<Boolean> SUPER_METHOD_PARAMETERS = Key.create("SUPER_METHOD_PARAMETERS");

  @Nullable
  public static Set<PsiType> getExpectedTypes(final CompletionParameters parameters) {
    final PsiExpression expr = PsiTreeUtil.getContextOfType(parameters.getPosition(), PsiExpression.class, true);
    if (expr != null) {
      final Set<PsiType> set = new THashSet<>();
      for (final ExpectedTypeInfo expectedInfo : JavaSmartCompletionContributor.getExpectedTypes(parameters)) {
        set.add(expectedInfo.getType());
      }
      return set;
    }
    return null;
  }

  private static final Key<List<SmartPsiElementPointer<PsiMethod>>> ALL_METHODS_ATTRIBUTE = Key.create("allMethods");

  public static PsiType getQualifierType(LookupElement item) {
    return item.getUserData(QUALIFIER_TYPE_ATTR);
  }

  public static void completeVariableNameForRefactoring(Project project, Set<LookupElement> set, String prefix, PsiType varType, VariableKind varKind) {
    final CamelHumpMatcher camelHumpMatcher = new CamelHumpMatcher(prefix);
    JavaMemberNameCompletionContributor.completeVariableNameForRefactoring(project, set, camelHumpMatcher, varType, varKind, true, false);
  }

  public static void putAllMethods(LookupElement item, List<PsiMethod> methods) {
    item.putUserData(ALL_METHODS_ATTRIBUTE, ContainerUtil.map(methods, method -> SmartPointerManager.getInstance(method.getProject()).createSmartPsiElementPointer(method)));
  }

  public static List<PsiMethod> getAllMethods(LookupElement item) {
    List<SmartPsiElementPointer<PsiMethod>> pointers = item.getUserData(ALL_METHODS_ATTRIBUTE);
    if (pointers == null) return null;

    return ContainerUtil.mapNotNull(pointers, pointer -> pointer.getElement());
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

    return JavaProjectCodeInsightSettings.getSettings(member.getProject()).isExcluded(name);
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

  @Nullable
  public static List<? extends PsiElement> getAllPsiElements(final LookupElement item) {
    List<PsiMethod> allMethods = getAllMethods(item);
    if (allMethods != null) return allMethods;
    if (item.getObject() instanceof PsiElement) return Collections.singletonList((PsiElement)item.getObject());
    return null;
  }

  @Nullable
  public static PsiType getLookupElementType(final LookupElement element) {
    TypedLookupItem typed = element.as(TypedLookupItem.CLASS_CONDITION_KEY);
    return typed != null ? typed.getType() : null;
  }

  @Nullable
  public static PsiType getQualifiedMemberReferenceType(@Nullable PsiType qualifierType, @NotNull final PsiMember member) {
    final Ref<PsiSubstitutor> subst = Ref.create(PsiSubstitutor.EMPTY);
    class MyProcessor implements PsiScopeProcessor, NameHint, ElementClassHint {
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

  public static Set<LookupElement> processJavaReference(final PsiElement element, 
                                                        final PsiJavaReference javaReference, 
                                                        final ElementFilter elementFilter,
                                                        final JavaCompletionProcessor.Options options,
                                                        final PrefixMatcher matcher, 
                                                        final CompletionParameters parameters) {
    PsiElement elementParent = element.getContext();
    if (elementParent instanceof PsiReferenceExpression) {
      final PsiExpression qualifierExpression = ((PsiReferenceExpression)elementParent).getQualifierExpression();
      if (qualifierExpression instanceof PsiReferenceExpression) {
        final PsiElement resolve = ((PsiReferenceExpression)qualifierExpression).resolve();
        if (resolve instanceof PsiParameter) {
          final PsiElement declarationScope = ((PsiParameter)resolve).getDeclarationScope();
          if (((PsiParameter)resolve).getType() instanceof PsiLambdaParameterType) {
            final PsiLambdaExpression lambdaExpression = (PsiLambdaExpression)declarationScope;
            if (PsiTypesUtil.getExpectedTypeByParent(lambdaExpression) == null) {
              final int parameterIndex = lambdaExpression.getParameterList().getParameterIndex((PsiParameter)resolve);
              final Set<LookupElement> set = new LinkedHashSet<>();
              final boolean overloadsFound = LambdaUtil.processParentOverloads(lambdaExpression, functionalInterfaceType -> {
                PsiType qualifierType = LambdaUtil.getLambdaParameterFromType(functionalInterfaceType, parameterIndex);
                if (qualifierType instanceof PsiWildcardType) {
                  qualifierType = ((PsiWildcardType)qualifierType).getBound();
                }
                if (qualifierType == null) return;

                PsiReferenceExpression fakeRef = createReference("xxx.xxx", createContextWithXxxVariable(element, qualifierType));
                set.addAll(processJavaQualifiedReference(fakeRef.getReferenceNameElement(), fakeRef, elementFilter, options, matcher, parameters));
              });
              if (overloadsFound) return set;
            }
          }
        }
      }
    }
    return processJavaQualifiedReference(element, javaReference, elementFilter, options, matcher, parameters);
  }

  private static Set<LookupElement> processJavaQualifiedReference(PsiElement element, PsiJavaReference javaReference, ElementFilter elementFilter,
                                                        JavaCompletionProcessor.Options options,
                                                        final PrefixMatcher matcher, CompletionParameters parameters) {
    final Set<LookupElement> set = new LinkedHashSet<>();
    final Condition<String> nameCondition = matcher::prefixMatches;

    final JavaCompletionProcessor processor = new JavaCompletionProcessor(element, elementFilter, options, nameCondition);
    final PsiType plainQualifier = processor.getQualifierType();

    List<PsiType> runtimeQualifiers = getQualifierCastTypes(javaReference, parameters);
    if (!runtimeQualifiers.isEmpty()) {
      PsiType composite = PsiIntersectionType.createIntersection(JBIterable.of(plainQualifier).append(runtimeQualifiers).toList());
      PsiElement ctx = createContextWithXxxVariable(element, composite);
      javaReference = createReference("xxx.xxx", ctx);
      processor.setQualifierType(composite);
    }

    javaReference.processVariants(processor);

    List<PsiTypeLookupItem> castItems = ContainerUtil.map(runtimeQualifiers, q -> PsiTypeLookupItem.createLookupItem(q, element));

    final boolean pkgContext = inSomePackage(element);

    PsiClass qualifierClass = PsiUtil.resolveClassInClassTypeOnly(plainQualifier);
    final boolean honorExcludes = qualifierClass == null || !isInExcludedPackage(qualifierClass, false);

    final Set<PsiMember> mentioned = new THashSet<>();
    for (CompletionElement completionElement : processor.getResults()) {
      for (LookupElement item : createLookupElements(completionElement, javaReference)) {
        item.putUserData(QUALIFIER_TYPE_ATTR, plainQualifier);
        final Object o = item.getObject();
        if (o instanceof PsiClass && !isSourceLevelAccessible(element, (PsiClass)o, pkgContext)) {
          continue;
        }
        if (o instanceof PsiMember) {
          if (honorExcludes && isInExcludedPackage((PsiMember)o, true)) {
            continue;
          }
          mentioned.add(CompletionUtil.getOriginalOrSelf((PsiMember)o));
        }
        PsiTypeLookupItem qualifierCast = findQualifierCast(item, castItems, plainQualifier, processor);
        if (qualifierCast != null) item = castQualifier(item, qualifierCast);
        set.add(highlightIfNeeded(qualifierCast != null ? qualifierCast.getType() : plainQualifier, item, o, element));
      }
    }

    if (javaReference instanceof PsiJavaCodeReferenceElement) {
      PsiElement refQualifier = ((PsiJavaCodeReferenceElement)javaReference).getQualifier();
      if (refQualifier == null && PsiTreeUtil.getParentOfType(element, PsiPackageStatement.class) == null) {
        final StaticMemberProcessor memberProcessor = new JavaStaticMemberProcessor(parameters);
        memberProcessor.processMembersOfRegisteredClasses(matcher, (member, psiClass) -> {
          if (!mentioned.contains(member) && processor.satisfies(member, ResolveState.initial())) {
            ContainerUtil.addIfNotNull(set, memberProcessor.createLookupElement(member, psiClass, true));
          }
        });
      }
      else if (refQualifier instanceof PsiSuperExpression && ((PsiSuperExpression)refQualifier).getQualifier() == null) {
        set.addAll(SuperCalls.suggestQualifyingSuperCalls(element, javaReference, elementFilter, options, nameCondition));
      }
    }

    return set;
  }

  @NotNull
  static PsiReferenceExpression createReference(@NotNull String text, @NotNull PsiElement context) {
    return (PsiReferenceExpression) JavaPsiFacade.getElementFactory(context.getProject()).createExpressionFromText(text, context);
  }

  @NotNull
  private static List<PsiType> getQualifierCastTypes(PsiJavaReference javaReference, CompletionParameters parameters) {
    if (javaReference instanceof PsiReferenceExpression) {
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)javaReference;
      final PsiExpression qualifier = refExpr.getQualifierExpression();
      if (qualifier != null) {
        final Project project = qualifier.getProject();
        PairFunction<PsiExpression, CompletionParameters, PsiType> evaluator = refExpr.getContainingFile().getCopyableUserData(DYNAMIC_TYPE_EVALUATOR);
        if (evaluator != null) {
          PsiType type = evaluator.fun(qualifier, parameters);
          if (type != null) {
            return Collections.singletonList(type);
          }
        }
        
        return GuessManager.getInstance(project).getControlFlowExpressionTypeConjuncts(qualifier);
      }
    }
    return Collections.emptyList();
  }

  private static boolean shouldCast(@NotNull LookupElement item,
                                    @NotNull PsiTypeLookupItem castTypeItem,
                                    @Nullable PsiType plainQualifier, JavaCompletionProcessor processor) {
    PsiType castType = castTypeItem.getType();
    if (plainQualifier != null) {
      Object o = item.getObject();
      if (o instanceof PsiMethod) {
        if (plainQualifier instanceof PsiClassType && castType instanceof PsiClassType) {
          PsiMethod method = (PsiMethod)o;
          PsiClassType.ClassResolveResult plainResult = ((PsiClassType)plainQualifier).resolveGenerics();
          PsiClass plainClass = plainResult.getElement();
          HierarchicalMethodSignature signature = method.getHierarchicalMethodSignature();
          if (plainClass != null && StreamEx.of(signature.getSuperSignatures()).prepend(signature)
            .anyMatch(sig -> MethodSignatureUtil.findMethodBySignature(plainClass, sig, true) != null)) {
            PsiClass castClass = ((PsiClassType)castType).resolveGenerics().getElement();

            if (castClass == null || !castClass.isInheritor(plainClass, true)) {
              return false;
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
                return false;
              }
            }
            return true;
          }
        }
      }
      
      return containsMember(castType, o, true) && !containsMember(plainQualifier, o, true);
    }
    return false;
  }

  @NotNull
  private static LookupElement castQualifier(@NotNull LookupElement item, @NotNull PsiTypeLookupItem castTypeItem) {
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

  private static PsiTypeLookupItem findQualifierCast(@NotNull LookupElement item,
                                                     @NotNull List<PsiTypeLookupItem> castTypeItems,
                                                     @Nullable PsiType plainQualifier, JavaCompletionProcessor processor) {
    return ContainerUtil.find(castTypeItems, c -> shouldCast(item, c, plainQualifier, processor));
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
    if (shouldMarkRed(object, place)) {
      return PrioritizedLookupElement.withExplicitProximity(LookupElementDecorator.withRenderer(item, new LookupElementRenderer<LookupElementDecorator<LookupElement>>() {
        @Override
        public void renderElement(LookupElementDecorator<LookupElement> element, LookupElementPresentation presentation) {
          element.getDelegate().renderElement(presentation);
          presentation.setItemTextForeground(JBColor.RED);
        }
      }), -1);
    }
    if (containsMember(qualifierType, object, false) && !qualifierType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
      LookupElementDecorator<LookupElement> bold = LookupElementDecorator.withRenderer(item, new LookupElementRenderer<LookupElementDecorator<LookupElement>>() {
        @Override
        public void renderElement(LookupElementDecorator<LookupElement> element, LookupElementPresentation presentation) {
          element.getDelegate().renderElement(presentation);
          presentation.setItemTextBold(true);
        }
      });
      return object instanceof PsiField ? bold : PrioritizedLookupElement.withExplicitProximity(bold, 1);
    }
    return item;
  }

  private static boolean shouldMarkRed(@NotNull Object object, @NotNull PsiElement place) {
    if (!(object instanceof PsiMember)) return false;
    if (Java15APIUsageInspectionBase.getLastIncompatibleLanguageLevel((PsiMember)object, PsiUtil.getLanguageLevel(place)) != null) return true;

    if (object instanceof PsiEnumConstant) {
      return findConstantsUsedInSwitch(place).contains(CompletionUtil.getOriginalOrSelf((PsiEnumConstant)object));
    }
    if (object instanceof PsiClass && ReferenceListWeigher.INSTANCE.getApplicability((PsiClass)object, place) == inapplicable) {
      return true;
    }
    return false;
  }

  @Contract("null, _, _ -> false")
  private static boolean containsMember(@Nullable PsiType qualifierType, @NotNull Object object, boolean checkBases) {
    if (!(object instanceof PsiMember)) return false;

    if (qualifierType instanceof PsiArrayType) { //length and clone()
      PsiFile file = ((PsiMember)object).getContainingFile();
      if (file == null || file.getVirtualFile() == null) { //yes, they're a bit dummy
        return true;
      }
    }
    else if (qualifierType instanceof PsiClassType) {
      PsiClass qualifierClass = ((PsiClassType)qualifierType).resolve();
      if (qualifierClass == null) return false;
      if (object instanceof PsiMethod && qualifierClass.findMethodBySignature((PsiMethod)object, checkBases) != null) {
        return true;
      }
      PsiClass memberClass = ((PsiMember)object).getContainingClass();
      return checkBases ? InheritanceUtil.isInheritorOrSelf(qualifierClass, memberClass, true) : qualifierClass.equals(memberClass);
    }
    return false;
  }

  static Iterable<? extends LookupElement> createLookupElements(CompletionElement completionElement, PsiJavaReference reference) {
    Object completion = completionElement.getElement();
    assert !(completion instanceof LookupElement);

    if (reference instanceof PsiJavaCodeReferenceElement) {
      if (completion instanceof PsiMethod &&
          ((PsiJavaCodeReferenceElement)reference).getParent() instanceof PsiImportStaticStatement) {
        return Collections.singletonList(JavaLookupElementBuilder.forMethod((PsiMethod)completion, PsiSubstitutor.EMPTY));
      }

      if (completion instanceof PsiClass) {
        List<JavaPsiClassReferenceElement> classItems = JavaClassNameCompletionContributor.createClassLookupItems((PsiClass)completion,
                                                                                                             JavaClassNameCompletionContributor.AFTER_NEW
                                                                                                               .accepts(reference),
                                                                                                             JavaClassNameInsertHandler.JAVA_CLASS_INSERT_HANDLER,
                                                                                                             Conditions.alwaysTrue());
        return JBIterable.from(classItems).flatMap(i -> JavaConstructorCallElement.wrap(i, reference.getElement()));
      }
    }
    
    if (reference instanceof PsiMethodReferenceExpression && completion instanceof PsiMethod && ((PsiMethod)completion).isConstructor()) {
      return Collections.singletonList(JavaLookupElementBuilder.forMethod((PsiMethod)completion, "new", PsiSubstitutor.EMPTY, null));
    }

    PsiSubstitutor substitutor = completionElement.getSubstitutor();
    if (substitutor == null) substitutor = PsiSubstitutor.EMPTY;
    if (completion instanceof PsiClass) {
      JavaPsiClassReferenceElement classItem =
        JavaClassNameCompletionContributor.createClassLookupItem((PsiClass)completion, true).setSubstitutor(substitutor);
      return JavaConstructorCallElement.wrap(classItem, reference.getElement());
    }
    if (completion instanceof PsiMethod) {
      JavaMethodCallElement item = new JavaMethodCallElement((PsiMethod)completion).setQualifierSubstitutor(substitutor);
      item.setForcedQualifier(completionElement.getQualifierText());
      return Collections.singletonList(item);
    }
    if (completion instanceof PsiVariable) {
      return Collections.singletonList(new VariableLookupItem((PsiVariable)completion).setSubstitutor(substitutor));
    }

    return Collections.singletonList(LookupItemUtil.objectToLookupItem(completion));
  }

  public static boolean hasAccessibleConstructor(PsiType type) {
    if (type instanceof PsiArrayType) return true;

    final PsiClass psiClass = PsiUtil.resolveClassInType(type);
    if (psiClass == null || psiClass.isEnum() || psiClass.isAnnotationType()) return false;

    PsiMethod[] methods = psiClass.getConstructors();
    return methods.length == 0 || Arrays.stream(methods).anyMatch(JavaCompletionUtil::isConstructorCompletable);
  }

  private static boolean isConstructorCompletable(@NotNull PsiMethod constructor) {
    return !(constructor instanceof PsiCompiledElement) || !constructor.hasModifierProperty(PsiModifier.PRIVATE);
  }

  public static LinkedHashSet<String> getAllLookupStrings(@NotNull PsiMember member) {
    LinkedHashSet<String> allLookupStrings = ContainerUtil.newLinkedHashSet();
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

    PsiReference reference = file.findReferenceAt(startOffset);
    if (reference != null && manager.areElementsEquivalent(psiClass, reference.resolve())) {
      return endOffset;
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
          PsiElement newElement;
          try {
            newElement = staticImport
                                    ? ((PsiImportStaticReferenceElement)ref).bindToTargetClass(psiClass)
                                    : ref.bindToElement(psiClass);
          }
          catch (IncorrectOperationException e) {
            return endOffset; // can happen if fqn contains reserved words, for example
          }

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

    if (toDelete != null && toDelete.isValid()) {
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

  @Nullable
  public static RangeMarker insertTemporary(int endOffset, Document document, String temporary) {
    final CharSequence chars = document.getCharsSequence();
    if (endOffset < chars.length() && Character.isJavaIdentifierPart(chars.charAt(endOffset))){
      document.insertString(endOffset, temporary);
      RangeMarker toDelete = document.createRangeMarker(endOffset, endOffset + 1);
      toDelete.setGreedyToLeft(true);
      toDelete.setGreedyToRight(true);
      return toDelete;
    }
    return null;
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
      final boolean hasParameters = hasParams;
      final boolean spaceBetweenParentheses = styleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES && hasParams;
      new ParenthesesInsertHandler<LookupElement>(styleSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES, spaceBetweenParentheses,
                                                  needRightParenth, styleSettings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE) {
        @Override
        protected boolean placeCaretInsideParentheses(InsertionContext context1, LookupElement item1) {
          return hasParameters;
        }

        @Override
        protected PsiElement findExistingLeftParenthesis(@NotNull InsertionContext context) {
          PsiElement token = super.findExistingLeftParenthesis(context);
          return isPartOfLambda(token) ? null : token;
        }

        private boolean isPartOfLambda(PsiElement token) {
          return token != null && token.getParent() instanceof PsiExpressionList &&
                 PsiUtilCore.getElementType(PsiTreeUtil.nextVisibleLeaf(token.getParent())) == JavaTokenType.ARROW;
        }
      }.handleInsert(context, item);
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
      if (!hasTail && item.getObject() instanceof PsiMethod && PsiType.VOID.equals(((PsiMethod)item.getObject()).getReturnType())) {
        PsiDocumentManager.getInstance(context.getProject()).commitAllDocuments();
        if (psiElement().beforeLeaf(psiElement().withText(".")).accepts(context.getFile().findElementAt(context.getTailOffset() - 1))) {
          return false;
        }

        boolean insertAdditionalSemicolon = true;
        PsiElement leaf = context.getFile().findElementAt(context.getStartOffset());
        PsiElement composite = leaf == null ? null : leaf.getParent();
        if (composite instanceof PsiMethodReferenceExpression && LambdaHighlightingUtil.insertSemicolon(composite.getParent())) {
          insertAdditionalSemicolon = false;
        }
        else if (composite instanceof PsiReferenceExpression) {
          PsiElement parent = composite.getParent();
          if (parent instanceof PsiMethodCallExpression) {
            parent = parent.getParent();
          }
          if (parent instanceof PsiLambdaExpression && !LambdaHighlightingUtil.insertSemicolonAfter((PsiLambdaExpression)parent)) {
            insertAdditionalSemicolon = false;
          }
          if (parent instanceof PsiMethodReferenceExpression && LambdaHighlightingUtil.insertSemicolon(parent.getParent())) {
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
    if (document == null) {
      PsiUtilCore.ensureValid(file);
      LOG.error("No document for " + file);
      return;
    }

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
    if (CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) {
      context.getDocument().insertString(offset + open.length(), escapeXmlIfNeeded(context, ">"));
    }
    if (context.getCompletionChar() != Lookup.COMPLETE_STATEMENT_SELECT_CHAR) {
      context.setAddCompletionChar(false);
    }
    return true;
  }

  public static FakePsiElement createContextWithXxxVariable(@NotNull PsiElement place, @NotNull PsiType varType) {
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

  public static boolean isEffectivelyDeprecated(PsiDocCommentOwner member) {
    if (member.isDeprecated()) {
      return true;
    }

    PsiClass aClass = member.getContainingClass();
    while (aClass != null) {
      if (aClass.isDeprecated()) {
        return true;
      }
      aClass = aClass.getContainingClass();
    }
    return false;
  }

  public static int findQualifiedNameStart(@NotNull InsertionContext context) {
    int start = context.getTailOffset() - 1;
    while (start >= 0) {
      char ch = context.getDocument().getCharsSequence().charAt(start);
      if (!Character.isJavaIdentifierPart(ch) && ch != '.') break;
      start--;
    }
    return start + 1;
  }
}