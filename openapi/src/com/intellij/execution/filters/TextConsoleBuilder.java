/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.filters;

import com.intellij.execution.ui.ConsoleView;

/**
 * @author dyoma
 */
public abstract class TextConsoleBuilder {
  public abstract ConsoleView getConsole();

  public abstract void addFilter(Filter filter);
}
