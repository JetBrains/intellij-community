// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author gregsh
 */
public abstract class PrintActionHandler {
  public static final ExtensionPointName<PrintActionHandler> EP_NAME = ExtensionPointName.create("com.intellij.printHandler");

  public static @Nullable PrintActionHandler getHandler(@NotNull DataContext dataContext) {
    return JBIterable.from(EP_NAME.getExtensionList()).find(o -> o.canPrint(dataContext));
  }

  public abstract boolean canPrint(@NotNull DataContext dataContext);

  public abstract void print(@NotNull DataContext dataContext);
}
