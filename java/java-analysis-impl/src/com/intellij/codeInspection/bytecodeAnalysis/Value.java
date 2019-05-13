// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.codeInspection.bytecodeAnalysis.asm.ASMUtils;
import com.intellij.codeInspection.dataFlow.ContractReturnValue;
import com.intellij.codeInspection.dataFlow.StandardMethodContract;
import org.jetbrains.org.objectweb.asm.Type;

import java.util.stream.Stream;

enum Value implements Result {
  Bot, NotNull, Null, True, False, Fail, Pure, Top;

  static Stream<Value> typeValues(Type type) {
    if (ASMUtils.isReferenceType(type)) return Stream.of(Null, NotNull);
    if (ASMUtils.isBooleanType(type)) return Stream.of(True, False);
    return Stream.empty();
  }

  ContractReturnValue toReturnValue() {
    switch (this) {
      case False: return ContractReturnValue.returnFalse();
      case True: return ContractReturnValue.returnTrue();
      case NotNull: return ContractReturnValue.returnNotNull();
      case Null: return ContractReturnValue.returnNull();
      case Fail: return ContractReturnValue.fail();
      default: return ContractReturnValue.returnAny();
    }
  }

  StandardMethodContract.ValueConstraint toValueConstraint() {
    switch (this) {
      case False: return StandardMethodContract.ValueConstraint.FALSE_VALUE;
      case True: return StandardMethodContract.ValueConstraint.TRUE_VALUE;
      case NotNull: return StandardMethodContract.ValueConstraint.NOT_NULL_VALUE;
      case Null: return StandardMethodContract.ValueConstraint.NULL_VALUE;
      default: return StandardMethodContract.ValueConstraint.ANY_VALUE;
    }
  }
}