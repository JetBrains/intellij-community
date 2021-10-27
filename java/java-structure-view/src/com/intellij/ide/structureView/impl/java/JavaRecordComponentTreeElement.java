// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.psi.PsiRecordComponent;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public class JavaRecordComponentTreeElement extends JavaVariableBaseTreeElement<PsiRecordComponent> {
  public JavaRecordComponentTreeElement(PsiRecordComponent field, boolean isInherited) {
    super(isInherited, field);
  }

  @Override
  public @NotNull Collection<StructureViewTreeElement> getChildrenBase() {
    return Collections.emptyList();
  }
}
