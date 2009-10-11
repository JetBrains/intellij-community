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
package com.intellij.compiler.classParsing;

import com.intellij.openapi.compiler.CompilerBundle;

import java.text.CharacterIterator;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 4, 2004
 */

public class SignatureParser {
  public static final SignatureParser INSTANCE = new SignatureParser();

  public void parseIdentifier(CharacterIterator it, final StringBuilder buf) {
    while (Character.isJavaIdentifierPart(it.current())) {
      buf.append(it.current());
      it.next();
    }
  }

  public void parseFormalTypeParameters(CharacterIterator it, final StringBuilder buf) throws SignatureParsingException {
    if (it.current() != '<') {
      throw new SignatureParsingException(CompilerBundle.message("error.signature.parsing.expected.other.symbol", "<", buf.toString()));
    }

    buf.append(it.current()); // skip '<'
    it.next();

    while (it.current() != '>') {
      parseFormalTypeParameter(it, buf);
    }

    buf.append(it.current());
    it.next();
  }

  public void parseFormalTypeParameter(CharacterIterator it, final StringBuilder buf) throws SignatureParsingException {
    parseIdentifier(it, buf);
    parseClassBound(it, buf);
    while (it.current() == ':') {
      parseInterfaceBound(it, buf);
    }
  }

  public void parseClassBound(CharacterIterator it, final StringBuilder buf) throws SignatureParsingException {
    if (it.current() != ':') {
      throw new SignatureParsingException(CompilerBundle.message("error.signature.parsing.expected.other.symbol", ":", buf.toString()));
    }
    buf.append(it.current());
    it.next();

    final char current = it.current();
    if (current != CharacterIterator.DONE && current != ':') {
      parseFieldTypeSignature(it, buf);
    }
  }

  public void parseInterfaceBound(CharacterIterator it, final StringBuilder buf) throws SignatureParsingException {
    if (it.current() != ':') {
      throw new SignatureParsingException(CompilerBundle.message("error.signature.parsing.expected.other.symbol", ":", buf.toString()));
    }
    buf.append(it.current());
    it.next();
    parseFieldTypeSignature(it, buf);
  }

  public void parseSuperclassSignature(CharacterIterator it, final StringBuilder buf) throws SignatureParsingException {
    parseClassTypeSignature(it, buf);
  }

  public void parseSuperinterfaceSignature(CharacterIterator it, final StringBuilder buf) throws SignatureParsingException {
    parseClassTypeSignature(it, buf);
  }

  public void parseFieldTypeSignature(CharacterIterator it, final StringBuilder buf) throws SignatureParsingException {
    if (it.current() == 'L') {
      parseClassTypeSignature(it, buf);
    }
    else if (it.current() == '[') {
      parseArrayTypeSignature(it, buf);
    }
    else if (it.current() == 'T') {
      parseTypeVariableSignature(it, buf);
    }
    else {
      //noinspection HardCodedStringLiteral
      throw new SignatureParsingException(CompilerBundle.message("error.signature.parsing.expected.other.symbol", "'L' / '[' / 'T'", buf.toString()));
    }
  }

  public void parseClassTypeSignature(CharacterIterator it, final StringBuilder buf) throws SignatureParsingException {
    buf.append(it.current());
    it.next();     // consume 'L'
    parseSimpleClassTypeSignature(it, buf);
    while (it.current() == '/' || it.current() == '.') { // Eclipse compiler generates '.' for inner classes 
      parseClassTypeSignatureSuffix(it, buf);
    }
    if (it.current() != ';') {
      throw new SignatureParsingException(CompilerBundle.message("error.signature.parsing.expected.other.symbol", ";", buf.toString()));
    }
    buf.append(it.current());
    it.next(); // consume ';'
  }

  public void parseSimpleClassTypeSignature(CharacterIterator it, final StringBuilder buf) throws SignatureParsingException {
    parseIdentifier(it, buf);
    if (it.current() == '<') {
      parseTypeArguments(it, buf);
    }
  }

  public void parseClassTypeSignatureSuffix(CharacterIterator it, final StringBuilder buf) throws SignatureParsingException {
    buf.append(it.current());
    it.next();
    parseSimpleClassTypeSignature(it, buf);
  }

  public void parseTypeVariableSignature(CharacterIterator it, final StringBuilder buf) throws SignatureParsingException {
    buf.append(it.current());
    it.next(); // consume 'T'
    parseIdentifier(it, buf);
    if (it.current() != ';') {
      throw new SignatureParsingException(CompilerBundle.message("error.signature.parsing.expected.other.symbol", ";", buf.toString()));
    }
    buf.append(it.current());
    it.next(); // consume ';'
  }

  public void parseTypeArguments(CharacterIterator it, final StringBuilder buf) throws SignatureParsingException {
    buf.append(it.current());
    it.next(); // consume '<'
    while (it.current() != '>') {
      parseTypeArgument(it, buf);
    }
    buf.append(it.current());
    it.next(); // consume '>'
  }

  public void parseTypeArgument(CharacterIterator it, final StringBuilder buf) throws SignatureParsingException {
    if (it.current() == '*') {
      parseWildcardIndicator(it, buf);
    }
    else {
      if (it.current() == '+' || it.current() == '-') {
        parseWildcardIndicator(it, buf);
      }
      parseFieldTypeSignature(it, buf);
    }
  }

  public void parseWildcardIndicator(CharacterIterator it, final StringBuilder buf) {
    buf.append(it.current());
    it.next();
  }

  public void parseArrayTypeSignature(CharacterIterator it, final StringBuilder buf) throws SignatureParsingException {
    buf.append(it.current());
    it.next(); // consume '['
    parseTypeSignature(it, buf);
  }

  public void parseTypeSignature(CharacterIterator it, final StringBuilder buf) throws SignatureParsingException {
    char current = it.current();
    if (current == 'B' || current == 'C' || current == 'D' || current == 'F' || current == 'I' || current == 'J' || current == 'S' || current == 'Z') {
      buf.append(it.current());
      it.next(); // base type
    }
    else if (current == 'L' || current == '[' || current == 'T') {
      parseFieldTypeSignature(it, buf);
    }
    else {
      throw new SignatureParsingException(CompilerBundle.message("error.signature.parsing.unknown.type.signature"));
    }
  }

  public void parseReturnType(CharacterIterator it, final StringBuilder buf) throws SignatureParsingException {
    if (it.current() == 'V') {
      buf.append(it.current());
      it.next();
    }
    else {
      parseTypeSignature(it, buf);
    }
  }

  public void parseThrowsSignature(CharacterIterator it, final StringBuilder buf) throws SignatureParsingException {
    if (it.current() != '^') {
      throw new SignatureParsingException(CompilerBundle.message("error.signature.parsing.expected.other.symbol", "^", buf.toString()));
    }
    buf.append(it.current());
    it.next();
    if (it.current() == 'T') {
      parseTypeVariableSignature(it, buf);
    }
    else {
      parseClassTypeSignature(it, buf);
    }
  }

  public void parseMethodSignature(CharacterIterator it, final StringBuilder buf) throws SignatureParsingException {
    if (it.current() == '<') {
      parseFormalTypeParameters(it, buf);
    }

    if (it.current() != '(') {
      throw new SignatureParsingException(CompilerBundle.message("error.signature.parsing.expected.other.symbol", "(", buf.toString()));
    }

    buf.append(it.current());
    it.next(); // skip '('

    while (it.current() != ')') {
      parseTypeSignature(it, buf);
    }

    buf.append(it.current());
    it.next(); // skip ')'

    parseReturnType(it, buf);

    if (it.current() != CharacterIterator.DONE) {
      parseThrowsSignature(it, buf);
    }
  }

  public void parseClassSignature(CharacterIterator it, final StringBuilder buf) throws SignatureParsingException {
    if (it.current() == '<') {
      buf.append(it.current());
      it.next();
      while (it.current() != '>') {
        parseFormalTypeParameter(it, buf);
      }
      buf.append(it.current());
      it.next();
    }

    parseClassTypeSignature(it, buf);

    while (it.current() != CharacterIterator.DONE) {
      parseClassTypeSignature(it, buf);
    }
  }
}
