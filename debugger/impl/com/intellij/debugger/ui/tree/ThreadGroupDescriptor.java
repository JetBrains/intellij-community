package com.intellij.debugger.ui.tree;

import com.intellij.debugger.engine.jdi.ThreadGroupReferenceProxy;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public interface ThreadGroupDescriptor extends NodeDescriptor{
  ThreadGroupReferenceProxy getThreadGroupReference();
}
