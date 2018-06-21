// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UCallableReferenceExpression;
import org.jetbrains.uast.UExpression;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Please, DO NOT use this interface in plugins until <code>@Experimental</code> is removed.
 * Probably this interface will be extended in future, which will break its implementations.
 *
 * @see com.siyeh.ig.callMatcher.CallMatcher
 */
@ApiStatus.Experimental
public interface UastCallMatcher {

  @Contract("null -> false")
  boolean testCallExpression(@Nullable UCallExpression expression);

  @Contract("null -> false")
  boolean testCallableReferenceExpression(@Nullable UCallableReferenceExpression expression);


  @NotNull
  static Builder builder() {
    return new Builder();
  }

  @NotNull
  static UastCallMatcher anyOf(@NotNull UastCallMatcher... matchers) {
    return new UastCallMatcher() {
      @Override
      public boolean testCallExpression(@Nullable UCallExpression expression) {
        return Arrays.stream(matchers).anyMatch(matcher -> matcher.testCallExpression(expression));
      }

      @Override
      public boolean testCallableReferenceExpression(@Nullable UCallableReferenceExpression expression) {
        return Arrays.stream(matchers).anyMatch(matcher -> matcher.testCallableReferenceExpression(expression));
      }
    };
  }





  //TODO support primitive types for receiver/return types and arguments
  //TODO support static methods
  class SimpleUastCallMatcher implements UastCallMatcher {
    // for all fields 'null' = doesn't matter

    private final String myMethodName;
    /**
     * array length is arguments count; each element is argument type FQN
     */
    private final String[] myArguments;
    private final boolean myMatchArgumentTypeInheritors;
    private final String myReturnTypeClassFqn;
    /**
     * FQN of receiver type class (for method calls)  or  class/object type class (for method references).
     */
    private final String myClassFqn;


    public SimpleUastCallMatcher(@Nullable String methodName,
                                 @Nullable String[] arguments,
                                 boolean matchArgumentTypeInheritors,
                                 @Nullable String classFqn,
                                 @Nullable String returnTypeClassFqn) {
      if (methodName == null &&
          arguments == null &&
          classFqn == null &&
          returnTypeClassFqn == null) {
        throw new IllegalArgumentException("At least one qualifier must be specified");
      }
      myMethodName = methodName;
      myArguments = arguments;
      myMatchArgumentTypeInheritors = matchArgumentTypeInheritors;
      myClassFqn = classFqn;
      myReturnTypeClassFqn = returnTypeClassFqn;
    }

    @Override
    public boolean testCallExpression(@Nullable UCallExpression expression) {
      if (expression == null || expression.getMethodName() == null) return false; // null method name for constructor calls
      return methodNameMatches(expression) &&
             classMatches(expression) &&
             returnTypeMatches(expression) &&
             argumentsMatch(expression);
    }

    @Override
    public boolean testCallableReferenceExpression(@Nullable UCallableReferenceExpression expression) {
      if (expression == null) return false;
      return methodNameMatches(expression) &&
             classMatches(expression) &&
             returnTypeMatches(expression) &&
             argumentsMatch(expression);
    }


    private boolean methodNameMatches(@NotNull UCallExpression expression) {
      return myMethodName == null ||
             myMethodName.equals(expression.getMethodName());
    }

    private boolean methodNameMatches(@NotNull UCallableReferenceExpression expression) {
      return myMethodName == null ||
             myMethodName.equals(expression.getCallableName());
    }

    private boolean classMatches(@NotNull UCallExpression expression) {
      return myClassFqn == null ||
             myClassFqn.equals(AnalysisUastUtil.getExpressionReceiverTypeClassFqn(expression));
    }

    private boolean classMatches(@NotNull UCallableReferenceExpression expression) {
      if (myClassFqn == null) return true;
      return false; //TODO implement
    }

    private boolean returnTypeMatches(@NotNull UCallExpression expression) {
      return myReturnTypeClassFqn == null ||
             myReturnTypeClassFqn.equals(AnalysisUastUtil.getExpressionReturnTypePsiClassFqn(expression));
    }

    private boolean returnTypeMatches(@NotNull UCallableReferenceExpression expression) {
      if (myReturnTypeClassFqn == null) return true;
      return false; //TODO implement
    }

    private boolean argumentsMatch(@NotNull UCallExpression expression) {
      if (myArguments == null) return true;
      if (myArguments.length != expression.getValueArgumentCount()) {
        return false;
      }

      List<UExpression> argumentExpressions = null;
      for (int i = 0; i < myArguments.length; i++) {
        String requiredArgumentTypeClassFqn = myArguments[i];
        if (requiredArgumentTypeClassFqn == null) continue;
        if (argumentExpressions == null) {
          argumentExpressions = expression.getValueArguments();
        }

        UExpression argumentExpression = argumentExpressions.get(i);
        PsiType argumentExpressionType = argumentExpression.getExpressionType();
        if (requiredArgumentTypeClassFqn.equals(AnalysisUastUtil.getTypeClassFqn(argumentExpressionType))) {
          continue;
        }
        if (!myMatchArgumentTypeInheritors) {
          return false;
        }

        PsiClass argumentExpressionTypeClass = AnalysisUastUtil.getTypePsiClass(argumentExpressionType);
        if (argumentExpressionTypeClass == null) return false;

        //TODO probably this can be optimized using BFS
        LinkedHashSet<PsiClass> expressionTypeSupers = InheritanceUtil.getSuperClasses(argumentExpressionTypeClass);
        boolean argumentMatches = false;
        for (PsiClass expressionTypeSuper : expressionTypeSupers) {
          if (requiredArgumentTypeClassFqn.equals(expressionTypeSuper.getQualifiedName())) {
            argumentMatches = true;
            break;
          }
        }
        if (!argumentMatches) return false;
      }
      return true;
    }

    private boolean argumentsMatch(@NotNull UCallableReferenceExpression expression) {
      if (myArguments == null) return true;
      return true; //TODO implement (seems like it only has meaning for static method references)
    }
  }


  /**
   * Builder for {@link SimpleUastCallMatcher}. At least one qualifier must be specified.
   *
   * Please note that {@link #withArgumentsCount(int)} and {@link #withArgumentTypes(String...)} cannot be used
   * at the same time (only the last call will have an effect).
   */
  class Builder {
    private String myMethodName;
    private String[] myArguments;
    private boolean myMatchArgumentTypeInheritors = true;
    private String myClassFqn;
    private String myReturnTypeClassFqn;

    @NotNull
    public Builder withMethodName(@NotNull String methodName) {
      myMethodName = methodName;
      return this;
    }

    @NotNull
    public Builder withClassFqn(@NotNull String classFqn) {
      myClassFqn = classFqn;
      return this;
    }

    @NotNull
    public Builder withReturnType(@NotNull String returnTypeClassFqn) {
      myReturnTypeClassFqn = returnTypeClassFqn;
      return this;
    }

    @NotNull
    public Builder withArgumentsCount(int argumentsCount) {
      myArguments = new String[argumentsCount];
      return this;
    }

    @NotNull
    public Builder withArgumentTypes(@NotNull String... arguments) {
      myArguments = arguments;
      return this;
    }

    @NotNull
    public Builder withMatchArgumentTypeInheritors(boolean matchArgumentTypeInheritors) {
      myMatchArgumentTypeInheritors = matchArgumentTypeInheritors;
      return this;
    }

    @NotNull
    public UastCallMatcher build() {
      return new SimpleUastCallMatcher(myMethodName,
                                       myArguments,
                                       myMatchArgumentTypeInheritors,
                                       myClassFqn,
                                       myReturnTypeClassFqn);
    }
  }
}
