/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.java.parser;

import com.intellij.psi.impl.source.AbstractBasicJavaElementTypeFactory;
import com.intellij.psi.impl.source.tree.JavaElementTypeFactory;
import org.jetbrains.annotations.NotNull;

public class JavaParser extends BasicJavaParser {
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

  @Override
  @NotNull
  public FileParser getFileParser() {
    return myFileParser;
  }

  @Override
  @NotNull
  public ModuleParser getModuleParser() {
    return myModuleParser;
  }

  @Override
  @NotNull
  public DeclarationParser getDeclarationParser() {
    return myDeclarationParser;
  }

  @Override
  @NotNull
  public StatementParser getStatementParser() {
    return myStatementParser;
  }

  @Override
  @NotNull
  public ExpressionParser getExpressionParser() {
    return myExpressionParser;
  }

  @Override
  @NotNull
  public ReferenceParser getReferenceParser() {
    return myReferenceParser;
  }

  @Override
  @NotNull
  public PatternParser getPatternParser() {
    return myPatternParser;
  }

  @Override
  public AbstractBasicJavaElementTypeFactory getJavaElementTypeFactory() {
    return JavaElementTypeFactory.INSTANCE;
  }
}