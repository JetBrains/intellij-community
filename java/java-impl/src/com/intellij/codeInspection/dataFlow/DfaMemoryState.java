/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.value.DfaRelationValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jul 16, 2003
 * Time: 10:25:44 PM
 * To change this template use Options | File Templates.
 */
public interface DfaMemoryState {
  DfaMemoryState createCopy();

  DfaValue pop();
  DfaValue peek();
  void push(@NotNull DfaValue value);

  int popOffset();
  void pushOffset(int offset);

  void emptyStack();

  void setVarValue(DfaVariableValue var, DfaValue value);

  boolean applyInstanceofOrNull(DfaRelationValue dfaCond);

  boolean applyCondition(DfaValue dfaCond);

  boolean applyNotNull(DfaValue value);

  void flushFields(DataFlowRunner runner);

  void flushVariable(DfaVariableValue variable);

  boolean isNull(DfaValue dfaVar);

  boolean checkNotNullable(DfaValue value);

  boolean canBeNaN(DfaValue dfaValue);

  boolean isNotNull(DfaVariableValue dfaVar);
}
