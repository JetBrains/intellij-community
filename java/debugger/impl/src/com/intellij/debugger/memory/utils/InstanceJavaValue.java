/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.debugger.memory.utils;

import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InstanceJavaValue extends JavaValue {
  public InstanceJavaValue(@NotNull ValueDescriptorImpl valueDescriptor,
                           @NotNull EvaluationContextImpl evaluationContext,
                           NodeManagerImpl nodeManager) {
    super(null, valueDescriptor, evaluationContext, nodeManager, false);
  }

  @Nullable
  @Override
  public String getEvaluationExpression() {
    ObjectReference ref = ((ObjectReference)getDescriptor().getValue());
    return NamesUtils.getUniqueName(ref);
  }

  @Override
  public boolean canNavigateToSource() {
    return false;
  }
}
