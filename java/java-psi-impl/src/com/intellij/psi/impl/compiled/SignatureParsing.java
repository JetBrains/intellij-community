/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.cls.ClsFormatException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.CharacterIterator;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.util.Pair.pair;

/**
 * @author max
 */
public class SignatureParsing {
  private SignatureParsing() { }

  @NotNull
  public static List<Pair<String, String[]>> parseTypeParametersDeclaration(CharacterIterator signature, Function<String, String> mapping) throws ClsFormatException {
    if (signature.current() != '<') {
      return Collections.emptyList();
    }

    List<Pair<String, String[]>> typeParameters = ContainerUtil.newArrayList();
    signature.next();
    while (signature.current() != '>') {
      typeParameters.add(parseTypeParameter(signature, mapping));
    }
    signature.next();
    return typeParameters;
  }

  private static Pair<String, String[]> parseTypeParameter(CharacterIterator signature, Function<String, String> mapping) throws ClsFormatException {
    StringBuilder name = new StringBuilder();
    while (signature.current() != ':' && signature.current() != CharacterIterator.DONE) {
      name.append(signature.current());
      signature.next();
    }
    if (signature.current() == CharacterIterator.DONE) {
      throw new ClsFormatException();
    }
    String parameterName = mapping.fun(name.toString());

    // postpone list allocation till a second bound is seen; ignore sole Object bound
    List<String> bounds = null;
    boolean jlo = false;
    while (signature.current() == ':') {
      signature.next();
      String bound = parseTopLevelClassRefSignature(signature, mapping);
      if (bound == null) continue;
      if (bounds == null) {
        if (CommonClassNames.JAVA_LANG_OBJECT.equals(bound)) {
          jlo = true;
          continue;
        }
        bounds = ContainerUtil.newSmartList();
        if (jlo) {
          bounds.add(CommonClassNames.JAVA_LANG_OBJECT);
        }
      }
      bounds.add(bound);
    }

    return pair(parameterName, ArrayUtil.toStringArray(bounds));
  }

  @Nullable
  public static String parseTopLevelClassRefSignature(CharacterIterator signature, Function<String, String> mapping) throws ClsFormatException {
    switch (signature.current()) {
      case 'L':
        return parseParameterizedClassRefSignature(signature, mapping);
      case 'T':
        return parseTypeVariableRefSignature(signature);
      default:
        return null;
    }
  }

  private static String parseTypeVariableRefSignature(CharacterIterator signature) {
    StringBuilder id = new StringBuilder();

    signature.next();
    while (signature.current() != ';' && signature.current() != '>') {
      id.append(signature.current());
      signature.next();
    }

    if (signature.current() == ';') {
      signature.next();
    }

    return id.toString();
  }

  private static String parseParameterizedClassRefSignature(CharacterIterator signature, Function<String, String> mapping) throws ClsFormatException {
    StringBuilder canonicalText = new StringBuilder();
    boolean mapped = false, firstArg;

    signature.next();
    while (signature.current() != ';' && signature.current() != CharacterIterator.DONE) {
      char c = signature.current();
      if (c == '<') {
        canonicalText = new StringBuilder(mapping.fun(canonicalText.toString()));
        mapped = true;
        firstArg = true;
        signature.next();
        do {
          canonicalText.append(firstArg ? '<' : ',').append(parseClassOrTypeVariableElement(signature, mapping));
          firstArg = false;
        }
        while (signature.current() != '>');
        canonicalText.append('>');
      }
      else if (c != ' ') {
        canonicalText.append(c);
      }
      signature.next();
    }

    if (signature.current() == CharacterIterator.DONE) {
      throw new ClsFormatException();
    }
    signature.next();

    String text = canonicalText.toString();
    if (!mapped) text = mapping.fun(text);
    return text;
  }

  private static String parseClassOrTypeVariableElement(CharacterIterator signature, Function<String, String> mapping) throws ClsFormatException {
    char variance = parseVariance(signature);
    if (variance == '*') {
      return decorateTypeText(null, variance);
    }

    int dimensions = parseDimensions(signature);

    String text = parseTypeWithoutVariance(signature, mapping);
    if (text == null) throw new ClsFormatException();

    if (dimensions > 0) {
      text += StringUtil.repeat("[]", dimensions);
    }

    return decorateTypeText(text, variance);
  }

  private static final char VARIANCE_NONE = '\0';
  private static final char VARIANCE_EXTENDS = '+';
  private static final char VARIANCE_SUPER = '-';
  private static final char VARIANCE_INVARIANT = '*';
  private static final String VARIANCE_EXTENDS_PREFIX = "? extends ";
  private static final String VARIANCE_SUPER_PREFIX = "? super ";

  private static String decorateTypeText(String canonical, char variance) {
    switch (variance) {
      case VARIANCE_NONE:
        return canonical;
      case VARIANCE_EXTENDS:
        return VARIANCE_EXTENDS_PREFIX + canonical;
      case VARIANCE_SUPER:
        return VARIANCE_SUPER_PREFIX + canonical;
      case VARIANCE_INVARIANT:
        return "?";
      default:
        assert false : "unknown variance";
        return null;
    }
  }

  private static char parseVariance(CharacterIterator signature) {
    char variance;
    switch (signature.current()) {
      case '+':
      case '-':
      case '*':
        variance = signature.current();
        signature.next();
        break;
      case '.':
      case '=':
        signature.next();
        // fall through
      default:
        variance = '\0';
    }
    return variance;
  }

  private static int parseDimensions(CharacterIterator signature) {
    int dimensions = 0;
    while (signature.current() == '[') {
      dimensions++;
      signature.next();
    }
    return dimensions;
  }

  @NotNull
  public static String parseTypeString(CharacterIterator signature, Function<String, String> mapping) throws ClsFormatException {
    int dimensions = parseDimensions(signature);

    String text = parseTypeWithoutVariance(signature, mapping);
    if (text == null) throw new ClsFormatException();

    if (dimensions > 0) {
      text += StringUtil.repeat("[]", dimensions);
    }

    return text;
  }

  @Nullable
  private static String parseTypeWithoutVariance(CharacterIterator signature, Function<String, String> mapping) throws ClsFormatException {
    String text = null;

    switch (signature.current()) {
      case 'L':
        text = parseParameterizedClassRefSignature(signature, mapping);
        break;

      case 'T':
        text = parseTypeVariableRefSignature(signature);
        break;

      case 'B':
        text = "byte";
        signature.next();
        break;

      case 'C':
        text = "char";
        signature.next();
        break;

      case 'D':
        text = "double";
        signature.next();
        break;

      case 'F':
        text = "float";
        signature.next();
        break;

      case 'I':
        text = "int";
        signature.next();
        break;

      case 'J':
        text = "long";
        signature.next();
        break;

      case 'S':
        text = "short";
        signature.next();
        break;

      case 'Z':
        text = "boolean";
        signature.next();
        break;

      case 'V':
        text = "void";
        signature.next();
        break;
    }

    return text;
  }
}