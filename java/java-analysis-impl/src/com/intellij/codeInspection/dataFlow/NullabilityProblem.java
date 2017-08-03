package com.intellij.codeInspection.dataFlow;

/**
 * @author peter
 */
public enum NullabilityProblem {
  callNPE,
  fieldAccessNPE,
  unboxingNullable,
  assigningToNotNull,
  nullableReturn,
  nullableFunctionReturn,
  passingNullableToNotNullParameter,
  passingNullableArgumentToNonAnnotatedParameter
}
