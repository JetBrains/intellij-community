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
package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

/**
 * Extension of {@link TypedActionHandler} that can supply an action plan before actually executing an action.
 * <p>
 * The generated action plan is used to perform preemptive rendering in editor before acquiring a write lock.
 *
 * @see TypedAction#beforeActionPerformed(Editor, char, DataContext, ActionPlan)
 * @see TypedActionHandler#execute(Editor, char, DataContext)
 * @see ActionPlan
 */
public interface TypedActionHandlerEx extends TypedActionHandler {
  /**
   * The method is invoked before acquiring a write lock and actually executing an action.
   * Expected to draft an action plan that will be used as a base for zero-latency rendering in editor.
   * <p>
   * There's no need to mirror all the oncoming changes, focus on latency-sensitive activity (like typing).
   * <p>
   * Implementation should not modify entities besides {@link ActionPlan} (e.g. {@link Editor}, {@link Document}, {@link CaretModel}, etc.).
   * Moreover, as the handlers can be chained, implementation must rely primarily on the state that is provided by the {@link ActionPlan}
   * (i.e. {@link ActionPlan#getText()} must be used instead of a direct {@link Document#getText()} call).
   * <p>
   * The handler is responsible for delegating to the previously registered handler if needed.
   *
   * @param editor  the editor in which the key was typed.
   * @param c       the typed character.
   * @param context the current data context.
   * @param plan    the current action plan draft.
   */
  void beforeExecute(@NotNull Editor editor, char c, @NotNull DataContext context, @NotNull ActionPlan plan);
}
