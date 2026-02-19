// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.parser;

import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Use {@link com.intellij.java.syntax.parser.JavaParser} instead
 */
@Deprecated
public class JavaParser {
  public static final JavaParser INSTANCE = new JavaParser();

  private final FileParser myFileParser;
  private final ModuleParser myModuleParser;
  private final DeclarationParser myDeclarationParser;
  private final StatementParser myStatementParser;
  private final ExpressionParser myExpressionParser;
  private final ReferenceParser myReferenceParser;
  private final PatternParser myPatternParser;

  public JavaParser() {
    myFileParser = new FileParser(this);
    myModuleParser = new ModuleParser(this);
    myDeclarationParser = new DeclarationParser(this);
    myStatementParser = new StatementParser(this);
    myExpressionParser = new ExpressionParser(this);
    myReferenceParser = new ReferenceParser(this);
    myPatternParser = new PatternParser(this);
  }

  public @NotNull FileParser getFileParser() {
    return myFileParser;
  }

  public @NotNull ModuleParser getModuleParser() {
    return myModuleParser;
  }

  public @NotNull DeclarationParser getDeclarationParser() {
    return myDeclarationParser;
  }

  public @NotNull StatementParser getStatementParser() {
    return myStatementParser;
  }

  public @NotNull ExpressionParser getExpressionParser() {
    return myExpressionParser;
  }

  public @NotNull ReferenceParser getReferenceParser() {
    return myReferenceParser;
  }

  public @NotNull PatternParser getPatternParser() {
    return myPatternParser;
  }
}