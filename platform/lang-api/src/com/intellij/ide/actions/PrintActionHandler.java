// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Nullable
  public static PrintActionHandler getHandler(@NotNull DataContext dataContext) {
    return JBIterable.from(EP_NAME.getExtensionList()).find(o -> o.canPrint(dataContext));
  }

  public abstract boolean canPrint(@NotNull DataContext dataContext);

  public abstract void print(@NotNull DataContext dataContext);
}
