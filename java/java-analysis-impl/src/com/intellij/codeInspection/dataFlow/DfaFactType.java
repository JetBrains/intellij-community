/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Key;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.patterns.PsiJavaPatterns.psiMember;
import static com.intellij.patterns.PsiJavaPatterns.psiParameter;
import static com.intellij.patterns.StandardPatterns.or;

/**
 * A type of the fact which restricts some value.
 *
 * @author Tagir Valeev
 */
public abstract class DfaFactType<T> extends Key<T> {
  private static final List<DfaFactType<?>> ourFactTypes = new ArrayList<>();

  /**
   * This fact specifies whether the value can be null. The absence of the fact means that the nullability is unknown.
   */
  public static final DfaFactType<Boolean> CAN_BE_NULL = new CanBeNullFactType();

  /**
   * This fact is applied to the Optional values (like {@link java.util.Optional} or Guava Optional).
   * When its value is true, then optional is known to be present.
   * When its value is false, then optional is known to be empty (absent).
   */
  public static final DfaFactType<Boolean> OPTIONAL_PRESENCE = new DfaFactType<Boolean>("Optional presense") {
    @Override
    String toString(Boolean fact) {
      return fact ? "present Optional" : "absent Optional";
    }

    @Nullable
    @Override
    Boolean fromDfaValue(DfaValue value) {
      return value instanceof DfaOptionalValue ? ((DfaOptionalValue)value).isPresent() : null;
    }
  };

  /**
   * This fact is applied to the integral values (of types byte, char, short, int, long).
   * Its value represents a range of possible values.
   */
  public static final DfaFactType<LongRangeSet> RANGE = new DfaFactType<LongRangeSet>("Range") {
    @Override
    boolean isSuper(@NotNull LongRangeSet superFact, @NotNull LongRangeSet subFact) {
      return superFact.contains(subFact);
    }

    @Nullable
    @Override
    LongRangeSet fromDfaValue(DfaValue value) {
      if(value instanceof DfaVariableValue) {
        return calcFromVariable((DfaVariableValue)value);
      }
      return LongRangeSet.fromDfaValue(value);
    }

    @Nullable
    @Override
    LongRangeSet calcFromVariable(@NotNull DfaVariableValue var) {
      if (var.getQualifier() != null) {
        for (SpecialField sf : SpecialField.values()) {
          if (sf.isMyAccessor(var.getPsiVariable())) {
            return sf.getRange();
          }
        }
      }
      return LongRangeSet.fromType(var.getVariableType());
    }

    @Nullable
    @Override
    LongRangeSet intersectFacts(@NotNull LongRangeSet left, @NotNull LongRangeSet right) {
      LongRangeSet intersection = left.intersect(right);
      return intersection.isEmpty() ? null : intersection;
    }

    @Override
    String toString(LongRangeSet fact) {
      return fact.toString();
    }
  };

  private DfaFactType(String name) {
    super("DfaFactType: " + name);
    // Thread-safe as all DfaFactType instances are created only from DfaFactType class static initializer
    ourFactTypes.add(this);
  }

  @Nullable
  T fromDfaValue(DfaValue value) {
    return null;
  }

  // Could be expensive
  @Nullable
  T calcFromVariable(@NotNull DfaVariableValue value) {
    return null;
  }

  boolean isSuper(@NotNull T superFact, @NotNull T subFact) {
    return false;
  }

  /**
   * Intersects two facts of this type.
   *
   * @param left left fact
   * @param right right fact
   * @return intersection fact or null if facts are incompatible
   */
  @Nullable
  T intersectFacts(@NotNull T left, @NotNull T right) {
    return left.equals(right) ? left : null;
  }

  String toString(T fact) {
    return fact.toString();
  }

  static List<DfaFactType<?>> getTypes() {
    return Collections.unmodifiableList(ourFactTypes);
  }

  private static class CanBeNullFactType extends DfaFactType<Boolean> {
    private static final ElementPattern<? extends PsiModifierListOwner> MEMBER_OR_METHOD_PARAMETER =
      or(psiMember(), psiParameter().withSuperParent(2, psiMember()));

    private CanBeNullFactType() {super("Can be null");}

    @Override
    String toString(Boolean fact) {
      return fact ? "Nullable" : "NotNull";
    }

    @Nullable
    @Override
    Boolean fromDfaValue(DfaValue value) {
      if (value instanceof DfaConstValue) {
        return ((DfaConstValue)value).getValue() == null;
      }
      if (value instanceof DfaBoxedValue || value instanceof DfaUnboxedValue || value instanceof DfaRangeValue) {
        return false;
      }
      if (value instanceof DfaTypeValue) {
        return ((DfaTypeValue)value).getNullness().toBoolean();
      }
      return null;
    }

    @Nullable
    @Override
    Boolean calcFromVariable(@NotNull DfaVariableValue value) {
      PsiModifierListOwner var = value.getPsiVariable();
      Nullness nullability = DfaPsiUtil.getElementNullability(value.getVariableType(), var);
      if (nullability != Nullness.UNKNOWN) {
        return nullability.toBoolean();
      }

      Nullness defaultNullability =
        value.getFactory().isUnknownMembersAreNullable() && MEMBER_OR_METHOD_PARAMETER.accepts(var) ? Nullness.NULLABLE : Nullness.UNKNOWN;

      if (var instanceof PsiParameter && var.getParent() instanceof PsiForeachStatement) {
        PsiExpression iteratedValue = ((PsiForeachStatement)var.getParent()).getIteratedValue();
        if (iteratedValue != null) {
          PsiType itemType = JavaGenericsUtil.getCollectionItemType(iteratedValue);
          if (itemType != null) {
            return DfaPsiUtil.getElementNullability(itemType, var).toBoolean();
          }
        }
      }

      if (var instanceof PsiField && value.getFactory().isHonorFieldInitializers()) {
        return getNullabilityFromFieldInitializers((PsiField)var, defaultNullability).toBoolean();
      }

      return defaultNullability.toBoolean();
    }

    private static Nullness getNullabilityFromFieldInitializers(PsiField field, Nullness defaultNullability) {
      if (DfaPsiUtil.isFinalField(field)) {
        PsiExpression initializer = field.getInitializer();
        if (initializer != null) {
          return getFieldInitializerNullness(initializer);
        }

        List<PsiExpression> initializers = DfaPsiUtil.findAllConstructorInitializers(field);
        if (initializers.isEmpty()) {
          return defaultNullability;
        }

        for (PsiExpression expression : initializers) {
          if (getFieldInitializerNullness(expression) == Nullness.NULLABLE) {
            return Nullness.NULLABLE;
          }
        }

        if (DfaPsiUtil.isInitializedNotNull(field)) {
          return Nullness.NOT_NULL;
        }
      }
      else if (isOnlyImplicitlyInitialized(field)) {
        return Nullness.NOT_NULL;
      }
      return defaultNullability;
    }

    private static boolean isOnlyImplicitlyInitialized(PsiField field) {
      return CachedValuesManager.getCachedValue(field, () -> CachedValueProvider.Result.create(
        isImplicitlyInitializedNotNull(field) && weAreSureThereAreNoExplicitWrites(field),
        PsiModificationTracker.MODIFICATION_COUNT));
    }

    private static boolean isImplicitlyInitializedNotNull(PsiField field) {
      return ContainerUtil.exists(Extensions.getExtensions(ImplicitUsageProvider.EP_NAME), p -> p.isImplicitlyNotNullInitialized(field));
    }

    private static boolean weAreSureThereAreNoExplicitWrites(PsiField field) {
      String name = field.getName();
      if (name == null || field.getInitializer() != null) return false;

      if (!isCheapEnoughToSearch(field, name)) return false;

      return ReferencesSearch
        .search(field).forEach(
          reference -> reference instanceof PsiReferenceExpression && !PsiUtil.isAccessedForWriting((PsiReferenceExpression)reference));
    }

    private static boolean isCheapEnoughToSearch(PsiField field, String name) {
      SearchScope scope = field.getUseScope();
      if (!(scope instanceof GlobalSearchScope)) return true;

      PsiSearchHelper helper = PsiSearchHelper.SERVICE.getInstance(field.getProject());
      PsiSearchHelper.SearchCostResult result =
        helper.isCheapEnoughToSearch(name, (GlobalSearchScope)scope, field.getContainingFile(), null);
      return result != PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES;
    }

    private static Nullness getFieldInitializerNullness(@NotNull PsiExpression expression) {
      if (expression.textMatches(PsiKeyword.NULL)) return Nullness.NULLABLE;
      if (expression instanceof PsiNewExpression ||
          expression instanceof PsiLiteralExpression ||
          expression instanceof PsiPolyadicExpression) {
        return Nullness.NOT_NULL;
      }
      if (expression instanceof PsiReferenceExpression) {
        PsiElement target = ((PsiReferenceExpression)expression).resolve();
        return DfaPsiUtil.getElementNullability(expression.getType(), (PsiModifierListOwner)target);
      }
      if (expression instanceof PsiMethodCallExpression) {
        PsiMethod method = ((PsiMethodCallExpression)expression).resolveMethod();
        return method != null ? DfaPsiUtil.getElementNullability(expression.getType(), method) : Nullness.UNKNOWN;
      }
      return Nullness.UNKNOWN;
    }
  }
}
