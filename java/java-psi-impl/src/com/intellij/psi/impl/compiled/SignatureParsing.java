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

/*
 * @author max
 */
package com.intellij.psi.impl.compiled;

import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiTypeParameterListStub;
import com.intellij.psi.impl.java.stubs.PsiTypeParameterStub;
import com.intellij.psi.impl.java.stubs.impl.PsiClassReferenceListStubImpl;
import com.intellij.psi.impl.java.stubs.impl.PsiTypeParameterListStubImpl;
import com.intellij.psi.impl.java.stubs.impl.PsiTypeParameterStubImpl;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.cls.ClsFormatException;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.text.CharacterIterator;
import java.util.ArrayList;
import java.util.Collections;

@SuppressWarnings({"HardCodedStringLiteral"})
public class SignatureParsing {
  private SignatureParsing() {
  }

  public static PsiTypeParameterListStub parseTypeParametersDeclaration(CharacterIterator signatureIterator, StubElement parentStub)
      throws ClsFormatException {
    PsiTypeParameterListStub list = new PsiTypeParameterListStubImpl(parentStub);
    if (signatureIterator.current() == '<') {
      signatureIterator.next();
      while (signatureIterator.current() != '>') {
        parseTypeParameter(signatureIterator, list);
      }
      signatureIterator.next();
    }

    return list;
  }

  private static PsiTypeParameterStub parseTypeParameter(CharacterIterator singatureIterator, PsiTypeParameterListStub parent)
      throws ClsFormatException {
    StringBuffer name = new StringBuffer();
    while (singatureIterator.current() != ':' && singatureIterator.current() != CharacterIterator.DONE) {
      name.append(singatureIterator.current());
      singatureIterator.next();
    }
    if (singatureIterator.current() == CharacterIterator.DONE) {
      throw new ClsFormatException();
    }

    //todo parse annotations on type param
    PsiTypeParameterStub parameterStub = new PsiTypeParameterStubImpl(parent, StringRef.fromString(name.toString()));

    ArrayList<String> bounds = null;
    while (singatureIterator.current() == ':') {
      singatureIterator.next();
      String bound = parseToplevelClassRefSignature(singatureIterator);
      if (bound != null && !bound.equals("java.lang.Object")) {
        if (bounds == null) bounds = new ArrayList<String>();
        bounds.add(bound);
      }
    }

    String[] sbounds = ArrayUtil.toStringArray(bounds == null ? Collections.<String>emptyList() : bounds);
    new PsiClassReferenceListStubImpl(JavaStubElementTypes.EXTENDS_BOUND_LIST, parameterStub, sbounds, PsiReferenceList.Role.EXTENDS_BOUNDS_LIST);

    return parameterStub;
  }

  @Nullable
  public static String parseToplevelClassRefSignature(CharacterIterator signature) throws ClsFormatException {
    if (signature.current() == 'L') {
      return parseParameterizedClassRefSignature(signature);
    }
    if (signature.current() == 'T') {
      return parseTypeVariableRefSignature(signature);
    }
    return null;
  }

  private static String parseTypeVariableRefSignature(CharacterIterator signature) {
    signature.next();
    StringBuffer id = new StringBuffer();
    while (signature.current() != ';' && signature.current() != '>') {
      id.append(signature.current());
      signature.next();
    }

    if (signature.current() == ';') {
      signature.next();
    }

    return id.toString();
  }

  private static String parseParameterizedClassRefSignature(CharacterIterator signature) throws ClsFormatException {
    assert signature.current() == 'L';

    signature.next();
    StringBuffer canonicalText = new StringBuffer();
    while (signature.current() != ';' && signature.current() != CharacterIterator.DONE) {
      switch (signature.current()) {
        case '$':
        case '/':
        case '.':
          canonicalText.append('.');
          break;
        case '<':
          canonicalText.append('<');
          signature.next();
          do {
            processTypeArgument(signature, canonicalText);
          }
          while (signature.current() != '>');
          canonicalText.append('>');
          break;
        case ' ':
          break;
        default:
          canonicalText.append(signature.current());
      }
      signature.next();
    }

    if (signature.current() == CharacterIterator.DONE) {
      throw new ClsFormatException();
    }

    for (int index = 0; index < canonicalText.length(); index++) {
      final char c = canonicalText.charAt(index);
      if ('0' <= c && c <= '1') {
        if (index > 0 && canonicalText.charAt(index - 1) == '.') {
          canonicalText.setCharAt(index - 1, '$');
        }
      }
    }

    signature.next();

    return canonicalText.toString();
  }

  private static void processTypeArgument(CharacterIterator signature, StringBuffer canonicalText) throws ClsFormatException {
    String typeArgument = parseClassOrTypeVariableElement(signature);
    canonicalText.append(typeArgument);
    if (signature.current() != '>') {
      canonicalText.append(',');
    }
  }

  public static String parseClassOrTypeVariableElement(CharacterIterator signature) throws ClsFormatException {
    char variance = parseVariance(signature);
    if (variance == '*') {
      return decorateTypeText("*", variance);
    }

    int arrayCount = 0;
    while (signature.current() == '[') {
      arrayCount++;
      signature.next();
    }

    final String type = parseTypeWithoutVariance(signature);
    if (type != null) {
      String ref = type;
      while (arrayCount > 0) {
        ref += "[]";
        arrayCount--;
      }
      return decorateTypeText(ref, variance);
    }
    else {
      throw new ClsFormatException();
    }
  }

  private static final char VARIANCE_NONE = '\0';
  private static final char VARIANCE_EXTENDS = '+';
  private static final char VARIANCE_SUPER = '-';
  private static final char VARIANCE_INVARIANT = '*';
  @NonNls private static final String VARIANCE_EXTENDS_PREFIX = "? extends ";
  @NonNls private static final String VARIANCE_SUPER_PREFIX = "? super ";

  private static String decorateTypeText(final String canonical, final char variance) {
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
        // fall thru
      default:
        variance = '\0';
    }

    return variance;
  }

  public static String parseTypeString(CharacterIterator signature) throws ClsFormatException {
    int arrayDimensions = 0;
    while (signature.current() == '[') {
      arrayDimensions++;
      signature.next();
    }

    char variance = parseVariance(signature);
    @NonNls String text = parseTypeWithoutVariance(signature);
    if (text == null) throw new ClsFormatException();

    for (int i = 0; i < arrayDimensions; i++) text += "[]";
    if (variance != '\0') {
      text = variance + text;
    }
    return text;
  }

  @Nullable
  private static String parseTypeWithoutVariance(final CharacterIterator signature) throws ClsFormatException {
    final String text;
    switch (signature.current()) {

      case 'L':
        text = parseParameterizedClassRefSignature(signature);
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

      default:
        return null;
    }
    return text;
  }
}
