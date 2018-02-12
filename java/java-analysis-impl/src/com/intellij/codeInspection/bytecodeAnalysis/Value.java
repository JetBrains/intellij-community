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
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.codeInspection.bytecodeAnalysis.asm.ASMUtils;
import com.intellij.codeInspection.dataFlow.MethodContract;
import org.jetbrains.org.objectweb.asm.Type;

import java.util.stream.Stream;

enum Value {
  Bot, NotNull, Null, True, False, Fail, Pure, Top;

  static Stream<Value> typeValues(Type type) {
    if(ASMUtils.isReferenceType(type)) {
      return Stream.of(Null, NotNull);
    } else if(ASMUtils.isBooleanType(type)) {
      return Stream.of(True, False);
    }
    return Stream.empty();
  }

  MethodContract.ValueConstraint toValueConstraint() {
    switch (this) {
      case False: return MethodContract.ValueConstraint.FALSE_VALUE;
      case True: return MethodContract.ValueConstraint.TRUE_VALUE;
      case NotNull: return MethodContract.ValueConstraint.NOT_NULL_VALUE;
      case Null: return MethodContract.ValueConstraint.NULL_VALUE;
      case Fail: return MethodContract.ValueConstraint.THROW_EXCEPTION;
      default: return MethodContract.ValueConstraint.ANY_VALUE;
    }
  }
}

