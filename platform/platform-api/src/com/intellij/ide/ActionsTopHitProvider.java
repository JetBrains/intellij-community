// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * @author Konstantin Bulenkov
 */
public abstract class ActionsTopHitProvider implements SearchTopHitProvider {
  @Override
  public void consumeTopHits(@NotNull String pattern, @NotNull Consumer<Object> collector, @Nullable Project project) {
    final ActionManager actionManager = ActionManager.getInstance();
    for (String[] strings : getActionsMatrix()) {
      if (StringUtil.isBetween(pattern, strings[0], strings[1])) {
        for (int i = 2; i < strings.length; i++) {
          collector.accept(actionManager.getAction(strings[i]));
        }
      }
    }
  }

  @NotNull
  protected abstract String[][] getActionsMatrix();
}
