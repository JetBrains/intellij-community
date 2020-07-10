// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiFieldImpl;
import com.intellij.psi.util.*;
import com.intellij.util.containers.FList;
import com.intellij.util.containers.FactoryMap;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.patterns.PsiJavaPatterns.psiMember;
import static com.intellij.patterns.PsiJavaPatterns.psiParameter;
import static com.intellij.patterns.StandardPatterns.or;

public class DfaValueFactory {
  private final @NotNull List<DfaValue> myValues = new ArrayList<>();
  private final boolean myUnknownMembersAreNullable;
  private final @NotNull FieldChecker myFieldChecker;
  private final @NotNull Project myProject;
  private @Nullable DfaVariableValue myAssertionDisabled;

  /**
   * @param project a project in which context the analysis is performed
   * @param context an item to analyze (code-block, expression, class)
   * @param unknownMembersAreNullable if true, unknown (non-annotated members) are assumed to be nullable
   */
  public DfaValueFactory(@NotNull Project project, @Nullable PsiElement context, boolean unknownMembersAreNullable) {
    myProject = project;
    myFieldChecker = new FieldChecker(context);
    myUnknownMembersAreNullable = unknownMembersAreNullable;
    myValues.add(null);
    myVarFactory = new DfaVariableValue.Factory(this);
    myBoxedFactory = new DfaBoxedValue.Factory(this);
    myExpressionFactory = new DfaExpressionFactory(this);
    myBinOpFactory = new DfaBinOpValue.Factory(this);
    myTypeValueFactory = new DfaTypeValue.Factory(this);
  }

  public boolean canTrustFieldInitializer(PsiField field) {
    return myFieldChecker.canTrustFieldInitializer(field);
  }

  private static final ElementPattern<? extends PsiModifierListOwner> MEMBER_OR_METHOD_PARAMETER =
    or(psiMember(), psiParameter().withSuperParent(2, psiMember()));


  @NotNull
  public Nullability suggestNullabilityForNonAnnotatedMember(@NotNull PsiModifierListOwner member) {
    if (myUnknownMembersAreNullable &&
        MEMBER_OR_METHOD_PARAMETER.accepts(member) &&
        AnnotationUtil.getSuperAnnotationOwners(member).isEmpty()) {
      return Nullability.NULLABLE;
    }

    return Nullability.UNKNOWN;
  }

  @NotNull
  public DfaTypeValue getObjectType(@Nullable PsiType type, @NotNull Nullability nullability) {
    return fromDfType(DfTypes.typedObject(type, nullability));
  }

  public @Nullable DfaVariableValue getAssertionDisabled() {
    return myAssertionDisabled;
  }

  void setAssertionDisabled(@NotNull DfaVariableValue value) {
    assert myAssertionDisabled == null;
    myAssertionDisabled = value;
  }

  int registerValue(DfaValue value) {
    myValues.add(value);
    return myValues.size() - 1;
  }

  public DfaValue getValue(int id) {
    return myValues.get(id);
  }

  @Nullable
  @Contract("null -> null")
  public DfaValue createValue(PsiExpression psiExpression) {
    return myExpressionFactory.getExpressionDfaValue(psiExpression);
  }

  @NotNull
  public DfaTypeValue getInt(int value) {
    return fromDfType(DfTypes.intValue(value));
  }
  
  @NotNull
  public DfaTypeValue getUnknown() {
    return fromDfType(DfTypes.TOP);
  }

  /**
   * @return a special sentinel value that never equals to anything else (even unknown value) and 
   * sometimes pushed on the stack as control flow implementation detail. 
   * It's never assigned to the variable or merged with any other value.  
   */
  @NotNull
  public DfaValue getSentinel() {
    return mySentinelValue;
  }

  @NotNull
  public DfaTypeValue getBoolean(boolean value) {
    return fromDfType(DfTypes.booleanValue(value));
  }

  /**
   * @return a null value
   */
  @NotNull
  public DfaTypeValue getNull() {
    return fromDfType(DfTypes.NULL);
  }

  /**
   * Creates a constant of given type and given value. Constants are always unique 
   * (two different constants are not equal to each other).
   * 
   * The following types of the objects are supported:
   * <ul>
   *   <li>Integer/Long/Double/Float/Boolean (will be unboxed)</li>
   *   <li>Character/Byte/Short (will be unboxed and widened to int)</li>
   *   <li>String</li>
   *   <li>{@link PsiEnumConstant} (enum constant value, type must be the corresponding enum type)</li>
   *   <li>{@link PsiField} (a final field that contains a unique value, type must be a type of that field)</li>
   *   <li>{@link PsiType} (java.lang.Class object value, type must be java.lang.Class)</li>
   * </ul>
   *
   * @param type type of the constant
   * @return a DfaTypeValue whose type is DfConstantType that corresponds to given constant.
   */
  public DfaTypeValue getConstant(Object value, @NotNull PsiType type) {
    return fromDfType(DfTypes.constant(value, type));
  }

  /**
   * @param variable variable to create a constant based on its value
   * @return a value that represents a constant created from variable; null if variable cannot be represented as a constant
   */
  @Nullable
  public DfaValue getConstantFromVariable(PsiVariable variable) {
    if (!variable.hasModifierProperty(PsiModifier.FINAL) || DfaUtil.ignoreInitializer(variable)) return null;
    Object value = variable.computeConstantValue();
    PsiType type = variable.getType();
    if (value == null) {
      Boolean boo = computeJavaLangBooleanFieldReference(variable);
      if (boo != null) {
        DfaValue unboxed = getConstant(boo, PsiType.BOOLEAN);
        return getBoxedFactory().createBoxed(unboxed, PsiType.BOOLEAN.getBoxedType(variable));
      }
      if (DfaUtil.isEmptyCollectionConstantField(variable)) {
        return getConstant(variable, type);
      }
      PsiExpression initializer = PsiFieldImpl.getDetachedInitializer(variable);
      initializer = PsiUtil.skipParenthesizedExprDown(initializer);
      if (initializer instanceof PsiLiteralExpression && initializer.textMatches(PsiKeyword.NULL)) {
        return getNull();
      }
      if (variable instanceof PsiField && variable.hasModifierProperty(PsiModifier.STATIC) && ExpressionUtils.isNewObject(initializer)) {
        return getConstant(variable, type);
      }
      return null;
    }
    return getConstant(value, type);
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Nullable
  private static Boolean computeJavaLangBooleanFieldReference(final PsiVariable variable) {
    if (!(variable instanceof PsiField)) return null;
    PsiClass psiClass = ((PsiField)variable).getContainingClass();
    if (psiClass == null || !CommonClassNames.JAVA_LANG_BOOLEAN.equals(psiClass.getQualifiedName())) return null;
    @NonNls String name = variable.getName();
    return "TRUE".equals(name) ? Boolean.TRUE : "FALSE".equals(name) ? Boolean.FALSE : null;
  }

  @NotNull
  public DfaTypeValue fromDfType(@NotNull DfType dfType) {
    return myTypeValueFactory.create(dfType);
  }

  public Collection<DfaValue> getValues() {
    return Collections.unmodifiableCollection(myValues);
  }

  @NotNull
  public DfaControlTransferValue controlTransfer(TransferTarget kind, FList<Trap> traps) {
    return myControlTransfers.get(Pair.create(kind, traps));
  }

  private final Map<Pair<TransferTarget, FList<Trap>>, DfaControlTransferValue> myControlTransfers =
    FactoryMap.create(p -> new DfaControlTransferValue(this, p.first, p.second));

  private final DfaVariableValue.Factory myVarFactory;
  private final DfaBoxedValue.Factory myBoxedFactory;
  private final DfaBinOpValue.Factory myBinOpFactory;
  private final DfaExpressionFactory myExpressionFactory;
  private final DfaTypeValue.Factory myTypeValueFactory;
  private final DfaValue mySentinelValue = new DfaValue(this) {
    @Override
    public String toString() {
      return "SENTINEL";
    }
  };

  @NotNull
  public DfaVariableValue.Factory getVarFactory() {
    return myVarFactory;
  }

  @NotNull
  public DfaBoxedValue.Factory getBoxedFactory() {
    return myBoxedFactory;
  }

  @NotNull
  public DfaExpressionFactory getExpressionFactory() { return myExpressionFactory;}

  @NotNull
  public DfaBinOpValue.Factory getBinOpFactory() {
    return myBinOpFactory;
  }

  @NotNull
  public DfaValue createCommonValue(PsiExpression @NotNull [] expressions, PsiType targetType) {
    DfaValue loopElement = null;
    for (PsiExpression expression : expressions) {
      DfaValue expressionValue = createValue(expression);
      if (expressionValue == null) {
        expressionValue = getObjectType(expression.getType(), NullabilityUtil.getExpressionNullability(expression));
      }
      loopElement = loopElement == null ? expressionValue : loopElement.unite(expressionValue);
      if (DfaTypeValue.isUnknown(loopElement)) break;
    }
    return loopElement == null ? getUnknown() : DfaUtil.boxUnbox(loopElement, targetType);
  }

  private static class ClassInitializationInfo {
    private static final CallMatcher SAFE_CALLS =
      CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_OBJECTS, "requireNonNull");
    
    final boolean myCanInstantiateItself;
    final boolean myCtorsCallMethods;
    final boolean mySuperCtorsCallMethods;

    ClassInitializationInfo(@NotNull PsiClass psiClass) {
      // Indirect instantiation via other class is still possible, but hopefully unlikely
      boolean canInstantiateItself = false;
      for (PsiElement child = psiClass.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (child instanceof PsiMember && ((PsiMember)child).hasModifierProperty(PsiModifier.STATIC) &&
            SyntaxTraverser.psiTraverser(child).filter(PsiNewExpression.class)
              .filterMap(PsiNewExpression::getClassReference)
              .find(classRef -> classRef.isReferenceTo(psiClass)) != null) {
          canInstantiateItself = true;
          break;
        }
      }
      myCanInstantiateItself = canInstantiateItself;
      mySuperCtorsCallMethods =
        !InheritanceUtil.processSupers(psiClass, false, superClass -> !canCallMethodsInConstructors(superClass, true));
      myCtorsCallMethods = canCallMethodsInConstructors(psiClass, false);
    }

    private static boolean canCallMethodsInConstructors(@NotNull PsiClass aClass, boolean virtual) {
      boolean inByteCode = false;
      if (aClass instanceof PsiCompiledElement) {
        inByteCode = true;
        PsiElement navigationElement = aClass.getNavigationElement();
        if (navigationElement instanceof PsiClass) {
          aClass = (PsiClass)navigationElement;
        }
      }
      for (PsiMethod constructor : aClass.getConstructors()) {
        if (inByteCode && JavaMethodContractUtil.isPure(constructor) &&
            !JavaMethodContractUtil.hasExplicitContractAnnotation(constructor)) {
          // While pure constructor may call pure overridable method, our current implementation
          // of bytecode inference will not infer the constructor purity in this case.
          // So if we inferred a constructor purity from bytecode we can currently rely that
          // no overridable methods are called there.
          continue;
        }
        if (!constructor.getLanguage().isKindOf(JavaLanguage.INSTANCE)) return true;

        PsiCodeBlock body = constructor.getBody();
        if (body == null) continue;

        for (PsiMethodCallExpression call : SyntaxTraverser.psiTraverser().withRoot(body).filter(PsiMethodCallExpression.class)) {
          PsiReferenceExpression methodExpression = call.getMethodExpression();
          if (methodExpression.textMatches(PsiKeyword.THIS) || methodExpression.textMatches(PsiKeyword.SUPER)) continue;
          if (SAFE_CALLS.test(call)) continue;
          if (!virtual) return true;

          PsiMethod target = call.resolveMethod();
          if (target != null && PsiUtil.canBeOverridden(target)) return true;
        }
      }

      return false;
    }
  }

  private static class FieldChecker {
    private final boolean myTrustDirectFieldInitializers;
    private final boolean myTrustFieldInitializersInConstructors;
    private final boolean myCanInstantiateItself;
    private final PsiClass myClass;

    FieldChecker(PsiElement context) {
      PsiMethod method = context instanceof PsiClass ? null : PsiTreeUtil.getParentOfType(context, PsiMethod.class);
      PsiClass contextClass = method != null ? method.getContainingClass() : context instanceof PsiClass ? (PsiClass)context : null;
      myClass = contextClass;
      if (method == null || myClass == null) {
        myTrustDirectFieldInitializers = myTrustFieldInitializersInConstructors = myCanInstantiateItself = false;
        return;
      }
      // Indirect instantiation via other class is still possible, but hopefully unlikely
      ClassInitializationInfo info = CachedValuesManager.getCachedValue(contextClass, () -> CachedValueProvider.Result
        .create(new ClassInitializationInfo(contextClass), PsiModificationTracker.MODIFICATION_COUNT));
      myCanInstantiateItself = info.myCanInstantiateItself;
      if (method.hasModifierProperty(PsiModifier.STATIC) || method.isConstructor()) {
        myTrustDirectFieldInitializers = true;
        myTrustFieldInitializersInConstructors = false;
        return;
      }
      myTrustFieldInitializersInConstructors = !info.mySuperCtorsCallMethods && !info.myCtorsCallMethods;
      myTrustDirectFieldInitializers = !info.mySuperCtorsCallMethods;
    }

    boolean canTrustFieldInitializer(PsiField field) {
      if (field.hasInitializer()) {
        boolean staticField = field.hasModifierProperty(PsiModifier.STATIC);
        if (staticField && myClass != null && field.getContainingClass() != myClass) return true;
        return myTrustDirectFieldInitializers && (!myCanInstantiateItself || !staticField);
      }
      return myTrustFieldInitializersInConstructors;
    }
  }
}
