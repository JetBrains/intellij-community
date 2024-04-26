// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.parser;

import com.intellij.psi.impl.source.AbstractBasicJavaElementTypeFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
abstract public class BasicJavaParser {

  @NotNull
  abstract public BasicFileParser getFileParser();

  @NotNull
  abstract public BasicModuleParser getModuleParser();

  @NotNull
  abstract public BasicDeclarationParser getDeclarationParser();

  @NotNull
  abstract public BasicStatementParser getStatementParser();

  @NotNull
  abstract public BasicExpressionParser getExpressionParser();

  @NotNull
  abstract public BasicReferenceParser getReferenceParser();

  @NotNull
  abstract public BasicPatternParser getPatternParser();

  abstract public AbstractBasicJavaElementTypeFactory getJavaElementTypeFactory();
}