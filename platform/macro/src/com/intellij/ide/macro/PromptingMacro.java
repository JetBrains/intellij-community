// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 */
public abstract class PromptingMacro extends Macro{

  @Override
  public final String expand(@NotNull DataContext dataContext) throws ExecutionCancelledException {
    Ref<String> userInput = Ref.create();
    ApplicationManager.getApplication().invokeAndWait(() -> userInput.set(promptUser(dataContext)));
    if (userInput.isNull()) {
      throw new ExecutionCancelledException();
    }
    return userInput.get();
  }


  /**
   * Called from expand() method
   *
   * @return user input. If null is returned, ExecutionCancelledException is thrown by expand() method
   */
  @Nullable
  protected abstract String promptUser(DataContext dataContext);
}
