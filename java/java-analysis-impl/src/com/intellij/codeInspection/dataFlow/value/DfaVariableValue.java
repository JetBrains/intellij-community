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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2002
 * Time: 6:31:08 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.codeInspection.dataFlow.SpecialField;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Trinity;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

import static com.intellij.patterns.PsiJavaPatterns.*;

public class DfaVariableValue extends DfaValue {

  private static final ElementPattern<? extends PsiModifierListOwner> MEMBER_OR_METHOD_PARAMETER =
    or(psiMember(), psiParameter().withSuperParent(2, psiMember()));

  public static class Factory {
    private final MultiMap<Trinity<Boolean,String,DfaVariableValue>,DfaVariableValue> myExistingVars = new MultiMap<>();
    private final DfaValueFactory myFactory;

    Factory(DfaValueFactory factory) {
      myFactory = factory;
    }

    public DfaVariableValue createVariableValue(PsiVariable myVariable, boolean isNegated) {
      PsiType varType = myVariable.getType();
      if (varType instanceof PsiEllipsisType) {
        varType = new PsiArrayType(((PsiEllipsisType)varType).getComponentType());
      }
      return createVariableValue(myVariable, varType, isNegated, null);
    }
    @NotNull
    public DfaVariableValue createVariableValue(@NotNull PsiModifierListOwner myVariable,
                                                @Nullable PsiType varType,
                                                boolean isNegated,
                                                @Nullable DfaVariableValue qualifier) {
      Trinity<Boolean,String,DfaVariableValue> key = Trinity.create(isNegated, ((PsiNamedElement)myVariable).getName(), qualifier);
      for (DfaVariableValue aVar : myExistingVars.get(key)) {
        if (aVar.hardEquals(myVariable, varType, isNegated, qualifier)) return aVar;
      }

      DfaVariableValue result = new DfaVariableValue(myVariable, varType, isNegated, myFactory, qualifier);
      myExistingVars.putValue(key, result);
      while (qualifier != null) {
        qualifier.myDependents.add(result);
        qualifier = qualifier.getQualifier();
      }
      return result;
    }

    @NotNull
    public List<DfaVariableValue> getAllQualifiedBy(@NotNull DfaVariableValue value) {
      return value.myDependents;
    }
  }

  private final PsiModifierListOwner myVariable;
  private final PsiType myVarType;
  @Nullable private final DfaVariableValue myQualifier;
  private DfaVariableValue myNegatedValue;
  private final boolean myIsNegated;
  private Nullness myInherentNullability;
  private final DfaTypeValue myTypeValue;
  private final List<DfaVariableValue> myDependents = new SmartList<>();

  private DfaVariableValue(@NotNull PsiModifierListOwner variable, @Nullable PsiType varType, boolean isNegated, DfaValueFactory factory, @Nullable DfaVariableValue qualifier) {
    super(factory);
    myVariable = variable;
    myIsNegated = isNegated;
    myQualifier = qualifier;
    myVarType = varType;
    DfaValue typeValue = myFactory.createTypeValue(varType, Nullness.UNKNOWN);
    myTypeValue = typeValue instanceof DfaTypeValue ? (DfaTypeValue)typeValue : null;
    if (varType != null && !varType.isValid()) {
      PsiUtil.ensureValidType(varType, "Variable: " + variable + " of class " + variable.getClass());
    }
  }

  @Nullable
  public DfaTypeValue getTypeValue() {
    return myTypeValue;
  }

  @NotNull
  public PsiModifierListOwner getPsiVariable() {
    return myVariable;
  }

  @Nullable
  public PsiType getVariableType() {
    return myVarType;
  }

  public boolean isNegated() {
    return myIsNegated;
  }

  @Nullable
  public DfaVariableValue getNegatedValue() {
    return myNegatedValue;
  }

  @Override
  public DfaVariableValue createNegated() {
    if (myNegatedValue != null) {
      return myNegatedValue;
    }
    return myNegatedValue = myFactory.getVarFactory().createVariableValue(myVariable, myVarType, !myIsNegated, myQualifier);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return (myIsNegated ? "!" : "") + ((PsiNamedElement)myVariable).getName() + (myQualifier == null ? "" : "|" + myQualifier.toString());
  }

  private boolean hardEquals(PsiModifierListOwner psiVar, PsiType varType, boolean negated, DfaVariableValue qualifier) {
    return psiVar == myVariable &&
           negated == myIsNegated &&
           qualifier == myQualifier &&
           Comparing.equal(TypeConversionUtil.erasure(varType), TypeConversionUtil.erasure(myVarType));
  }

  @Nullable
  public DfaVariableValue getQualifier() {
    return myQualifier;
  }

  @NotNull
  public Nullness getInherentNullability() {
    if (myInherentNullability != null) {
      return myInherentNullability;
    }

    return myInherentNullability = calcInherentNullability();
  }

  @NotNull
  private Nullness calcInherentNullability() {
    PsiModifierListOwner var = getPsiVariable();
    Nullness nullability = DfaPsiUtil.getElementNullability(getVariableType(), var);
    if (nullability != Nullness.UNKNOWN) {
      return nullability;
    }

    Nullness defaultNullability = myFactory.isUnknownMembersAreNullable() && MEMBER_OR_METHOD_PARAMETER.accepts(var) ? Nullness.NULLABLE : Nullness.UNKNOWN;

    if (var instanceof PsiParameter && var.getParent() instanceof PsiForeachStatement) {
      PsiExpression iteratedValue = ((PsiForeachStatement)var.getParent()).getIteratedValue();
      if (iteratedValue != null) {
        PsiType itemType = JavaGenericsUtil.getCollectionItemType(iteratedValue);
        if (itemType != null) {
          return DfaPsiUtil.getElementNullability(itemType, var);
        }
      }
    }

    if (var instanceof PsiField && myFactory.isHonorFieldInitializers()) {
      return getNullabilityFromFieldInitializers((PsiField)var, defaultNullability);
    }

    return defaultNullability;
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

    return ReferencesSearch.search(field).forEach(reference -> reference instanceof PsiReferenceExpression && !PsiUtil.isAccessedForWriting((PsiReferenceExpression)reference));
  }

  private static boolean isCheapEnoughToSearch(PsiField field, String name) {
    SearchScope scope = field.getUseScope();
    if (!(scope instanceof GlobalSearchScope)) return true;

    PsiSearchHelper helper = PsiSearchHelper.SERVICE.getInstance(field.getProject());
    PsiSearchHelper.SearchCostResult result = helper.isCheapEnoughToSearch(name, (GlobalSearchScope)scope, field.getContainingFile(), null);
    return result != PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES;
  }

  private static Nullness getFieldInitializerNullness(@NotNull PsiExpression expression) {
    if (expression.textMatches(PsiKeyword.NULL)) return Nullness.NULLABLE;
    if (expression instanceof PsiNewExpression || expression instanceof PsiLiteralExpression || expression instanceof PsiPolyadicExpression) return Nullness.NOT_NULL;
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

  public boolean isFlushableByCalls() {
    if (myVariable instanceof PsiLocalVariable || myVariable instanceof PsiParameter) return false;
    boolean finalField = myVariable instanceof PsiVariable && myVariable.hasModifierProperty(PsiModifier.FINAL);
    boolean specialFinalField = myVariable instanceof PsiMethod &&
                           Arrays.stream(SpecialField.values()).anyMatch(sf -> sf.isFinal() && sf.isMyMethod((PsiMethod)myVariable));
    if (finalField || specialFinalField) {
      return myQualifier != null && myQualifier.isFlushableByCalls();
    }
    return true;
  }

  public boolean containsCalls() {
    return myVariable instanceof PsiMethod || myQualifier != null && myQualifier.containsCalls();
  }

}
