/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;

/**
 *  @author dsl
 */
public interface ModuleOrderEntry extends ExportableOrderEntry {
  Module getModule();

  String getModuleName();
}
