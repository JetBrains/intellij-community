/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.parsing;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.source.ParsingContext;
import com.intellij.util.CharTable;

/**
 * @author ik
 */
public class JavaParsingContext extends ParsingContext {
  private final DeclarationParsing myDeclarationParsing;
  private final ExpressionParsing myExpressionParsing;
  private final ClassBodyParsing myClassBodyParsing;
  private final StatementParsing myStatementParsing;
  private final ImportsTextParsing myImportsParsing;
  private final FileTextParsing myFileTextParsing;
  private final JavadocParsing myJavadocParsing;
  private final LanguageLevel myLanguageLevel;

  public JavaParsingContext(CharTable table, LanguageLevel languageLevel) {
    super(table);
    myLanguageLevel = languageLevel;
    myStatementParsing = new StatementParsing(this);
    myDeclarationParsing = new DeclarationParsing(this);
    myExpressionParsing = new ExpressionParsing(this);
    myClassBodyParsing = new ClassBodyParsing(this);
    myImportsParsing = new ImportsTextParsing(this);
    myFileTextParsing = new FileTextParsing(this);
    myJavadocParsing = new JavadocParsing(this);
  }

  public StatementParsing getStatementParsing() {
    return myStatementParsing;
  }

  public DeclarationParsing getDeclarationParsing() {
    return myDeclarationParsing;
  }

  public ExpressionParsing getExpressionParsing() {
    return myExpressionParsing;
  }

  public ClassBodyParsing getClassBodyParsing() {
    return myClassBodyParsing;
  }

  public ImportsTextParsing getImportsTextParsing() {
    return myImportsParsing;
  }

  public FileTextParsing getFileTextParsing() {
    return myFileTextParsing;
  }

  public JavadocParsing getJavadocParsing() {
    return myJavadocParsing;
  }

  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }
}
