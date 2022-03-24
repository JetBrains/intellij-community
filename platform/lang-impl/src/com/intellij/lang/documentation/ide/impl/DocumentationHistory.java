// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.impl;

import com.intellij.util.concurrency.annotations.RequiresEdt;

/**
 * The entity which documentation history actions are acting upon.
 */
public interface DocumentationHistory {

  @RequiresEdt
  boolean canBackward();

  @RequiresEdt
  void backward();

  @RequiresEdt
  boolean canForward();

  @RequiresEdt
  void forward();
}
