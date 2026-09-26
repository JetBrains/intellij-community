// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.completion.modcommand;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.completion.BaseCompletionParameters;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.JavaCompletionContributor;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.completion.JavaStaticMemberProcessor;
import com.intellij.codeInsight.completion.PlainPrefixMatcher;
import com.intellij.codeInsight.completion.ReferenceExpressionCompletionContributor;
import com.intellij.codeInsight.completion.StaticMemberProcessor;
import com.intellij.codeInspection.magicConstant.MagicCompletionContributor;
import com.intellij.modcompletion.ModCompletionItem;
import com.intellij.modcompletion.ModCompletionResult;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Conditions;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSwitchBlock;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.ResolveState;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.impl.java.stubs.index.JavaStaticMemberTypeIndex;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

final class JavaMembersGetter {
  private static final Map<String, List<String>> COMMON_INHERITORS =
    Map.of("java.util.Collection", List.of("java.util.List", "java.util.Set"),
           "java.util.List", List.of("java.util.ArrayList"),
           "java.util.Set", List.of("java.util.HashSet", "java.util.TreeSet"),
           "java.util.Map", List.of("java.util.HashMap", "java.util.TreeMap"));
  private final PsiElement myPlace;
  private final @NotNull PsiType myExpectedType;
  private final BaseCompletionParameters myParameters;
  private final Set<PsiMember> myImportedStatically = new HashSet<>();
  private final List<PsiClass> myPlaceClasses = new ArrayList<>();
  private final List<PsiMethod> myPlaceMethods = new ArrayList<>();

  JavaMembersGetter(@NotNull PsiType expectedType, BaseCompletionParameters parameters) {
    StaticMemberProcessor processor = new JavaStaticMemberProcessor(parameters);
    PsiElement place = parameters.getPosition();
    myPlace = place;
    processor.processMembersOfRegisteredClasses(Conditions.alwaysTrue(), (member, psiClass) -> myImportedStatically.add(member));

    PsiClass current = PsiTreeUtil.getContextOfType(place, PsiClass.class);
    while (current != null) {
      current = CompletionUtil.getOriginalOrSelf(current);
      myPlaceClasses.add(current);
      current = PsiTreeUtil.getContextOfType(current, PsiClass.class);
    }

    PsiMethod eachMethod = PsiTreeUtil.getContextOfType(place, PsiMethod.class);
    while (eachMethod != null) {
      eachMethod = CompletionUtil.getOriginalOrSelf(eachMethod);
      myPlaceMethods.add(eachMethod);
      eachMethod = PsiTreeUtil.getContextOfType(eachMethod, PsiMethod.class);
    }

    myExpectedType = JavaCompletionUtil.originalize(expectedType);
    myParameters = parameters;
  }

  public void addMembers(boolean searchInheritors, final ModCompletionResult results) {
    if (MagicCompletionContributor.getAllowedValues(myParameters.getPosition()) != null) {
      return;
    }

    addKnownConstants(results);

    addConstantsFromTargetClass(results, searchInheritors);
    if (myExpectedType instanceof PsiPrimitiveType && PsiTypes.doubleType().isAssignableFrom(myExpectedType)) {
      addConstantsFromReferencedClassesInSwitch(results);
    }

    if (JavaCompletionContributor.IN_SWITCH_LABEL.accepts(myPlace)) {
      return; //non-enum values are processed above, enum values will be suggested by reference completion
    }

    final PsiClass psiClass = PsiUtil.resolveClassInType(myExpectedType);
    processMembers(results, psiClass, PsiTreeUtil.getParentOfType(myPlace, PsiAnnotation.class) == null, searchInheritors);

    //if (psiClass != null && myExpectedType instanceof PsiClassType) {
    //  new BuilderCompletion((PsiClassType)myExpectedType, psiClass, myPlace).suggestBuilderVariants().forEach(results::consume);
    //}
  }

  private boolean mayProcessMembers(@Nullable PsiClass psiClass) {
    if (psiClass == null) {
      return false;
    }

    for (PsiClass placeClass : myPlaceClasses) {
      if (InheritanceUtil.isInheritorOrSelf(placeClass, psiClass, true)) {
        return false;
      }
    }
    return true;
  }

  public void processMembers(final ModCompletionResult results, final @Nullable PsiClass where,
                             final boolean acceptMethods, final boolean searchInheritors) {
    if (where == null || isPrimitiveClass(where)) return;

    String qualifiedName = where.getQualifiedName();
    final boolean searchFactoryMethods = searchInheritors &&
                                         !CommonClassNames.JAVA_LANG_OBJECT.equals(qualifiedName) &&
                                         !isPrimitiveClass(where);

    final Project project = myPlace.getProject();
    final GlobalSearchScope scope = myPlace.getResolveScope();

    final PsiClassType baseType = JavaPsiFacade.getElementFactory(project).createType(where);
    Consumer<PsiType> consumer = psiType -> {
      PsiClass psiClass = PsiUtil.resolveClassInType(psiType);
      if (psiClass == null) {
        return;
      }
      psiClass = CompletionUtil.getOriginalOrSelf(psiClass);
      if (mayProcessMembers(psiClass)) {
        final FilterScopeProcessor<PsiElement> declProcessor = new FilterScopeProcessor<>(TrueFilter.INSTANCE);
        psiClass.processDeclarations(declProcessor, ResolveState.initial(), null, myPlace);
        doProcessMembers(acceptMethods, results, psiType == baseType, psiClass, declProcessor.getResults());

        String name = psiClass.getName();
        if (name != null && searchFactoryMethods) {
          Collection<PsiMember> factoryMethods = JavaStaticMemberTypeIndex.getInstance().getStaticMembers(name, project, scope);
          doProcessMembers(acceptMethods, results, false, psiClass, factoryMethods);
        }
      }
    };
    consumer.accept(baseType);
    if (searchInheritors && !CommonClassNames.JAVA_LANG_OBJECT.equals(qualifiedName)) {
      CodeInsightUtil.processSubTypes(baseType, myPlace, true, PlainPrefixMatcher.ALWAYS_TRUE, consumer::accept);
    } else if (qualifiedName != null) {
      // If we don't search inheritors, we still process some known very common ones
      StreamEx.ofTree(qualifiedName, cls -> StreamEx.of(COMMON_INHERITORS.getOrDefault(cls, List.of()))).skip(1)
        .map(className -> JavaPsiFacade.getElementFactory(project).createTypeByFQClassName(className, myPlace.getResolveScope()))
        .forEach(consumer);
    }
  }

  private void doProcessMembers(boolean acceptMethods,
                                ModCompletionResult results,
                                boolean isExpectedTypeMember,
                                PsiClass origClass,
                                Collection<? extends PsiElement> declarations) {
    for (final PsiElement result : declarations) {
      if (result instanceof PsiMember member && !(result instanceof PsiClass)) {
        if (member instanceof PsiMethod method && method.isConstructor()) {
          PsiClass aClass = member.getContainingClass();
          if (aClass == null || aClass.hasModifierProperty(PsiModifier.ABSTRACT)) continue;
          PsiClass enclosingClass = aClass.hasModifierProperty(PsiModifier.STATIC) ? null : aClass.getContainingClass();
          if (enclosingClass != null &&
              !InheritanceUtil.hasEnclosingInstanceInScope(enclosingClass, myPlace, true, false)) {
            continue;
          }
          // For parameterized class constructors, we add a diamond. Do not suggest constructors for parameterized classes
          // in Java 6 or older when diamond was not supported
          if (aClass.getTypeParameters().length > 0 && !PsiUtil.isAvailable(JavaFeature.DIAMOND_TYPES, myPlace)) continue;
          // Constructor type parameters aren't supported yet
          if (method.getTypeParameters().length > 0) continue;
        }
        else if (!(member.hasModifierProperty(PsiModifier.STATIC))) {
          continue;
        }
        if (!(result instanceof PsiField) && !(result instanceof PsiMethod)) continue;
        if (result instanceof PsiField && !member.hasModifierProperty(PsiModifier.FINAL)) continue;
        if (result instanceof PsiMethod && (!acceptMethods || myPlaceMethods.contains(result))) continue;
        if (JavaCompletionUtil.isInExcludedPackage(member, false) || myImportedStatically.contains(member)) continue;

        if (!JavaPsiFacade.getInstance(myPlace.getProject()).getResolveHelper().isAccessible(member, myPlace, null)) {
          continue;
        }

        final ModCompletionItem item = result instanceof PsiMethod method ? createMethodElement(method, origClass) :
                                   createFieldElement((PsiField)result, origClass);
        if (item != null) {
          results.accept(item);// AutoCompletionPolicy.NEVER_AUTOCOMPLETE.applyPolicy(item));
        }
      }
    }
  }

  private static boolean isPrimitiveClass(PsiClass where) {
    String qname = where.getQualifiedName();
    if (qname == null || !qname.startsWith("java.lang.")) return false;
    return CommonClassNames.JAVA_LANG_STRING.equals(qname) || InheritanceUtil.isInheritor(where, CommonClassNames.JAVA_LANG_NUMBER);
  }

  private record ConstantClass(@NotNull String constantContainingClass, @NotNull LanguageLevel languageLevel,
                               @Nullable String priorityConstant) {
  }
  
  private static final Map<String, ConstantClass> CONSTANT_SUGGESTIONS = Map.of(
    "java.nio.charset.Charset", new ConstantClass("java.nio.charset.StandardCharsets", LanguageLevel.JDK_1_7, "UTF_8"),
    "java.time.temporal.TemporalUnit", new ConstantClass("java.time.temporal.ChronoUnit", LanguageLevel.JDK_1_8, null),
    "java.time.temporal.TemporalField", new ConstantClass("java.time.temporal.ChronoField", LanguageLevel.JDK_1_8, null)
  );

  private void addKnownConstants(ModCompletionResult results) {
    PsiFile file = myParameters.getOriginalFile();
    ConstantClass constantClass = CONSTANT_SUGGESTIONS.get(myExpectedType.getCanonicalText());
    if (constantClass != null && PsiUtil.getLanguageLevel(file).isAtLeast(constantClass.languageLevel)) {
      PsiClass charsetsClass =
        JavaPsiFacade.getInstance(file.getProject()).findClass(constantClass.constantContainingClass, file.getResolveScope());
      if (charsetsClass != null) {
        for (PsiField field : charsetsClass.getFields()) {
          if (field.hasModifierProperty(PsiModifier.STATIC) &&
              field.hasModifierProperty(PsiModifier.PUBLIC) && myExpectedType.isAssignableFrom(field.getType())) {
            ModCompletionItem element = createFieldElement(field, charsetsClass);
            if (element != null && field.getName().equals(constantClass.priorityConstant)) {
              //element = PrioritizedModCompletionItem.withPriority(element, 1.0);
            }
            results.accept(element);
          }
        }
      }
    }
  }

  private void addConstantsFromReferencedClassesInSwitch(final ModCompletionResult results) {
    if (!JavaCompletionContributor.IN_SWITCH_LABEL.accepts(myPlace)) return;
    PsiSwitchBlock block = Objects.requireNonNull(PsiTreeUtil.getParentOfType(myPlace, PsiSwitchBlock.class));
    final Set<PsiField> fields = ReferenceExpressionCompletionContributor.findConstantsUsedInSwitch(block);
    final Set<PsiClass> classes = new HashSet<>();
    for (PsiField field : fields) {
      ContainerUtil.addIfNotNull(classes, field.getContainingClass());
    }
    for (PsiClass aClass : classes) {
      processMembers(element -> {
        if (!fields.contains(element.contextObject())) {
          results.accept(element);
        }
      }, aClass, true, false);
    }
  }

  private void addConstantsFromTargetClass(ModCompletionResult results, boolean searchInheritors) {
    PsiElement parent = myPlace.getParent();
    if (!(parent instanceof PsiReferenceExpression)) {
      return;
    }

    PsiElement prev = parent;
    parent = parent.getParent();
    while (parent instanceof PsiBinaryExpression binaryExpression) {
      final IElementType op = binaryExpression.getOperationTokenType();
      if (JavaTokenType.EQEQ == op || JavaTokenType.NE == op) {
        if (prev == binaryExpression.getROperand()) {
          processMembers(results, getCalledClass(binaryExpression.getLOperand()), true, searchInheritors
          );
        }
        return;
      }
      prev = parent;
      parent = parent.getParent();
    }
    if (parent instanceof PsiExpressionList) {
      processMembers(results, getCalledClass(parent.getParent()), true, searchInheritors);
    }
  }

  private static @Nullable PsiClass getCalledClass(@Nullable PsiElement call) {
    if (call instanceof PsiMethodCallExpression methodCall) {
      for (final JavaResolveResult result : methodCall.multiResolve(true)) {
        final PsiElement element = result.getElement();
        if (element instanceof PsiMethod method) {
          final PsiClass aClass = method.getContainingClass();
          if (aClass != null && !CommonClassNames.JAVA_LANG_MATH.equals(aClass.getQualifiedName())) {
            return aClass;
          }
        }
      }
    }
    if (call instanceof PsiNewExpression newExpression) {
      final PsiJavaCodeReferenceElement reference = newExpression.getClassReference();
      if (reference != null) {
        for (final JavaResolveResult result : reference.multiResolve(true)) {
          final PsiElement element = result.getElement();
          if (element instanceof PsiClass) {
            return (PsiClass)element;
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private VariableCompletionItem createFieldElement(@NotNull PsiField field, @NotNull PsiClass origClass) {
    if (!myExpectedType.isAssignableFrom(field.getType())) {
      return null;
    }

    return new VariableCompletionItem(field, false)
      .qualifyIfNeeded(ObjectUtils.tryCast(myParameters.getPosition().getParent(), PsiJavaCodeReferenceElement.class), origClass);
  }

  @Nullable
  private MethodCallCompletionItem createMethodElement(@NotNull PsiMethod method, @NotNull PsiClass origClass) {
    MethodCallCompletionItem item = new MethodCallCompletionItem(method, false, false);
    item = item.withExpectedType(myPlace, myExpectedType);
    PsiType type = item.getType();
    if (type == null || !myExpectedType.isAssignableFrom(type)) {
      return null;
    }

    return item;
  }
}
