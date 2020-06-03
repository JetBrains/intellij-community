// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree.render;

import com.intellij.openapi.util.JDOMExternalizable;
import com.sun.jdi.Type;

import java.util.concurrent.CompletableFuture;

public interface Renderer extends Cloneable, JDOMExternalizable {
  String getUniqueId();

  /**
   * Checks whether this renderer is applicable to this type
   * @deprecated override {@link #isApplicableAsync(Type)}
   */
  @Deprecated
  default boolean isApplicable(Type type) {
    throw new AbstractMethodError("Override isApplicableAsync");
  }

  /**
   * Checks whether this renderer is applicable to this type
   */
  default CompletableFuture<Boolean> isApplicableAsync(Type type) {
    return CompletableFuture.completedFuture(isApplicable(type));
  }

  Renderer clone();
}
