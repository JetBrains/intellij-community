// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.callMatcher;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.ImportsUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UCallableReferenceExpression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

/**
 * This interface represents a condition upon method call
 *
 * @author Tagir Valeev
 */
public interface CallMatcher extends Predicate<PsiMethodCallExpression> {
  /**
   * @return distinct names of the methods for which this matcher may return true. For any other method it guaranteed to return false
   */
  Stream<String> names();

  @Contract(value = "null -> false", pure = true)
  boolean methodReferenceMatches(PsiMethodReferenceExpression methodRef);

  @Override
  @Contract(value = "null -> false", pure = true)
  boolean test(@Nullable PsiMethodCallExpression call);

  @Contract(value = "null -> false", pure = true)
  boolean methodMatches(@Nullable PsiMethod method);

  @Contract(value = "null -> false", pure = true)
  boolean uCallMatches(@Nullable UCallExpression call);

  @Contract(value = "null -> false", pure = true)
  boolean uCallableReferenceMatches(@Nullable UCallableReferenceExpression reference);

  /**
   * Returns true if the supplied expression is (possibly parenthesized) method call which matches this matcher
   *
   * @param expression expression to test
   * @return true if the supplied expression matches this matcher
   */
  @Contract(value = "null -> false", pure = true)
  default boolean matches(@Nullable PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    return expression instanceof PsiMethodCallExpression && test((PsiMethodCallExpression)expression);
  }

  /**
   * @return a matcher that matches nothing
   */
  static CallMatcher none() {
    return new CallMatcher() {
      @Override
      public Stream<String> names() {
        return Stream.empty();
      }

      @Override
      public boolean methodReferenceMatches(PsiMethodReferenceExpression methodRef) {
        return false;
      }

      @Override
      public boolean test(@Nullable PsiMethodCallExpression call) {
        return false;
      }

      @Override
      public boolean methodMatches(@Nullable PsiMethod method) {
        return false;
      }

      @Override
      public boolean uCallMatches(@Nullable UCallExpression call) {
        return false;
      }

      @Override
      public boolean uCallableReferenceMatches(@Nullable UCallableReferenceExpression reference) {
        return false;
      }
    };
  }

  /**
   * Returns a new matcher which will return true if any of supplied matchers return true
   *
   * @param matchers matchers to delegate to
   * @return a new matcher
   */
  static CallMatcher anyOf(CallMatcher... matchers) {
    if (matchers.length == 0) return none();
    if (matchers.length == 1) return matchers[0];
    return new CallMatcher() {
      @Override
      public Stream<String> names() {
        return Stream.of(matchers).flatMap(CallMatcher::names).distinct();
      }

      @Override
      public boolean methodReferenceMatches(PsiMethodReferenceExpression methodRef) {
        for (CallMatcher m : matchers) {
          if (m.methodReferenceMatches(methodRef)) {
            return true;
          }
        }
        return false;
      }

      @Override
      public boolean methodMatches(PsiMethod method) {
        for (CallMatcher m : matchers) {
          if (m.methodMatches(method)) {
            return true;
          }
        }
        return false;
      }

      @Override
      public boolean uCallMatches(@Nullable UCallExpression call) {
        for (CallMatcher m : matchers) {
          if (m.uCallMatches(call)) {
            return true;
          }
        }
        return false;
      }

      @Override
      public boolean uCallableReferenceMatches(@Nullable UCallableReferenceExpression reference) {
        for (CallMatcher m : matchers) {
          if (m.uCallableReferenceMatches(reference)) {
            return true;
          }
        }
        return false;
      }

      @Override
      public boolean test(PsiMethodCallExpression call) {
        for (CallMatcher m : matchers) {
          if (m.test(call)) {
            return true;
          }
        }
        return false;
      }

      @Override
      public String toString() {
        return Stream.of(matchers).map(CallMatcher::toString).collect(Collectors.joining(" or ", "{", "}"));
      }
    };
  }

  /**
   * Creates a matcher which matches an instance method having one of supplied names which class (or any of superclasses) is className
   *
   * @param className fully-qualified class name
   * @param methodNames names of the methods
   * @return a new matcher
   */
  @Contract(pure = true)
  static Simple instanceCall(@NotNull @NonNls String className, @NonNls String... methodNames) {
    return new Simple(className, Set.of(methodNames), null, CallType.INSTANCE);
  }

  /**
   * Creates a matcher which matches an instance method having one of supplied names which class is exactly a className
   *
   * @param className fully-qualified class name
   * @param methodNames names of the methods
   * @return a new matcher
   */
  @Contract(pure = true)
  static Simple exactInstanceCall(@NotNull @NonNls String className, @NonNls String... methodNames) {
    return new Simple(className, Set.of(methodNames), null, CallType.EXACT_INSTANCE);
  }

  /**
   * Creates a matcher which matches a static method having one of supplied names which class is className
   *
   * @param className fully-qualified class name
   * @param methodNames names of the methods
   * @return a new matcher
   */
  @Contract(pure = true)
  static Simple staticCall(@NotNull @NonNls String className, @NonNls String... methodNames) {
    return new Simple(className, Set.of(methodNames), null, CallType.STATIC);
  }

  static Simple enumValues() {
    return Simple.ENUM_VALUES;
  }

  static Simple enumValueOf() {
    return Simple.ENUM_VALUE_OF;
  }

  /**
   * Matches given expression if it's a call or a method reference returning a corresponding PsiReferenceExpression if match is successful.
   *
   * @param expression expression to match
   * @return PsiReferenceExpression if match is successful, null otherwise
   */
  @Contract(pure = true)
  default @Nullable PsiReferenceExpression getReferenceIfMatched(PsiExpression expression) {
    if (expression instanceof PsiMethodReferenceExpression && methodReferenceMatches((PsiMethodReferenceExpression)expression)) {
      return (PsiReferenceExpression)expression;
    }
    if (expression instanceof PsiMethodCallExpression && test((PsiMethodCallExpression)expression)) {
      return ((PsiMethodCallExpression)expression).getMethodExpression();
    }
    return null;
  }

  /**
   * @return call matcher with additional check before actual call matching
   */
  @Contract(pure = true)
  default CallMatcher withContextFilter(@NotNull Predicate<? super PsiElement> filter) {
    return new CallMatcher() {
      @Override
      public Stream<String> names() {
        return CallMatcher.this.names();
      }

      @Override
      public boolean methodReferenceMatches(PsiMethodReferenceExpression methodRef) {
        if (methodRef == null || !filter.test(methodRef)) return false;
        return CallMatcher.this.methodReferenceMatches(methodRef);
      }

      @Override
      public boolean test(@Nullable PsiMethodCallExpression call) {
        if (call == null || !filter.test(call)) return false;
        return CallMatcher.this.test(call);
      }

      @Override
      public boolean methodMatches(@Nullable PsiMethod method) {
        if (method == null || !filter.test(method)) return false;
        return CallMatcher.this.methodMatches(method);
      }

      @Override
      public boolean uCallMatches(@Nullable UCallExpression call) {
        if (call == null || !filter.test(call.getSourcePsi())) return false;
        return CallMatcher.this.uCallMatches(call);
      }

      @Override
      public boolean uCallableReferenceMatches(@Nullable UCallableReferenceExpression reference) {
        if (reference == null || !filter.test(reference.getSourcePsi())) return false;
        return CallMatcher.this.uCallableReferenceMatches(reference);
      }

      @Override
      public String toString() {
        return CallMatcher.this.toString();
      }
    };
  }

  /**
   * @return call matcher, that matches element for file with given language level or higher
   */
  @Contract(pure = true)
  default CallMatcher withLanguageLevelAtLeast(@NotNull LanguageLevel level) {
    return withContextFilter(element -> PsiUtil.getLanguageLevel(element).isAtLeast(level));
  }

  final class Simple implements CallMatcher {
    static final Simple ENUM_VALUES = new Simple("", Collections.singleton("values"), ArrayUtilRt.EMPTY_STRING_ARRAY, CallType.ENUM_STATIC);
    static final Simple ENUM_VALUE_OF =
      new Simple("", Collections.singleton("valueOf"), new String[]{CommonClassNames.JAVA_LANG_STRING}, CallType.ENUM_STATIC);
    private final @NotNull String myClassName;
    private final @NotNull Set<String> myNames;
    private final String @Nullable [] myParameters;
    private final CallType myCallType;

    private Simple(@NotNull String className, @NotNull Set<String> names, String @Nullable [] parameters, CallType callType) {
      myClassName = className;
      myNames = names;
      myParameters = parameters;
      myCallType = callType;
    }

    /**
     * Creates a new matcher based on the current matcher, allowing unresolved method calls to be matched.
     * This matcher supports verifying unresolved method calls and their context, such as method names,
     * qualifier expressions, and class names.
     * <p>
     * The resulting matcher enforces the following criteria for unresolved calls:
     * - Method name must match the specified names.
     * - The argument list must match certain conditions based on parameter types.
     * - Checking types is limited and checks only basic cases. Generics, primitive conversions, and so on are not supported and should be checked separately.
     * - Class name must match the specified class name.
     * - Class name must end with qualifier expressions. Qualifier expression should be unresolved.
     * - Call is checked as it is static.
     * <p>
     * This matcher supports only {@link #test(PsiMethodCallExpression)} method.
     *
     * @return a new CallMatcher instance that allows unresolved method calls to be matched
     */
    public CallMatcher allowStaticUnresolved() {
      return new UnresolvedStaticCallMatcher();
    }

    @Override
    public Stream<String> names() {
      return myNames.stream();
    }

    /**
     * Creates a new matcher which in addition to current matcher checks the number of parameters of the called method
     *
     * @param count expected number of parameters
     * @return a new matcher
     * @throws IllegalStateException if this matcher is already limited to parameters count or types
     */
    @Contract(pure = true)
    public Simple parameterCount(int count) {
      if (myParameters != null) {
        throw new IllegalStateException("Parameter count is already set to " + count);
      }
      return new Simple(myClassName, myNames, count == 0 ? ArrayUtilRt.EMPTY_STRING_ARRAY : new String[count], myCallType);
    }

    /**
     * Creates a new matcher which in addition to current matcher checks the number of parameters of the called method
     * and their types
     *
     * @param types textual representation of parameter types (may contain null to ignore checking parameter type of specific argument)
     * @return a new matcher
     * @throws IllegalStateException if this matcher is already limited to parameters count or types
     */
    @Contract(pure = true)
    public Simple parameterTypes(@NonNls String @NotNull ... types) {
      if (myParameters != null) {
        throw new IllegalStateException("Parameters are already registered");
      }
      return new Simple(myClassName, myNames, types.length == 0 ? ArrayUtilRt.EMPTY_STRING_ARRAY : types.clone(), myCallType);
    }

    private static boolean parameterTypeMatches(String type, PsiParameter parameter) {
      if (type == null) return true;
      PsiType psiType = parameter.getType();
      return psiType.equalsToText(type) || PsiTypesUtil.classNameEquals(psiType, type);
    }

    private static boolean expressionTypeMatches(@Nullable String type, @NotNull PsiExpression argument) {
      if (type == null) return true;
      if (type.endsWith("...")) {
        type = type.substring(0, type.length() - 3);
      }
      PsiType psiType = argument.getType();
      if (psiType == null) return false;
      return psiType.equalsToText(type) ||
             PsiTypesUtil.classNameEquals(psiType, type) ||
             JAVA_LANG_OBJECT.equals(type) ||
             //small optimization, because it can be slow and String is popular
             (!JAVA_LANG_STRING.equals(type) && InheritanceUtil.isInheritor(psiType, type));
    }

    @Contract(pure = true)
    @Override
    public boolean methodReferenceMatches(PsiMethodReferenceExpression methodRef) {
      if (methodRef == null) return false;
      String name = methodRef.getReferenceName();
      if (name == null || !myNames.contains(name)) return false;
      PsiMethod method = ObjectUtils.tryCast(methodRef.resolve(), PsiMethod.class);
      return methodMatches(method);
    }

    @Contract(value = "null -> false", pure = true)
    @Override
    public boolean test(PsiMethodCallExpression call) {
      if (call == null) return false;
      String name = call.getMethodExpression().getReferenceName();
      if (name == null || !myNames.contains(name)) return false;
      PsiExpression[] args = call.getArgumentList().getExpressions();
      if (myParameters != null && myParameters.length > 0) {
        if (args.length < myParameters.length - 1) return false;
      }
      PsiMethod method = call.resolveMethod();
      if (method == null) return false;
      PsiParameterList parameterList = method.getParameterList();
      int count = parameterList.getParametersCount();
      if (count > args.length + 1 || (!MethodCallUtils.isVarArgCall(call) && count != args.length)) {
        return false;
      }
      return methodMatches(method);
    }

    private boolean parametersMatch(@NotNull PsiParameterList parameterList) {
      if (myParameters == null) return true;
      if (myParameters.length != parameterList.getParametersCount()) return false;
      return StreamEx.zip(myParameters, parameterList.getParameters(),
                          Simple::parameterTypeMatches).allMatch(Boolean.TRUE::equals);
    }

    private boolean unresolvedArgumentListMatch(@NotNull PsiExpressionList expressionList) {
      if (myParameters == null) return true;
      PsiExpression[] args = expressionList.getExpressions();
      if (myParameters.length == 0 && args.length != 0) return false;
      if (myParameters.length > 0) {
        String lastParameter = myParameters[myParameters.length - 1];
        if (lastParameter != null && lastParameter.endsWith("...")) {
          if (args.length < myParameters.length - 1) return false;
        }
        else {
          if (args.length != myParameters.length) return false;
        }
      }
      for (int i = 0; i < args.length; i++) {
        PsiExpression arg = args[i];
        String parameter;
        if (i < myParameters.length) {
          parameter = myParameters[i];
        }
        else {
          parameter = myParameters[myParameters.length - 1];
        }
        if (!(expressionTypeMatches(parameter, arg) ||
              (myParameters.length - 1 == i && args.length == myParameters.length &&
               parameter.endsWith("...") &&
               expressionTypeMatches(parameter.substring(0, parameter.length() - 3) + "[]", arg)))) return false;
      }
      return true;
    }

    @Override
    @Contract(value = "null -> false", pure = true)
    public boolean methodMatches(@Nullable PsiMethod method) {
      if (method == null) return false;
      if (!myNames.contains(method.getName())) return false;
      PsiClass aClass = method.getContainingClass();
      if (aClass == null) return false;
      return myCallType.matches(aClass, myClassName, method.hasModifierProperty(PsiModifier.STATIC)) &&
             parametersMatch(method.getParameterList());
    }

    @Override
    @Contract(value = "null -> false", pure = true)
    public boolean uCallMatches(@Nullable UCallExpression call) {
      if (call == null) return false;
      if (!call.isMethodNameOneOf(myNames)) return false;
      return methodMatches(call.resolve());
    }

    @Override
    @Contract(value = "null -> false", pure = true)
    public boolean uCallableReferenceMatches(@Nullable UCallableReferenceExpression reference) {
      if (reference == null) return false;
      String name = reference.getCallableName();
      if (!myNames.contains(name)) return false;
      PsiMethod method = ObjectUtils.tryCast(reference.resolve(), PsiMethod.class);
      return methodMatches(method);
    }

    @Override
    public String toString() {
      return myClassName + "." + String.join("|", myNames);
    }

    /**
     * @see Simple#allowStaticUnresolved()
     */
    private class UnresolvedStaticCallMatcher implements CallMatcher{
        @Override
        public Stream<String> names() {
          return Simple.this.names();
        }

        @Override
        public boolean methodReferenceMatches(PsiMethodReferenceExpression methodRef) {
          throw new UnsupportedOperationException("PsiMethodReferenceExpression is not supported");
        }

        @Override
        public boolean test(@Nullable PsiMethodCallExpression call) {
          if (Simple.this.test(call)) return true;
          if (call == null) return false;
          String name = call.getMethodExpression().getReferenceName();
          if (name == null || !myNames.contains(name)) return false;
          if (!unresolvedArgumentListMatch(call.getArgumentList())) return false;
          PsiMethod method = call.resolveMethod();
          if (method != null) return false;
          if(!qualifierMatch(call.getMethodExpression().getQualifierExpression(), call)) return false;
          return true;
        }

        private boolean qualifierMatch(@Nullable PsiExpression expression, @NotNull PsiMethodCallExpression call) {
          StringBuilder referenceName = new StringBuilder();
          if (expression instanceof PsiReferenceExpression qualifierRefExpression) {
            PsiReferenceExpression currentQualifier = qualifierRefExpression;
            while (true) {
              String nextReferenceName = currentQualifier.getReferenceName();
              if (nextReferenceName == null) break;
              if (referenceName.isEmpty()) {
                referenceName = new StringBuilder(nextReferenceName);
              }
              else {
                referenceName.insert(0, nextReferenceName + ".");
              }
              if (currentQualifier.getQualifierExpression() instanceof PsiReferenceExpression referenceExpression) {
                currentQualifier = referenceExpression;
              }
              else {
                break;
              }
            }
          }
          if (myClassName.contentEquals(referenceName)) return true;
          if (myClassName.equals("java.lang." + referenceName)) return true;
          if (!(call.getContainingFile() instanceof PsiJavaFile javaFile)) return false;
          if (javaFile.getPackageStatement() != null) {
            if (myClassName.equals(javaFile.getPackageStatement().getPackageName() + "." + referenceName)) return true;
          }
          List<PsiImportStatementBase> importStatements = new ArrayList<>(ImportsUtil.getAllImplicitImports(javaFile));
          PsiImportList importList = javaFile.getImportList();
          if (importList != null) {
            importStatements.addAll(List.of(importList.getAllImportStatements()));
          }
          for (PsiImportStatementBase statement : importStatements) {
            if (!(statement instanceof PsiImportStaticStatement staticStatement)) continue;
            if (staticStatement.isOnDemand() && staticStatement.getImportReference() != null) {
              if (myClassName.equals(staticStatement.getImportReference().getQualifiedName() + "." + referenceName)) return true;
              if ((referenceName.isEmpty()) && myClassName.equals(staticStatement.getImportReference().getQualifiedName())) return true;
            }
            if (!staticStatement.isOnDemand() && staticStatement.getImportReference() != null) {
              String staticReference = staticStatement.getImportReference().getQualifiedName();
              String shortName = StringUtil.getShortName(staticReference);
              if (shortName.contentEquals(referenceName) ||
                  referenceName.toString().startsWith(shortName + ".")) {
                if (myClassName.equals(StringUtil.getPackageName(staticReference) + "." + referenceName)) return true;
              }
            }
          }
          return false;
        }

        @Override
        public boolean methodMatches(@Nullable PsiMethod method) {
          throw new UnsupportedOperationException("PsiMethod is not supported");
        }

        @Override
        public boolean uCallMatches(@Nullable UCallExpression call) {
          throw new UnsupportedOperationException("UCallExpression is not supported");
        }

        @Override
        public boolean uCallableReferenceMatches(@Nullable UCallableReferenceExpression reference) {
          throw new UnsupportedOperationException("UCallableReferenceExpression is not supported");
        }
      }
  }

  enum CallType {
    STATIC {
      @Override
      boolean matches(PsiClass aClass, String className, boolean isStatic) {
        return isStatic && className.equals(aClass.getQualifiedName());
      }
    },
    ENUM_STATIC {
      @Override
      boolean matches(PsiClass aClass, String className, boolean isStatic) {
        return isStatic && aClass.isEnum();
      }
    },
    INSTANCE {
      @Override
      boolean matches(PsiClass aClass, String className, boolean isStatic) {
        return !isStatic && InheritanceUtil.isInheritor(aClass, className);
      }
    },
    EXACT_INSTANCE {
      @Override
      boolean matches(PsiClass aClass, String className, boolean isStatic) {
        return !isStatic && className.equals(aClass.getQualifiedName());
      }
    };

    abstract boolean matches(PsiClass aClass, String className, boolean isStatic);
  }
}