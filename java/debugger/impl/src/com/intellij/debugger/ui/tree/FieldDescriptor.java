package com.intellij.debugger.ui.tree;

import com.sun.jdi.Field;
import com.sun.jdi.ObjectReference;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public interface FieldDescriptor extends NodeDescriptor{
  Field           getField();
  ObjectReference getObject();
}
