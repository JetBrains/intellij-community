package com.intellij.debugger.engine.jdi;

import com.sun.jdi.ThreadReference;
import com.sun.jdi.ThreadGroupReference;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public interface ThreadGroupReferenceProxy extends ObjectReferenceProxy{
  ThreadGroupReference getThreadGroupReference();
}
