/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.command;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Nls;
import com.intellij.openapi.project.Project;

/**
 * @author max
 */
public abstract class CommandProcessorEx extends CommandProcessor {
  public abstract void enterModal();
  public abstract void leaveModal();

  @Nullable
  public abstract Object startCommand(Project project, @Nls String name, Object groupId, UndoConfirmationPolicy undoConfirmationPolicy);

  public abstract void finishCommand(Project project, final Object command, Throwable throwable);
}
