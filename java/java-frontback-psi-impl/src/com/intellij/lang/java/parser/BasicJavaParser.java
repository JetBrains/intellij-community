// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.parser;

import com.intellij.psi.impl.source.AbstractBasicJavaElementTypeFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Use the new Java syntax library instead.
 *             See {@link com.intellij.java.syntax.parser.JavaParser}
 */
@Deprecated
@ApiStatus.Experimental
public abstract class BasicJavaParser {

  public abstract @NotNull BasicFileParser getFileParser();

  public abstract @NotNull BasicModuleParser getModuleParser();

  public abstract @NotNull BasicDeclarationParser getDeclarationParser();

  public abstract @NotNull BasicStatementParser getStatementParser();

  public abstract @NotNull BasicExpressionParser getExpressionParser();

  public abstract @NotNull BasicReferenceParser getReferenceParser();

  public abstract @NotNull BasicPatternParser getPatternParser();

  public abstract AbstractBasicJavaElementTypeFactory getJavaElementTypeFactory();
}