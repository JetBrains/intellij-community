// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.codeInsight.lookup.VariableLookupItem;
import com.intellij.codeInspection.magicConstant.MagicCompletionContributor;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class JavaMembersGetter extends MembersGetter {
  private final @NotNull PsiType myExpectedType;
  private final CompletionParameters myParameters;

  public JavaMembersGetter(@NotNull PsiType expectedType, CompletionParameters parameters) {
    super(new JavaStaticMemberProcessor(parameters), parameters.getPosition());
    myExpectedType = JavaCompletionUtil.originalize(expectedType);
    myParameters = parameters;
  }

  public void addMembers(boolean searchInheritors, final Consumer<? super LookupElement> results) {
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

    if (psiClass != null && myExpectedType instanceof PsiClassType) {
      new BuilderCompletion((PsiClassType)myExpectedType, psiClass, myPlace).suggestBuilderVariants().forEach(results::consume);
    }
  }

  private record ConstantClass(@NotNull String constantContainingClass, @NotNull LanguageLevel languageLevel,
                               @Nullable String priorityConstant) {
  }
  
  private static final Map<String, ConstantClass> CONSTANT_SUGGESTIONS = Map.of(
    "java.nio.charset.Charset", new ConstantClass("java.nio.charset.StandardCharsets", LanguageLevel.JDK_1_7, "UTF_8"),
    "java.time.temporal.TemporalUnit", new ConstantClass("java.time.temporal.ChronoUnit", LanguageLevel.JDK_1_8, null),
    "java.time.temporal.TemporalField", new ConstantClass("java.time.temporal.ChronoField", LanguageLevel.JDK_1_8, null)
  );

  private void addKnownConstants(Consumer<? super LookupElement> results) {
    PsiFile file = myParameters.getOriginalFile();
    ConstantClass constantClass = CONSTANT_SUGGESTIONS.get(myExpectedType.getCanonicalText());
    if (constantClass != null && PsiUtil.getLanguageLevel(file).isAtLeast(constantClass.languageLevel)) {
      PsiClass charsetsClass =
        JavaPsiFacade.getInstance(file.getProject()).findClass(constantClass.constantContainingClass, file.getResolveScope());
      if (charsetsClass != null) {
        for (PsiField field : charsetsClass.getFields()) {
          if (field.hasModifierProperty(PsiModifier.STATIC) &&
              field.hasModifierProperty(PsiModifier.PUBLIC) && myExpectedType.isAssignableFrom(field.getType())) {
            LookupElement element = createFieldElement(field, charsetsClass);
            if (element != null && field.getName().equals(constantClass.priorityConstant)) {
              element = PrioritizedLookupElement.withPriority(element, 1.0);
            }
            results.consume(element);
          }
        }
      }
    }
  }

  private void addConstantsFromReferencedClassesInSwitch(final Consumer<? super LookupElement> results) {
    if (!JavaCompletionContributor.IN_SWITCH_LABEL.accepts(myPlace)) return;
    PsiSwitchBlock block = Objects.requireNonNull(PsiTreeUtil.getParentOfType(myPlace, PsiSwitchBlock.class));
    final Set<PsiField> fields = ReferenceExpressionCompletionContributor.findConstantsUsedInSwitch(block);
    final Set<PsiClass> classes = new HashSet<>();
    for (PsiField field : fields) {
      ContainerUtil.addIfNotNull(classes, field.getContainingClass());
    }
    for (PsiClass aClass : classes) {
      processMembers(element -> {
        //noinspection SuspiciousMethodCalls
        if (!fields.contains(element.getObject())) {
          results.consume(TailTypeDecorator.withTail(element, TailTypes.forSwitchLabel(block)));
        }
      }, aClass, true, false);
    }
  }

  private void addConstantsFromTargetClass(Consumer<? super LookupElement> results, boolean searchInheritors) {
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

  @Nullable
  private static PsiClass getCalledClass(@Nullable PsiElement call) {
    if (call instanceof PsiMethodCallExpression) {
      for (final JavaResolveResult result : ((PsiMethodCallExpression)call).getMethodExpression().multiResolve(true)) {
        final PsiElement element = result.getElement();
        if (element instanceof PsiMethod) {
          final PsiClass aClass = ((PsiMethod)element).getContainingClass();
          if (aClass != null && !CommonClassNames.JAVA_LANG_MATH.equals(aClass.getQualifiedName())) {
            return aClass;
          }
        }
      }
    }
    if (call instanceof PsiNewExpression) {
      final PsiJavaCodeReferenceElement reference = ((PsiNewExpression)call).getClassReference();
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

  @Override
  @Nullable
  protected LookupElement createFieldElement(@NotNull PsiField field, @NotNull PsiClass origClass) {
    if (!myExpectedType.isAssignableFrom(field.getType())) {
      return null;
    }

    return new VariableLookupItem(field, false)
      .qualifyIfNeeded(ObjectUtils.tryCast(myParameters.getPosition().getParent(), PsiJavaCodeReferenceElement.class), origClass);
  }

  @Override
  @Nullable
  protected LookupElement createMethodElement(@NotNull PsiMethod method, @NotNull PsiClass origClass) {
    JavaMethodCallElement item = new JavaMethodCallElement(method, false, false);
    item.setInferenceSubstitutorFromExpectedType(myPlace, myExpectedType);
    PsiType type = item.getType();
    if (type == null || !myExpectedType.isAssignableFrom(type)) {
      return null;
    }

    return item;
  }
}
