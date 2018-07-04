// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions;

public interface ActionRequest {

  /**
   * Request may be bound to the PSI-element in call-site language,
   * which means request will become invalid if element is invalidated.
   *
   * @return {@code true} if it is safe to call other methods of this request, {@code false} otherwise
   */
  boolean isValid();
}
