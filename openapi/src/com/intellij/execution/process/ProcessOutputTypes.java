/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.process;

import com.intellij.openapi.util.Key;

public interface ProcessOutputTypes {
  Key SYSTEM = new Key("system");
  Key STDOUT = new Key("stdout");
  Key STDERR = new Key("stderr");
}
