/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.requests;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcess;
import com.sun.jdi.ReferenceType;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Jun 27, 2003
 * Time: 7:27:41 PM
 * To change this template use Options | File Templates.
 */
public interface ClassPrepareRequestor extends Requestor {
  void processClassPrepare(DebugProcess debuggerProcess, final ReferenceType referenceType);
}
