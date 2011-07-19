/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * Defines contract for renderer that performs the job (rendering) by delegating to another object.
 * <p/>
 * Example: we can render {@link ArrayList} by delegating to its backed array. The array is a <code>'delegate object'</code> here
 * and its renderer is exposed via the current interface.
 * 
 * @author Denis Zhdanov
 * @since 7/18/11 12:14 PM
 */
public interface DelegatingChildrenRenderer extends ChildrenRenderer {

  /**
   * @param evaluationContext  evaluation context to use
   * @param descriptor         target node descriptor
   * @param value              target value for which <code>'delegate object'</code> can be determined
   * @return                   renderer fo the <code>'delegate object'</code> if the one exists; <code>null</code> otherwise
   */
  @Nullable
  ChildrenRenderer getDelegate(@NotNull EvaluationContext evaluationContext, @NotNull NodeDescriptor descriptor, @NotNull Value value);

  /**
   * Instructs to use given renderer when <code>'delegate object'</code> has given type. 
   * 
   * @param evaluationContext    evaluation context to use
   * @param descriptor           target node descriptor
   * @param value                target value for which <code>'delegate object'</code> given custom renderer should be used
   * @param renderer             delegating renderer to use
   * @return                     <code>true</code> if given custom renderer is applied; <code>false</code> otherwise
   */
  boolean setDelegate(@NotNull EvaluationContext evaluationContext, @NotNull NodeDescriptor descriptor, @NotNull Value value,
                      @NotNull ChildrenRenderer renderer);
}
