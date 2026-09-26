// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events.impl;

import com.intellij.build.events.SuccessResult;
import com.intellij.build.events.Warning;

import java.util.Collections;
import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public final class SuccessResultImpl implements SuccessResult {

  private final boolean myUpToDate;

  public SuccessResultImpl() {
    this(false);
  }

  public SuccessResultImpl(boolean isUpToDate) {
    myUpToDate = isUpToDate;
  }

  @Override
  public boolean isUpToDate() {
    return myUpToDate;
  }

  @Override
  public List<? extends Warning> getWarnings() {
    return Collections.emptyList();
  }
}
