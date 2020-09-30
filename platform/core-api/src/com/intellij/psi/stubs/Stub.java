// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public interface Stub {
  Stub getParentStub();

  @NotNull List<? extends Stub> getChildrenStubs();

  ObjectStubSerializer<?, Stub> getStubType();
}
