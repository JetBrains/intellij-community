/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vfs;

import java.util.EventListener;

public interface ModificationAttemptListener extends EventListener{
  void readOnlyModificationAttempt(ModificationAttemptEvent event);
}
