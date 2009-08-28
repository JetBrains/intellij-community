/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.impl;

import org.jetbrains.annotations.NotNull;

/**
 * @author Alexander Podkhalyuzin
 */
public interface ConsoleInputListener {
  void textEntered(@NotNull String userText);
}
