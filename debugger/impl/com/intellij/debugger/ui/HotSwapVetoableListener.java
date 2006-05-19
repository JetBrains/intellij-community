/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.debugger.ui;

import com.intellij.openapi.compiler.CompileContext;

/**
 * @author nik
 */
public interface HotSwapVetoableListener {

  boolean shouldHotSwap(CompileContext finishedCompilationContext);

}
