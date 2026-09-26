// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.source.tree.java.RecordHeaderElement;
import com.intellij.psi.tree.ICompositeElementType;
import org.jetbrains.annotations.NotNull;

public class JavaRecordHeaderElementType extends JavaStubElementType implements ICompositeElementType {
  public JavaRecordHeaderElementType() {
    super("RECORD_HEADER");
  }

  @Override
  public @NotNull ASTNode createCompositeNode() {
    return new RecordHeaderElement();
  }
}