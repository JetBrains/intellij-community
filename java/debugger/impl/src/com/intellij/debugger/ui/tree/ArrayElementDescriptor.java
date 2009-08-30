package com.intellij.debugger.ui.tree;

import com.sun.jdi.ArrayReference;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public interface ArrayElementDescriptor extends NodeDescriptor{
  ArrayReference getArray();
  int getIndex();
}
