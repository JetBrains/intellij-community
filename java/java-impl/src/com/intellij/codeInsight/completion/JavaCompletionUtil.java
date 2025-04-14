// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.completion.scope.CompletionElement;
import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.codeInsight.completion.util.CompletionStyleUtil;
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler;
import com.intellij.codeInsight.editorActions.TabOutScopesTracker;
import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.codeInsight.lookup.*;
import com.intellij.java.codeserver.core.JavaPsiModuleUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.JdkApiCompatibilityCache;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
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
import com.intellij.psi.jsp.JspxLanguage;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.*;
import com.intellij.psi.util.proximity.ReferenceListWeigher;
import com.intellij.ui.JBColor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PairFunction;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.siyeh.ig.psiutils.SideEffectChecker;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.intellij.codeInsight.completion.ReferenceExpressionCompletionContributor.findConstantsUsedInSwitch;
import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.psi.util.proximity.ReferenceListWeigher.ReferenceListApplicability.inapplicable;

public final class JavaCompletionUtil {
  public static final Key<Boolean> FORCE_SHOW_SIGNATURE_ATTR = Key.create("forceShowSignature");
  private static final Logger LOG = Logger.getInstance(JavaCompletionUtil.class);
  public static final Key<PairFunction<PsiExpression, CompletionParameters, PsiType>> DYNAMIC_TYPE_EVALUATOR = Key.create("DYNAMIC_TYPE_EVALUATOR");

  private static final Key<PsiType> QUALIFIER_TYPE_ATTR = Key.create("qualifierType"); // SmartPsiElementPointer to PsiType of "qualifier"
  static final NullableLazyKey<ExpectedTypeInfo[], CompletionLocation> EXPECTED_TYPES = NullableLazyKey.create(
    "expectedTypes",
    location -> {
      if (PsiJavaPatterns.psiElement().beforeLeaf(PsiJavaPatterns.psiElement().withText("."))
        .accepts(location.getCompletionParameters().getPosition())) {
        return ExpectedTypeInfo.EMPTY_ARRAY;
      }

      return JavaSmartCompletionContributor.getExpectedTypes(location.getCompletionParameters());
    });

  public static final Key<Boolean> SUPER_METHOD_PARAMETERS = Key.create("SUPER_METHOD_PARAMETERS");

  public static @Nullable Set<PsiType> getExpectedTypes(@NotNull CompletionParameters parameters) {
    PsiExpression expr = PsiTreeUtil.getContextOfType(parameters.getPosition(), PsiExpression.class, true);
    if (expr != null) {
      Set<PsiType> set = new HashSet<>();
      for (ExpectedTypeInfo expectedInfo : JavaSmartCompletionContributor.getExpectedTypes(parameters)) {
        set.add(expectedInfo.getType());
      }
      return set;
    }
    return null;
  }

  private static final Key<List<SmartPsiElementPointer<PsiMethod>>> ALL_METHODS_ATTRIBUTE = Key.create("allMethods");

  public static PsiType getQualifierType(@NotNull LookupElement item) {
    return item.getUserData(QUALIFIER_TYPE_ATTR);
  }

  public static void completeVariableNameForRefactoring(Project project, Set<LookupElement> set, String prefix, PsiType varType, VariableKind varKind) {
    CamelHumpMatcher camelHumpMatcher = new CamelHumpMatcher(prefix);
    JavaMemberNameCompletionContributor.completeVariableNameForRefactoring(project, set, camelHumpMatcher, varType, varKind, true, false);
  }

  public static void putAllMethods(@NotNull LookupElement item, @NotNull List<? extends PsiMethod> methods) {
    item.putUserData(ALL_METHODS_ATTRIBUTE, ContainerUtil.map(methods, method -> SmartPointerManager.getInstance(method.getProject()).createSmartPsiElementPointer(method)));
  }

  public static @Unmodifiable List<PsiMethod> getAllMethods(@NotNull LookupElement item) {
    List<SmartPsiElementPointer<PsiMethod>> pointers = item.getUserData(ALL_METHODS_ATTRIBUTE);
    if (pointers == null) return null;

    return ContainerUtil.mapNotNull(pointers, SmartPsiElementPointer::getElement);
  }

  public static String @NotNull [] completeVariableNameForRefactoring(@NotNull JavaCodeStyleManager codeStyleManager,
                                                                      @Nullable PsiType varType,
                                                                      @NotNull VariableKind varKind,
                                                                      @NotNull SuggestedNameInfo suggestedNameInfo) {
    return JavaMemberNameCompletionContributor
      .completeVariableNameForRefactoring(codeStyleManager, new CamelHumpMatcher(""), varType, varKind, suggestedNameInfo, true, false);
  }

  public static boolean isInExcludedPackage(@NotNull PsiMember member, boolean allowInstanceInnerClasses) {
    String name = PsiUtil.getMemberQualifiedName(member);
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

  public static @NotNull <T extends PsiType> T originalize(@NotNull T type) {
    if (!type.isValid()) {
      return type;
    }

    T result = new PsiTypeMapper() {
      private final Set<PsiClassType> myVisited = new ReferenceOpenHashSet<>();

      @Override
      public PsiType visitClassType(@NotNull PsiClassType classType) {
        if (!myVisited.add(classType)) return classType;

        PsiClassType.ClassResolveResult classResolveResult = classType.resolveGenerics();
        PsiClass psiClass = classResolveResult.getElement();
        PsiSubstitutor substitutor = classResolveResult.getSubstitutor();
        if (psiClass == null) return classType;

        return new PsiImmediateClassType(CompletionUtil.getOriginalOrSelf(psiClass), originalizeSubstitutor(substitutor));
      }

      private @NotNull PsiSubstitutor originalizeSubstitutor(@NotNull PsiSubstitutor substitutor) {
        PsiSubstitutor originalSubstitutor = PsiSubstitutor.EMPTY;
        for (Map.Entry<PsiTypeParameter, PsiType> entry : substitutor.getSubstitutionMap().entrySet()) {
          PsiType value = entry.getValue();
          originalSubstitutor = originalSubstitutor.put(CompletionUtil.getOriginalOrSelf(entry.getKey()),
                                                        value == null ? null : mapType(value));
        }
        return originalSubstitutor;
      }

      @Override
      public PsiType visitType(@NotNull PsiType type) {
        return type;
      }
    }.mapType(type);
    if (result == null) {
      throw new AssertionError("Null result for type " + type + " of class " + type.getClass());
    }
    return result;
  }

  public static @Nullable List<? extends PsiElement> getAllPsiElements(@NotNull LookupElement item) {
    List<PsiMethod> allMethods = getAllMethods(item);
    if (allMethods != null) return allMethods;
    if (item.getObject() instanceof PsiElement) return Collections.singletonList((PsiElement)item.getObject());
    return null;
  }

  static @Nullable PsiType getLookupElementType(@NotNull LookupElement element) {
    TypedLookupItem typed = element.as(TypedLookupItem.CLASS_CONDITION_KEY);
    return typed != null ? typed.getType() : null;
  }

  static @Nullable PsiType getQualifiedMemberReferenceType(@Nullable PsiType qualifierType, @NotNull PsiMember member) {
    Ref<PsiSubstitutor> subst = Ref.create(PsiSubstitutor.EMPTY);
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
      public boolean shouldProcess(@NotNull DeclarationKind kind) {
        return member instanceof PsiEnumConstant ? kind == DeclarationKind.ENUM_CONST :
               member instanceof PsiField ? kind == DeclarationKind.FIELD :
               kind == DeclarationKind.METHOD;
      }

      @Override
      public <T> T getHint(@NotNull Key<T> hintKey) {
        //noinspection unchecked
        return hintKey == NameHint.KEY || hintKey == ElementClassHint.KEY ? (T)this : null;
      }
    }

    PsiScopesUtil.processTypeDeclarations(qualifierType, member, new MyProcessor());

    PsiType rawType = member instanceof PsiField ? ((PsiField)member).getType() :
                      member instanceof PsiMethod ? ((PsiMethod)member).getReturnType() :
                      JavaPsiFacade.getElementFactory(member.getProject()).createType((PsiClass)member);
    return subst.get().substitute(rawType);
  }

  static @NotNull Set<LookupElement> processJavaReference(@NotNull PsiElement element,
                                                          @NotNull PsiJavaCodeReferenceElement javaReference,
                                                          @NotNull ElementFilter elementFilter,
                                                          @NotNull JavaCompletionProcessor.Options options,
                                                          @NotNull Condition<? super String> nameCondition,
                                                          @NotNull CompletionParameters parameters) {
    PsiElement elementParent = element.getContext();
    if (elementParent instanceof PsiReferenceExpression) {
      PsiExpression qualifierExpression = ((PsiReferenceExpression)elementParent).getQualifierExpression();
      if (qualifierExpression instanceof PsiReferenceExpression) {
        PsiElement resolve = ((PsiReferenceExpression)qualifierExpression).resolve();
        if (resolve instanceof PsiParameter) {
          PsiElement declarationScope = ((PsiParameter)resolve).getDeclarationScope();
          if (((PsiParameter)resolve).getType() instanceof PsiLambdaParameterType) {
            PsiLambdaExpression lambdaExpression = (PsiLambdaExpression)declarationScope;
            if (PsiTypesUtil.getExpectedTypeByParent(lambdaExpression) == null) {
              int parameterIndex = lambdaExpression.getParameterList().getParameterIndex((PsiParameter)resolve);
              Set<LookupElement> set = new LinkedHashSet<>();
              boolean overloadsFound = LambdaUtil.processParentOverloads(lambdaExpression, functionalInterfaceType -> {
                PsiType qualifierType = LambdaUtil.getLambdaParameterFromType(functionalInterfaceType, parameterIndex);
                if (qualifierType instanceof PsiWildcardType) {
                  qualifierType = ((PsiWildcardType)qualifierType).getBound();
                }
                if (qualifierType == null) return;

                PsiReferenceExpression fakeRef = createReference("xxx.xxx", createContextWithXxxVariable(element, qualifierType));
                set.addAll(processJavaQualifiedReference(fakeRef.getReferenceNameElement(), fakeRef, elementFilter, options, nameCondition, parameters));
              });
              if (overloadsFound) return set;
            }
          }
        }
      }
    }
    return processJavaQualifiedReference(element, javaReference, elementFilter, options, nameCondition, parameters);
  }

  private static @NotNull Set<LookupElement> processJavaQualifiedReference(@NotNull PsiElement element,
                                                                           @NotNull PsiJavaCodeReferenceElement javaReference,
                                                                           @NotNull ElementFilter elementFilter,
                                                                           @NotNull JavaCompletionProcessor.Options options,
                                                                           @NotNull Condition<? super String> nameCondition,
                                                                           @NotNull CompletionParameters parameters) {
    Set<LookupElement> set = new LinkedHashSet<>();

    JavaCompletionProcessor processor = new JavaCompletionProcessor(element, elementFilter, options, nameCondition);
    PsiType plainQualifier = processor.getQualifierType();

    List<PsiType> runtimeQualifiers = getQualifierCastTypes(javaReference, parameters);
    if (!runtimeQualifiers.isEmpty()) {
      PsiType[] conjuncts = JBIterable.of(plainQualifier).append(runtimeQualifiers).toArray(PsiType.EMPTY_ARRAY);
      PsiType composite = PsiIntersectionType.createIntersection(false, conjuncts);
      PsiElement ctx = createContextWithXxxVariable(element, composite);
      javaReference = createReference("xxx.xxx", ctx);
      processor.setQualifierType(composite);
    }

    javaReference.processVariants(processor);

    List<PsiTypeLookupItem> castItems = ContainerUtil.map(runtimeQualifiers, q -> PsiTypeLookupItem.createLookupItem(q, element));

    boolean pkgContext = inSomePackage(element);

    PsiClass qualifierClass = PsiUtil.resolveClassInClassTypeOnly(plainQualifier);
    boolean honorExcludes = qualifierClass == null || !isInExcludedPackage(qualifierClass, false);

    Set<PsiType> expectedTypes = ObjectUtils.coalesce(getExpectedTypes(parameters), Collections.emptySet());

    Set<PsiMember> mentioned = new HashSet<>();
    JavaLookupElementHighlighter highlighter = getHighlighterForPlace(element, parameters.getOriginalFile().getVirtualFile());
    for (CompletionElement completionElement : processor.getResults()) {
      for (LookupElement item : createLookupElements(completionElement, javaReference)) {
        item.putUserData(QUALIFIER_TYPE_ATTR, plainQualifier);
        Object o = item.getObject();
        if (o instanceof PsiClass) {
          PsiClass specifiedQualifierClass = javaReference.isQualified() ? qualifierClass : ((PsiClass)o).getContainingClass();
          if (!isSourceLevelAccessible(element, (PsiClass)o, pkgContext, specifiedQualifierClass)) {
            continue;
          }
        }
        if (o instanceof PsiMember) {
          if (honorExcludes && isInExcludedPackage((PsiMember)o, true)) {
            continue;
          }
          mentioned.add(CompletionUtil.getOriginalOrSelf((PsiMember)o));
        }
        PsiTypeLookupItem qualifierCast = findQualifierCast(item, castItems, plainQualifier, processor, expectedTypes);
        if (qualifierCast != null) item = castQualifier(item, qualifierCast);
        set.add(highlighter.highlightIfNeeded(qualifierCast != null ? qualifierCast.getType() : plainQualifier, item, o));
      }
    }

    PsiElement refQualifier = javaReference.getQualifier();
    if (refQualifier == null && PsiTreeUtil.getParentOfType(element, PsiPackageStatement.class, PsiImportStatementBase.class) == null) {
      StaticMemberProcessor memberProcessor = new JavaStaticMemberProcessor(parameters);
      memberProcessor.processMembersOfRegisteredClasses(nameCondition, (member, psiClass) -> {
        if (!mentioned.contains(member) && processor.satisfies(member, ResolveState.initial())) {
          ContainerUtil.addIfNotNull(set, memberProcessor.createLookupElement(member, psiClass, true));
        }
      });
    }
    else if (refQualifier instanceof PsiSuperExpression && ((PsiSuperExpression)refQualifier).getQualifier() == null) {
      set.addAll(SuperCalls.suggestQualifyingSuperCalls(element, javaReference, elementFilter, options, nameCondition));
    }

    return set;
  }

  static @NotNull PsiReferenceExpression createReference(@NotNull String text, @NotNull PsiElement context) {
    return (PsiReferenceExpression)JavaPsiFacade.getElementFactory(context.getProject()).createExpressionFromText(text, context);
  }

  private static @NotNull List<PsiType> getQualifierCastTypes(PsiJavaReference javaReference, @NotNull CompletionParameters parameters) {
    if (javaReference instanceof PsiReferenceExpression refExpr) {
      PsiExpression qualifier = refExpr.getQualifierExpression();
      if (qualifier != null) {
        Project project = qualifier.getProject();
        PairFunction<PsiExpression, CompletionParameters, PsiType> evaluator = refExpr.getContainingFile().getCopyableUserData(DYNAMIC_TYPE_EVALUATOR);
        if (evaluator != null) {
          PsiType type = evaluator.fun(qualifier, parameters);
          if (type != null) {
            return Collections.singletonList(type);
          }
        }

        return GuessManager.getInstance(project).getControlFlowExpressionTypeConjuncts(qualifier, parameters.getInvocationCount() > 1);
      }
    }
    return Collections.emptyList();
  }

  private static boolean shouldCast(@NotNull LookupElement item,
                                    @NotNull PsiTypeLookupItem castTypeItem,
                                    @Nullable PsiType plainQualifier,
                                    @NotNull JavaCompletionProcessor processor,
                                    @NotNull Set<? extends PsiType> expectedTypes) {
    PsiType castType = castTypeItem.getType();
    if (plainQualifier != null) {
      Object o = item.getObject();
      if (o instanceof PsiMethod) {
        if (plainQualifier instanceof PsiClassType && castType instanceof PsiClassType) {
          PsiMethod method = (PsiMethod)o;
          PsiClassType.ClassResolveResult plainResult = ((PsiClassType)plainQualifier).resolveGenerics();
          PsiClass plainClass = plainResult.getElement();
          HierarchicalMethodSignature signature = method.getHierarchicalMethodSignature();
          PsiMethod plainMethod = plainClass == null ? null :
                                  StreamEx.ofTree(signature, s -> StreamEx.of(s.getSuperSignatures()))
                                    .map(sig -> MethodSignatureUtil.findMethodBySignature(plainClass, sig, true))
                                    .filter(Objects::nonNull)
                                    .findFirst().orElse(null);
          if (plainMethod != null) {
            PsiClassType.ClassResolveResult castResult = ((PsiClassType)castType).resolveGenerics();
            PsiClass castClass = castResult.getElement();

            if (castClass == null || !castClass.isInheritor(plainClass, true)) {
              return false;
            }

            if (!processor.isAccessible(plainMethod)) {
              return true;
            }

            PsiSubstitutor castSub = TypeConversionUtil.getSuperClassSubstitutor(plainClass, (PsiClassType)castType);
            PsiType typeAfterCast = toRaw(castSub.substitute(method.getReturnType()));
            PsiType typeDeclared = toRaw(plainResult.getSubstitutor().substitute(plainMethod.getReturnType()));
            return typeAfterCast != null && typeDeclared != null &&
                   !typeAfterCast.equals(typeDeclared) &&
                   ContainerUtil.exists(expectedTypes, et -> et.isAssignableFrom(typeAfterCast) && !et.isAssignableFrom(typeDeclared));
          }
        }
      }

      return containsMember(castType, o, true) && !containsMember(plainQualifier, o, true);
    }
    return false;
  }

  private static @NotNull LookupElement castQualifier(@NotNull LookupElement item, @NotNull PsiTypeLookupItem castTypeItem) {
    return new LookupElementDecorator<>(item) {
      @Override
      public void handleInsert(@NotNull InsertionContext context) {
        Document document = context.getEditor().getDocument();
        context.commitDocument();
        PsiFile file = context.getFile();
        PsiJavaCodeReferenceElement ref =
          PsiTreeUtil.findElementOfClassAtOffset(file, context.getStartOffset(), PsiJavaCodeReferenceElement.class, false);
        if (ref != null) {
          PsiElement qualifier = ref.getQualifier();
          if (qualifier != null) {
            CommonCodeStyleSettings settings = CompletionStyleUtil.getCodeStyleSettings(context);

            String parenSpace = settings.SPACE_WITHIN_PARENTHESES ? " " : "";
            document.insertString(qualifier.getTextRange().getEndOffset(), parenSpace + ")");

            String spaceWithin = settings.SPACE_WITHIN_CAST_PARENTHESES ? " " : "";
            String prefix = "(" + parenSpace + "(" + spaceWithin;
            String spaceAfter = settings.SPACE_AFTER_TYPE_CAST ? " " : "";
            int exprStart = qualifier.getTextRange().getStartOffset();
            document.insertString(exprStart, prefix + spaceWithin + ")" + spaceAfter);

            CompletionUtil.emulateInsertion(context, exprStart + prefix.length(), castTypeItem);
            PsiDocumentManager.getInstance(file.getProject()).doPostponedOperationsAndUnblockDocument(document);
            context.getEditor().getCaretModel().moveToOffset(context.getTailOffset());
          }
        }

        super.handleInsert(context);
      }

      @Override
      public void renderElement(@NotNull LookupElementPresentation presentation) {
        super.renderElement(presentation);

        presentation.appendTailText(" on " + castTypeItem.getType().getPresentableText(), true);
      }
    };
  }

  private static PsiTypeLookupItem findQualifierCast(@NotNull LookupElement item,
                                                     @NotNull List<PsiTypeLookupItem> castTypeItems,
                                                     @Nullable PsiType plainQualifier,
                                                     JavaCompletionProcessor processor,
                                                     Set<? extends PsiType> expectedTypes) {
    return ContainerUtil.find(castTypeItems, c -> shouldCast(item, c, plainQualifier, processor, expectedTypes));
  }

  private static @Nullable PsiType toRaw(@Nullable PsiType type) {
    return type instanceof PsiClassType ? ((PsiClassType)type).rawType() : type;
  }

  public static boolean insertSemicolonAfter(@NotNull PsiLambdaExpression lambdaExpression) {
    return lambdaExpression.getBody() instanceof PsiCodeBlock || insertSemicolon(lambdaExpression.getParent());
  }

  static boolean insertSemicolon(PsiElement parent) {
    return !(parent instanceof PsiExpressionList) && !(parent instanceof PsiExpression);
  }

  static class JavaLookupElementHighlighter {
    private final @NotNull PsiElement myPlace;
    private final @Nullable VirtualFile myOriginalFile;
    private final @NotNull LanguageLevel myLanguageLevel;
    private final @NotNull Set<PsiField> myConstantsUsedInSwitch;

    JavaLookupElementHighlighter(@NotNull PsiElement place, @Nullable VirtualFile originalFile) {
      myPlace = place;
      myOriginalFile = originalFile;
      myLanguageLevel = PsiUtil.getLanguageLevel(myPlace);
      myConstantsUsedInSwitch = findConstantsUsedInSwitch(myPlace);
    }

    @NotNull
    LookupElement highlightIfNeeded(@Nullable PsiType qualifierType, @NotNull LookupElement item, @NotNull Object object) {
      LookupElement element = generateLookupElementDecorator(qualifierType, object, presentationDecorator ->
        LookupElementDecorator.withRenderer(item, new LookupElementRenderer<>() {
          @Override
          public void renderElement(LookupElementDecorator<LookupElement> element, LookupElementPresentation presentation) {
            element.getDelegate().renderElement(presentation);
            presentationDecorator.accept(presentation);
          }
        }));
      return element == null ? item : element;
    }

    private @Nullable LookupElement generateLookupElementDecorator(@Nullable PsiType qualifierType, @NotNull Object object,
                                                                   @NotNull Function<Consumer<LookupElementPresentation>, LookupElementDecorator<LookupElement>> generator) {
      if (object instanceof PsiMember) {
        if (JdkApiCompatibilityCache.getInstance().firstCompatibleLanguageLevel((PsiMember)object, myLanguageLevel) != null) {
          LookupElementDecorator<LookupElement> element = generator.apply(presentation -> presentation.setItemTextForeground(JBColor.RED));
          return PrioritizedLookupElement.withExplicitProximity(element, -1);
        }
        if (object instanceof PsiEnumConstant psiEnumConstant &&
            myConstantsUsedInSwitch.contains(CompletionUtil.getOriginalOrSelf(psiEnumConstant))) {
          LookupElementDecorator<LookupElement> element = generator.apply(presentation -> presentation.setItemTextForeground(JBColor.RED));
          return PrioritizedLookupElement.withExplicitProximity(element, -1);
        }
        if (object instanceof PsiClass psiClass) {
          if (ReferenceListWeigher.INSTANCE.getApplicability(psiClass, myPlace) == inapplicable) {
            LookupElementDecorator<LookupElement> element = generator.apply(presentation -> presentation.setItemTextForeground(JBColor.RED));
            return PrioritizedLookupElement.withExplicitProximity(element, -1);
          }
          if (PsiUtil.isAvailable(JavaFeature.MODULES, myPlace)) {
            final PsiJavaModule currentModule =
              ReadAction.compute(() -> JavaPsiModuleUtil.findDescriptorByFile(myOriginalFile, myPlace.getProject()));
            if (currentModule != null) {
              final PsiJavaModule targetModule = ReadAction.compute(() -> JavaPsiModuleUtil.findDescriptorByElement(psiClass));
              if (targetModule != null && targetModule != currentModule &&
                  !JavaPsiModuleUtil.reads(currentModule, targetModule)) {
                LookupElementDecorator<LookupElement> element = generator.apply(presentation -> presentation.setItemTextForeground(JBColor.RED));
                return PrioritizedLookupElement.withExplicitProximity(element, -1);
              }
            }
          }
        }
      }
      if (containsMember(qualifierType, object, false) && !qualifierType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
        LookupElementDecorator<LookupElement> element = generator.apply(presentation -> presentation.setItemTextBold(true));
        return object instanceof PsiField ? element : PrioritizedLookupElement.withExplicitProximity(element, 1);
      }
      return null;
    }
  }

  static @NotNull JavaLookupElementHighlighter getHighlighterForPlace(@NotNull PsiElement place, @Nullable VirtualFile originalFile) {
    return new JavaLookupElementHighlighter(place, originalFile);
  }

  public static @NotNull LookupElement highlightIfNeeded(@Nullable PsiType qualifierType,
                                                         @Nullable VirtualFile originalFile,
                                                         @NotNull LookupElement item,
                                                         @NotNull Object object,
                                                         @NotNull PsiElement place) {
    return getHighlighterForPlace(place, originalFile).highlightIfNeeded(qualifierType, item, object);
  }


  @Contract("null, _, _ -> false")
  private static boolean containsMember(@Nullable PsiType qualifierType, @NotNull Object object, boolean checkBases) {
    if (!(object instanceof PsiMember)) return false;

    if (qualifierType instanceof PsiArrayType) { //length and clone()
      PsiFile file = ((PsiMember)object).getContainingFile();
      //yes, they're a bit dummy
      return file == null || file.getVirtualFile() == null;
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

  static @Unmodifiable @NotNull Iterable<? extends LookupElement> createLookupElements(@NotNull CompletionElement completionElement, @NotNull PsiJavaReference reference) {
    Object completion = completionElement.getElement();
    assert !(completion instanceof LookupElement);

    if (reference instanceof PsiJavaCodeReferenceElement) {
      if (completion instanceof PsiMethod &&
          ((PsiJavaCodeReferenceElement)reference).getParent() instanceof PsiImportStaticStatement) {
        return Collections.singletonList(JavaLookupElementBuilder.forMethod((PsiMethod)completion, PsiSubstitutor.EMPTY));
      }

      if (completion instanceof PsiClass) {
        List<JavaPsiClassReferenceElement> classItems = JavaClassNameCompletionContributor.createClassLookupItems(
          CompletionUtil.getOriginalOrSelf((PsiClass)completion),
          JavaClassNameCompletionContributor.AFTER_NEW.accepts(reference),
          JavaClassNameInsertHandler.JAVA_CLASS_INSERT_HANDLER,
          Conditions.alwaysTrue());
        return JBIterable.from(classItems).flatMap(i -> JavaConstructorCallElement.wrap(i, reference.getElement()));
      }
    }

    PsiSubstitutor substitutor = completionElement.getSubstitutor();
    if (substitutor == null) substitutor = PsiSubstitutor.EMPTY;
    if (completion instanceof PsiClass) {
      JavaPsiClassReferenceElement classItem =
        JavaClassNameCompletionContributor.createClassLookupItem((PsiClass)completion, true).setSubstitutor(substitutor);
      return JavaConstructorCallElement.wrap(classItem, reference.getElement());
    }
    if (completion instanceof PsiMethod) {
      if (reference instanceof PsiMethodReferenceExpression) {
        return Collections.singleton((LookupElement)new JavaMethodReferenceElement(
          (PsiMethod)completion, (PsiMethodReferenceExpression)reference, completionElement.getMethodRefType()));
      }

      JavaMethodCallElement item = new JavaMethodCallElement((PsiMethod)completion).setQualifierSubstitutor(substitutor);
      item.setForcedQualifier(completionElement.getQualifierText());
      return Collections.singletonList(item);
    }
    if (completion instanceof PsiVariable) {
      if (completion instanceof PsiEnumConstant enumConstant &&
          PsiTreeUtil.isAncestor(enumConstant.getArgumentList(), reference.getElement(), true)) {
        return Collections.emptyList();
      }
      return Collections.singletonList(new VariableLookupItem((PsiVariable)completion).setSubstitutor(substitutor).qualifyIfNeeded(reference, null));
    }
    if (completion instanceof PsiPackage) {
      return Collections.singletonList(new PackageLookupItem((PsiPackage)completion, reference.getElement()));
    }

    return Collections.singletonList(LookupItemUtil.objectToLookupItem(completion));
  }

  public static boolean hasAccessibleConstructor(@NotNull PsiType type, @NotNull PsiElement place) {
    if (type instanceof PsiArrayType) return true;

    PsiClass psiClass = PsiUtil.resolveClassInType(type);
    if (psiClass == null || psiClass.isEnum() || psiClass.isAnnotationType()) return false;

    PsiMethod[] methods = psiClass.getConstructors();
    return methods.length == 0 || ContainerUtil.exists(methods, constructor -> isConstructorCompletable(constructor, place));
  }

  private static boolean isConstructorCompletable(@NotNull PsiMethod constructor, @NotNull PsiElement place) {
    if (!(constructor instanceof PsiCompiledElement)) return true; // it's possible to use a quick fix to make accessible after completion
    if (constructor.hasModifierProperty(PsiModifier.PRIVATE)) return false;
    if (constructor.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) return PsiUtil.isAccessible(constructor, place, null);
    return true;
  }

  public static LinkedHashSet<String> getAllLookupStrings(@NotNull PsiMember member) {
    LinkedHashSet<String> allLookupStrings = new LinkedHashSet<>();
    String name = member.getName();
    allLookupStrings.add(name);
    PsiClass containingClass = member.getContainingClass();
    while (containingClass != null) {
      String className = containingClass.getName();
      if (className == null) {
        break;
      }
      name = className + "." + name;
      allLookupStrings.add(name);
      PsiElement parent = containingClass.getParent();
      if (!(parent instanceof PsiClass)) {
        break;
      }
      containingClass = (PsiClass)parent;
    }
    return allLookupStrings;
  }

  public static boolean mayHaveSideEffects(@Nullable PsiElement element) {
    return element instanceof PsiExpression && SideEffectChecker.mayHaveSideEffects((PsiExpression)element);
  }

  public static void insertClassReference(@NotNull PsiClass psiClass, @NotNull PsiFile file, int offset) {
    insertClassReference(psiClass, file, offset, offset);
  }

  public static int insertClassReference(PsiClass psiClass, PsiFile file, int startOffset, int endOffset) {
    Project project = file.getProject();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    documentManager.commitAllDocuments();

    PsiManager manager = file.getManager();

    Document document = FileDocumentManager.getInstance().getDocument(file.getViewProvider().getVirtualFile());

    PsiReference reference = file.findReferenceAt(startOffset);
    if (reference != null && manager.areElementsEquivalent(psiClass, resolve(project, reference))) {
      return endOffset;
    }

    String name = psiClass.getName();
    if (name == null) {
      return endOffset;
    }

    if (reference != null && !psiClass.hasModifierProperty(PsiModifier.STATIC)) {
      PsiClass containingClass = psiClass.getContainingClass();
      if (containingClass != null && containingClass.hasTypeParameters()) {
        PsiModifierListOwner enclosingStaticElement = PsiUtil.getEnclosingStaticElement(reference.getElement(), null);
        if (enclosingStaticElement != null && !PsiTreeUtil.isAncestor(enclosingStaticElement, psiClass, false)) {
          return endOffset;
        }
      }
    }

    assert document != null;
    document.replaceString(startOffset, endOffset, name);

    int newEndOffset = startOffset + name.length();
    RangeMarker toDelete = insertTemporary(newEndOffset, document, " ");

    documentManager.commitAllDocuments();

    PsiElement element = file.findElementAt(startOffset);
    if (element instanceof PsiIdentifier) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement ref && !ref.isQualified() &&
          !(parent.getParent() instanceof PsiPackageStatement) && psiClass.isValid() &&
          !psiClass.getManager().areElementsEquivalent(psiClass, resolveReference(ref))) {
        boolean staticImport = ref instanceof PsiImportStaticReferenceElement;
        PsiElement newElement;
        try {
          newElement = staticImport
                       ? ((PsiImportStaticReferenceElement)ref).bindToTargetClass(psiClass)
                       : ref.bindToElement(psiClass);
        }
        catch (IncorrectOperationException e) {
          return endOffset; // can happen if fqn contains reserved words, for example
        }
        SmartPsiElementPointer<PsiClass> classPointer = SmartPointerManager.createPointer(psiClass);

        RangeMarker rangeMarker = document.createRangeMarker(newElement.getTextRange());
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
          psiClass = classPointer.getElement();

          if (!staticImport &&
              psiClass != null &&
              !psiClass.getManager().areElementsEquivalent(psiClass, resolveReference((PsiReference)newElement)) &&
              !PsiUtil.isInnerClass(psiClass)) {
            String qName = psiClass.getQualifiedName();
            if (qName != null) {
              document.replaceString(newElement.getTextRange().getStartOffset(), newEndOffset, qName);
              newEndOffset = newElement.getTextRange().getStartOffset() + qName.length();
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

  private static @Nullable PsiElement resolve(Project project, PsiReference reference) {
    return DumbService.getInstance(project).computeWithAlternativeResolveEnabled(reference::resolve);
  }

  static @Nullable PsiElement resolveReference(PsiReference psiReference) {
    return DumbService.getInstance(psiReference.getElement().getProject()).computeWithAlternativeResolveEnabled(() -> {
      if (psiReference instanceof PsiPolyVariantReference) {
        ResolveResult[] results = ((PsiPolyVariantReference)psiReference).multiResolve(true);
        if (results.length == 1) return results[0].getElement();
      }
      return psiReference.resolve();
    });
  }

  public static @Nullable RangeMarker insertTemporary(int endOffset, Document document, String temporary) {
    CharSequence chars = document.getCharsSequence();
    if (endOffset < chars.length() && Character.isJavaIdentifierPart(chars.charAt(endOffset))) {
      document.insertString(endOffset, temporary);
      RangeMarker toDelete = document.createRangeMarker(endOffset, endOffset + 1);
      toDelete.setGreedyToLeft(true);
      toDelete.setGreedyToRight(true);
      return toDelete;
    }
    return null;
  }

  public static void insertParentheses(@NotNull InsertionContext context,
                                       @NotNull LookupElement item,
                                       boolean overloadsMatter,
                                       boolean hasParams) {
    insertParentheses(context, item, overloadsMatter, ThreeState.fromBoolean(hasParams), false);
  }

  static void insertParentheses(@NotNull InsertionContext context,
                                @NotNull LookupElement item,
                                boolean overloadsMatter,
                                ThreeState hasParams, // UNSURE if providing no arguments is a valid situation
                                boolean forceClosingParenthesis) {
    Editor editor = context.getEditor();
    char completionChar = context.getCompletionChar();
    PsiFile file = context.getFile();

    TailType tailType = completionChar == '(' ? TailTypes.noneType() :
                        completionChar == ':' ? TailTypes.conditionalExpressionColonType() :
                        LookupItem.handleCompletionChar(context.getEditor(), item, completionChar);
    boolean hasTail = tailType != TailTypes.noneType() && tailType != TailTypes.unknownType();
    boolean smart = completionChar == Lookup.COMPLETE_STATEMENT_SELECT_CHAR;

    if (completionChar == '(' || completionChar == '.' || completionChar == ',' || completionChar == ';' || completionChar == ':' || completionChar == ' ') {
      context.setAddCompletionChar(false);
    }

    if (hasTail) {
      hasParams = ThreeState.NO;
    }
    boolean needRightParenth = forceClosingParenthesis ||
                               !smart && (CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET ||
                                          hasParams == ThreeState.NO && completionChar != '(');

    context.commitDocument();

    CommonCodeStyleSettings styleSettings = CompletionStyleUtil.getCodeStyleSettings(context);
    PsiElement elementAt = file.findElementAt(context.getStartOffset());
    if (elementAt == null || !(elementAt.getParent() instanceof PsiMethodReferenceExpression)) {
      ThreeState hasParameters = hasParams;
      boolean spaceBetweenParentheses = hasParams == ThreeState.YES && styleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES ||
                                        hasParams == ThreeState.UNSURE && styleSettings.SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES;
      new ParenthesesInsertHandler<>(styleSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES, spaceBetweenParentheses,
                                     needRightParenth, styleSettings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE) {
        @Override
        protected boolean placeCaretInsideParentheses(InsertionContext context1, LookupElement item1) {
          return hasParameters != ThreeState.NO;
        }

        @Override
        protected PsiElement findExistingLeftParenthesis(@NotNull InsertionContext context) {
          PsiElement token = super.findExistingLeftParenthesis(context);
          return isPartOfLambda(token) ? null : token;
        }

        private static boolean isPartOfLambda(PsiElement token) {
          return token != null && token.getParent() instanceof PsiExpressionList &&
                 PsiUtilCore.getElementType(PsiTreeUtil.nextVisibleLeaf(token.getParent())) == JavaTokenType.ARROW;
        }
      }.handleInsert(context, item);
    }

    if (hasParams != ThreeState.NO) {
      // Invoke parameters popup
      AutoPopupController.getInstance(file.getProject()).autoPopupParameterInfo(editor, overloadsMatter ? null : (PsiElement)item.getObject());
    }

    if (smart || !needRightParenth || !EditorSettingsExternalizable.getInstance().isInsertParenthesesAutomatically() ||
        !insertTail(context, item, tailType, hasTail)) {
      return;
    }

    if (completionChar == '.') {
      AutoPopupController.getInstance(file.getProject()).autoPopupMemberLookup(context.getEditor(), null);
    } else if (completionChar == ',') {
      AutoPopupController.getInstance(file.getProject()).autoPopupParameterInfo(context.getEditor(), null);
    }
  }

  private static boolean insertTail(InsertionContext context, LookupElement item, TailType tailType, boolean hasTail) {
    TailType toInsert = tailType;
    LookupItem<?> lookupItem = item.as(LookupItem.CLASS_CONDITION_KEY);
    if (toInsert == EqTailType.INSTANCE) {
      toInsert = TailTypes.unknownType();
    }
    if (lookupItem == null || lookupItem.getAttribute(LookupItem.TAIL_TYPE_ATTR) != TailTypes.unknownType()) {
      if (!hasTail && item.getObject() instanceof PsiMethod && PsiTypes.voidType().equals(((PsiMethod)item.getObject()).getReturnType())) {
        PsiDocumentManager.getInstance(context.getProject()).commitAllDocuments();
        if (psiElement().beforeLeaf(psiElement().withText(".")).accepts(context.getFile().findElementAt(context.getTailOffset() - 1))) {
          return false;
        }

        boolean insertAdditionalSemicolon = true;
        PsiElement leaf = context.getFile().findElementAt(context.getStartOffset());
        PsiElement composite = leaf == null ? null : leaf.getParent();
        if (composite instanceof PsiReferenceExpression) {
          PsiElement parent = composite.getParent();
          if (parent instanceof PsiMethodCallExpression) {
            parent = parent.getParent();
          }
          if (parent instanceof PsiLambdaExpression lambda && !insertSemicolonAfter(lambda)) {
            insertAdditionalSemicolon = false;
          }
          if (parent instanceof PsiExpressionStatement && parent.getParent() instanceof PsiForStatement forStatement &&
              forStatement.getUpdate() == parent) {
            insertAdditionalSemicolon = false;
          }
        }
        if (insertAdditionalSemicolon) {
          toInsert = TailTypes.semicolonType();
        }

      }
    }
    Editor editor = context.getEditor();
    int tailOffset = context.getTailOffset();
    int afterTailOffset = toInsert.processTail(editor, tailOffset);
    int caretOffset = editor.getCaretModel().getOffset();
    if (afterTailOffset > tailOffset &&
        tailOffset > caretOffset &&
        TabOutScopesTracker.getInstance().removeScopeEndingAt(editor, caretOffset) > 0) {
      TabOutScopesTracker.getInstance().registerEmptyScope(editor, caretOffset, afterTailOffset);
    }
    return true;
  }

  //need to shorten references in type argument list
  public static void shortenReference(PsiFile file, int offset) throws IncorrectOperationException {
    Project project = file.getProject();
    PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
    Document document = manager.getDocument(file);
    if (document == null) {
      PsiUtilCore.ensureValid(file);
      LOG.error("No document for " + file);
      return;
    }

    manager.commitDocument(document);
    PsiReference ref = file.findReferenceAt(offset);
    if (ref != null) {
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(ref.getElement());
      PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
    }
  }

  static boolean inSomePackage(@NotNull PsiElement context) {
    PsiFile contextFile = context.getContainingFile();
    return contextFile instanceof PsiClassOwner && StringUtil.isNotEmpty(((PsiClassOwner)contextFile).getPackageName());
  }

  static boolean isSourceLevelAccessible(@NotNull PsiElement context,
                                         @NotNull PsiClass psiClass,
                                         boolean pkgContext) {
    return isSourceLevelAccessible(context, psiClass, pkgContext, psiClass.getContainingClass());
  }

  private static boolean isSourceLevelAccessible(PsiElement context,
                                                 @NotNull PsiClass psiClass,
                                                 boolean pkgContext,
                                                 @Nullable PsiClass qualifierClass) {
    if (!JavaPsiFacade.getInstance(psiClass.getProject()).getResolveHelper().isAccessible(psiClass, context, qualifierClass)) {
      return false;
    }

    if (pkgContext) {
      PsiClass topLevel = PsiUtil.getTopLevelClass(psiClass);
      if (topLevel != null) {
        String fqName = topLevel.getQualifiedName();
        if (fqName == null || !StringUtil.isEmpty(StringUtil.getPackageName(fqName))) return true;
        if (!(topLevel instanceof PsiImplicitClass)) return false;
        PsiClass contextClass = PsiTreeUtil.getParentOfType(context, PsiClass.class);
        return contextClass != null && fqName.equals(contextClass.getQualifiedName());
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
    offset += open.length();
    context.getEditor().getCaretModel().moveToOffset(offset);
    if (CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) {
      context.getDocument().insertString(offset, escapeXmlIfNeeded(context, ">"));
      context.commitDocument();

      TabOutScopesTracker.getInstance().registerEmptyScope(context.getEditor(), offset, getTabOutOffset(context, offset));
    }
    if (context.getCompletionChar() != Lookup.COMPLETE_STATEMENT_SELECT_CHAR) {
      context.setAddCompletionChar(false);
    }
    return true;
  }

  private static int getTabOutOffset(@NotNull InsertionContext context, int offset) {
    int result = offset + 2;

    PsiCall call = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), offset, PsiCall.class, false);
    if (call == null || call.getArgumentList() == null) return result;

    int argumentListEnd = call.getArgumentList().getTextRange().getEndOffset();

    if (argumentListEnd <= result && isAnonymous(call)) return offset + 1;

    return argumentListEnd - 1;
  }

  private static boolean isAnonymous(@NotNull PsiCall call) {
    if (!(call instanceof PsiNewExpression newExpression)) {
      return false;
    }

    PsiImmediateClassType targetType = ObjectUtils.tryCast(newExpression.getType(), PsiImmediateClassType.class);
    if (targetType == null) return false;

    return targetType.resolve() instanceof PsiAnonymousClass;
  }

  public static FakePsiElement createContextWithXxxVariable(@NotNull PsiElement place, @NotNull PsiType varType) {
    return new FakePsiElement() {
      @Override
      public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                         @NotNull ResolveState state,
                                         PsiElement lastParent,
                                         @NotNull PsiElement place) {
        return processor.execute(new LightVariableBuilder<>("xxx", varType, place), ResolveState.initial());
      }

      @Override
      public PsiElement getParent() {
        return place;
      }
    };
  }

  public static @NotNull String escapeXmlIfNeeded(InsertionContext context, @NotNull String generics) {
    if (context.getFile().getViewProvider().getBaseLanguage() instanceof JspxLanguage) {
      return StringUtil.escapeXmlEntities(generics);
    }
    return generics;
  }

  public static boolean isEffectivelyDeprecated(PsiDocCommentOwner member) {
    if (DumbService.isDumb(member.getProject())) return false;
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

  static int findQualifiedNameStart(@NotNull InsertionContext context) {
    int start = context.getTailOffset() - 1;
    while (start >= 0) {
      char ch = context.getDocument().getCharsSequence().charAt(start);
      if (!Character.isJavaIdentifierPart(ch) && ch != '.') break;
      start--;
    }
    return start + 1;
  }

  static @Nullable PsiExpression getInstanceOfOperand(PsiElement position) {
    PsiElement parent = position.getParent();
    while (parent instanceof PsiJavaCodeReferenceElement || parent instanceof PsiTypeElement) {
      parent = parent.getParent();
    }
    if (parent instanceof PsiInstanceOfExpression) {
      return ((PsiInstanceOfExpression)parent).getOperand();
    }
    return null;
  }
}
