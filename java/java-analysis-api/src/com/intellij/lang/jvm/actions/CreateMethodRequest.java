// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface CreateMethodRequest extends CreateExecutableRequest {

  @NotNull
  String getMethodName();

  @NotNull
  List<ExpectedType> getReturnType();
}
