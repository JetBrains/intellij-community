// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.psi.*;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Represents method mutation signature
 */
public class MutationSignature {
  public static final String ATTR_MUTATES = "mutates";
  static final MutationSignature UNKNOWN = new MutationSignature(false, new boolean[0]);
  static final MutationSignature PURE = new MutationSignature(false, new boolean[0]);
  public static final String INVALID_TOKEN_MESSAGE = "Invalid token: %s; supported are 'this', 'param1', 'param2', etc.";
  private final boolean myThis;
  private final boolean[] myParameters;

  private MutationSignature(boolean mutatesThis, boolean[] params) {
    myThis = mutatesThis;
    myParameters = params;
  }

  /**
   * @return true if the instance method may mutate this object
   */
  public boolean mutatesThis() {
    return myThis;
  }

  /**
   * @param n argument number (zero-based)
   * @return true if the method may mutate given argument
   */
  public boolean mutatesArg(int n) {
    return n < myParameters.length && myParameters[n];
  }

  /**
   * @return true if the method is static or never mutates this object
   */
  public boolean preservesThis() {
    return this != UNKNOWN && !myThis;
  }

  /**
   * @param n argument number (zero-based)
   * @return true if the method never mutates given argument
   */
  public boolean preservesArg(int n) {
    return this != UNKNOWN && !mutatesArg(n);
  }

  /**
   * @return a signature that is equivalent to this signature but may also mutate this object
   */
  public MutationSignature alsoMutatesThis() {
    return myThis ? this : new MutationSignature(true, myParameters);
  }

  /**
   * @param n argument number (zero-based)
   * @return a signature that is equivalent to this signature but may also mutate n-th argument
   */
  public MutationSignature alsoMutatesArg(int n) {
    if (myParameters.length > n && myParameters[n]) return this;
    boolean[] params = Arrays.copyOf(myParameters, Math.max(n + 1, myParameters.length));
    params[n] = true;
    return new MutationSignature(myThis, params);
  }

  /**
   * Returns a stream of expressions which are mutated by given signature assuming that supplied call
   * resolves to the method with this signature.
   *
   * @param call a call which resolves to the method with this mutation signature
   * @return a stream of expressions which are mutated by this call. If qualifier is omitted, but mutated,
   * a non-physical {@link PsiThisExpression} might be returned.
   */
  public Stream<PsiExpression> mutatedExpressions(PsiMethodCallExpression call) {
    PsiExpression[] args = call.getArgumentList().getExpressions();
    StreamEx<PsiExpression> elements =
      IntStreamEx.range(Math.min(myParameters.length, MethodCallUtils.isVarArgCall(call) ? args.length - 1 : args.length))
        .filter(idx -> myParameters[idx]).elements(args);
    if (myThis) {
      PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(call.getMethodExpression());
      if (qualifier != null) {
        return elements.prepend(qualifier);
      }
    }
    return elements;
  }

  /**
   * @return true if known to mutate any parameter or receiver; false if pure or not known
   */
  public boolean mutatesAnything() {
    if (myThis) return true;
    for (boolean parameter : myParameters) {
      if (parameter) return true;
    }
    return false;
  }

  /**
   * @param signature to parse
   * @return a parsed mutation signature
   * @throws IllegalArgumentException if signature is invalid
   */
  public static MutationSignature parse(@NotNull String signature) {
    if (signature.trim().isEmpty()) {
      return UNKNOWN;
    }
    boolean mutatesThis = false;
    boolean[] args = {};
    for (String part : signature.split(",")) {
      part = part.trim();
      if (part.equals("this")) {
        mutatesThis = true;
      }
      else if (part.equals("param")) {
        if (args.length == 0) {
          args = new boolean[] {true};
        } else {
          args[0] = true;
        }
      }
      else if (part.startsWith("param")) {
        int argNum = Integer.parseInt(part.substring("param".length()));
        if (argNum < 0 || argNum > 255) {
          throw new IllegalArgumentException(String.format(INVALID_TOKEN_MESSAGE, part));
        }
        if(args.length < argNum) {
          args = Arrays.copyOf(args, argNum);
        }
        args[argNum-1] = true;
      }
      else if (!part.isEmpty()) {
        throw new IllegalArgumentException(String.format(INVALID_TOKEN_MESSAGE, part));
      }
    }
    return new MutationSignature(mutatesThis, args);
  }

  /**
   * Checks the mutation signature
   * @param signature signature to check
   * @param method a method to apply the signature
   * @return error message or null if signature is valid
   */
  public static @Nullable String checkSignature(@NotNull String signature, @NotNull PsiMethod method) {
    try {
      MutationSignature ms = parse(signature);
      if (ms.myThis && method.hasModifierProperty(PsiModifier.STATIC)) {
        return "Static method cannot mutate 'this'";
      }
      PsiParameter[] parameters = method.getParameterList().getParameters();
      if (ms.myParameters.length > parameters.length) {
        return "Reference to parameter #" + ms.myParameters.length + " is invalid";
      }
      for (int i = 0; i < ms.myParameters.length; i++) {
        if (ms.myParameters[i]) {
          PsiType type = parameters[i].getType();
          if (ClassUtils.isImmutable(type)) {
            return "Parameter #" + (i + 1) + " has immutable type '" + type.getPresentableText() + "'";
          }
        }
      }
    }
    catch (IllegalArgumentException ex) {
      return ex.getMessage();
    }
    return null;
  }

  public static @NotNull MutationSignature fromMethod(@Nullable PsiMethod method) {
    if (method == null) return UNKNOWN;
    return JavaMethodContractUtil.getContractInfo(method).getMutationSignature();
  }

  /**
   * @return a signature of the pure method, which doesn't mutate anything
   */
  public static @NotNull MutationSignature pure() {
    return PURE;
  }

  /**
   * @return a signature of the unknown method, which may mutate anything
   */
  public static @NotNull MutationSignature unknown() {
    return UNKNOWN;
  }
}
