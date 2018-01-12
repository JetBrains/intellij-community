// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class MutationSignature {
  private static final String ATTR_MUTATES = "mutates";
  private static final String CONTRACT_ANNOTATION = "org.jetbrains.annotations.Contract";
  private static final MutationSignature UNKNOWN = new MutationSignature(false, new boolean[0]);
  private static final MutationSignature PURE = new MutationSignature(false, new boolean[0]);
  public static final String INVALID_TOKEN_MESSAGE = "Invalid token: %s; supported are 'this', 'arg1', 'arg2', etc.";
  private final boolean myThis;
  private final boolean[] myArgs;

  private MutationSignature(boolean mutatesThis, boolean[] args) {
    myThis = mutatesThis;
    myArgs = args;
  }

  public boolean mutatesThis() {
    return myThis;
  }

  public boolean mutatesArg(int n) {
    return n < myArgs.length && myArgs[n];
  }

  public boolean preservesThis() {
    return this != UNKNOWN && !myThis;
  }

  public boolean preservesArg(int n) {
    return this != UNKNOWN && !mutatesArg(n);
  }

  /**
   * @param signature to parse
   * @return a parsed mutation signature
   * @throws IllegalArgumentException if signature is invalid
   */
  public static MutationSignature parse(String signature) {
    boolean mutatesThis = false;
    boolean[] args = {};
    for (String part : signature.split(",")) {
      part = part.trim();
      if (part.equals("this")) {
        mutatesThis = true;
      }
      else if (part.equals("arg")) {
        if (args.length == 0) {
          args = new boolean[] {true};
        } else {
          args[0] = true;
        }
      }
      else if (part.startsWith("arg")) {
        int argNum = Integer.parseInt(part.substring("arg".length()));
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
  @Nullable
  public static String checkSignature(@NotNull String signature, @NotNull PsiMethod method) {
    try {
      MutationSignature ms = parse(signature);
      if (ms.myThis && method.hasModifierProperty(PsiModifier.STATIC)) {
        return "Static method cannot mutate 'this'";
      }
      if (ms.myArgs.length > method.getParameterList().getParametersCount()) {
        return "Reference to argument #" + ms.myArgs.length + " is invalid";
      }
    }
    catch (IllegalArgumentException ex) {
      return ex.getMessage();
    }
    return null;
  }

  @NotNull
  public static MutationSignature fromMethod(@Nullable PsiMethod method) {
    if (method == null) return UNKNOWN;
    PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, CONTRACT_ANNOTATION);
    if (annotation == null) return UNKNOWN;
    PsiAnnotationMemberValue value = annotation.findAttributeValue(ATTR_MUTATES);
    if (value instanceof PsiLiteralExpression) {
      Object text = ((PsiLiteralExpression)value).getValue();
      if (text instanceof String) {
        try {
          return parse((String)text);
        }
        catch (IllegalArgumentException ignored) { }
      }
    }
    if(ControlFlowAnalyzer.isPure(method)) {
      return PURE;
    }
    return UNKNOWN;
  }
}
