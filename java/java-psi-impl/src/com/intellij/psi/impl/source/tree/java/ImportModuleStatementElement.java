// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.impl.source.tree.JavaElementType;

public class ImportModuleStatementElement extends ImportStatementBaseElement {
  public ImportModuleStatementElement() {
    super(JavaElementType.IMPORT_MODULE_STATEMENT);
  }
}
