package com.intellij.debugger.ui.tree;

import com.intellij.debugger.engine.jdi.LocalVariableProxy;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public interface LocalVariableDescriptor extends ValueDescriptor{
  LocalVariableProxy getLocalVariable();
}
