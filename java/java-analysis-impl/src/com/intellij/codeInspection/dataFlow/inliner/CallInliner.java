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
package com.intellij.codeInspection.dataFlow.inliner;

import com.intellij.codeInspection.dataFlow.ControlFlowAnalyzer;
import com.intellij.psi.PsiMethodCallExpression;

/**
 * A CallInliner can recognize specific method calls and inline their implementation into current CFG
 */
public interface CallInliner {
  /**
   * Try to inline the supplied call
   *
   * @param builder a builder to use for inlining. Current state is before given method call (call arguments and qualifier are not
   *                handled yet).
   * @param call    a call to inline
   * @return true if inlining is successful. In this case subsequent inliners are skipped and default processing is omitted.
   * If false is returned, inliner must not emit any instructions via builder.
   */
  boolean tryInlineCall(ControlFlowAnalyzer.CFGBuilder builder, PsiMethodCallExpression call);
}
