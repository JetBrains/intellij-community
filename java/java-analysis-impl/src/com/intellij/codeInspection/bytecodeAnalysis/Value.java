// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.codeInspection.bytecodeAnalysis.asm.ASMUtils;
import com.intellij.codeInspection.dataFlow.ContractReturnValue;
import com.intellij.codeInspection.dataFlow.StandardMethodContract;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicValue;

import java.util.List;

enum Value implements Result {
  Bot, NotNull, Null, True, False, Fail, Pure, Top;
  static final List<Value> BOOLEAN = List.of(True, False);
  static final List<Value> OBJECT = List.of(Null, NotNull);

  static List<Value> typeValues(Type type) {
    if (ASMUtils.isReferenceType(type)) return OBJECT;
    if (ASMUtils.isBooleanType(type)) return BOOLEAN;
    return List.of();
  }

  static @Nullable Value fromBasicValue(BasicValue value) {
    if (value == AbstractValues.TrueValue) {
      return True;
    }
    if (value == AbstractValues.FalseValue) {
      return False;
    }
    if (value == AbstractValues.NullValue) {
      return Null;
    }
    if (value instanceof AbstractValues.NotNullValue) {
      return NotNull;
    }
    return null;
  }

  ContractReturnValue toReturnValue() {
    return switch (this) {
      case False -> ContractReturnValue.returnFalse();
      case True -> ContractReturnValue.returnTrue();
      case NotNull -> ContractReturnValue.returnNotNull();
      case Null -> ContractReturnValue.returnNull();
      case Fail -> ContractReturnValue.fail();
      default -> ContractReturnValue.returnAny();
    };
  }

  StandardMethodContract.ValueConstraint toValueConstraint() {
    return switch (this) {
      case False -> StandardMethodContract.ValueConstraint.FALSE_VALUE;
      case True -> StandardMethodContract.ValueConstraint.TRUE_VALUE;
      case NotNull -> StandardMethodContract.ValueConstraint.NOT_NULL_VALUE;
      case Null -> StandardMethodContract.ValueConstraint.NULL_VALUE;
      default -> StandardMethodContract.ValueConstraint.ANY_VALUE;
    };
  }
}