// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler;

import com.intellij.compiler.backwardRefs.CompilerReferenceServiceBase;
import com.intellij.compiler.server.BuildProcessParametersProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexWriter;

import java.util.Collections;
import java.util.List;

final class CompilerReferenceIndexBuildParametersProvider extends BuildProcessParametersProvider {
  @NotNull
  @Override
  public List<String> getVMArguments() {
    return CompilerReferenceServiceBase.isEnabled()
           ? List.of("-D" + JavaBackwardReferenceIndexWriter.PROP_KEY + "=true")
           : Collections.emptyList();
  }
}
