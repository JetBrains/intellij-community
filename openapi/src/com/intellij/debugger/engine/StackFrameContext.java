/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.engine;

import com.intellij.debugger.engine.jdi.StackFrameProxy;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Jun 3, 2003
 * Time: 5:58:58 PM
 * To change this template use Options | File Templates.
 */
public interface StackFrameContext {
  StackFrameProxy  getFrameProxy  ();
  DebugProcess     getDebugProcess();
}
