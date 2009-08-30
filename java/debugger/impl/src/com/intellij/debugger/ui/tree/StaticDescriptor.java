package com.intellij.debugger.ui.tree;

import com.sun.jdi.ReferenceType;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public interface StaticDescriptor extends NodeDescriptor{
  ReferenceType getType();
}
