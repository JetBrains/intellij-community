// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiTypeParameterListStub;
import com.intellij.psi.impl.java.stubs.PsiTypeParameterStub;
import com.intellij.psi.impl.java.stubs.impl.PsiClassReferenceListStubImpl;
import com.intellij.psi.impl.java.stubs.impl.PsiTypeParameterStubImpl;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.cls.ClsFormatException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.TypeReference;

import java.text.CharacterIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class SignatureParsing {
  private SignatureParsing() { }
  
  static class TypeParametersDeclaration {
    static final TypeParametersDeclaration EMPTY = new TypeParametersDeclaration(Collections.emptyList());
    
    private final List<TypeParameterDeclaration> myDeclarations;

    private TypeParametersDeclaration(List<TypeParameterDeclaration> declarations) {
      myDeclarations = declarations;
    }

    TypeInfo getBoundType(TypeReference ref) {
      int typeParameterIndex = ref.getTypeParameterIndex();
      int boundIndex = ref.getTypeParameterBoundIndex();
      if (typeParameterIndex < myDeclarations.size()) {
        TypeParameterDeclaration typeParam = myDeclarations.get(typeParameterIndex);
        if (boundIndex < typeParam.myBounds.length) {
          return typeParam.myBounds[boundIndex];
        }
      }
      return null;
    }

    TypeInfo getParameterType(TypeReference ref) {
      int typeParameterIndex = ref.getTypeParameterIndex();
      if (typeParameterIndex < myDeclarations.size()) {
        return myDeclarations.get(typeParameterIndex).myTypeParameter;
      }
      return null;
    }

    void fillInTypeParameterList(StubElement<?> parent) {
      PsiTypeParameterListStub listStub = parent.findChildStubByType(JavaStubElementTypes.TYPE_PARAMETER_LIST);
      if (listStub == null) return;
      for (TypeParameterDeclaration parameter : this.myDeclarations) {
        parameter.createTypeParameter(listStub);
      }
    }
  }

  private static class TypeParameterDeclaration {
    private final TypeInfo myTypeParameter;
    private final TypeInfo[] myBounds;

    private TypeParameterDeclaration(String parameter, TypeInfo[] bounds) {
      myTypeParameter = new TypeInfo(parameter);
      myBounds = bounds;
    }

    private void createTypeParameter(PsiTypeParameterListStub listStub) {
      PsiTypeParameterStub stub = new PsiTypeParameterStubImpl(listStub, this.myTypeParameter.text);
      myTypeParameter.getTypeAnnotations().createAnnotationStubs(stub);
      TypeInfo[] info = this.myBounds;
      if (info.length > 0 && info[0].text == null) {
        info = Arrays.copyOfRange(info, 1, info.length);
      }
      new PsiClassReferenceListStubImpl(JavaStubElementTypes.EXTENDS_BOUND_LIST, stub, info);
    }
  }

  @NotNull
  static TypeParametersDeclaration parseTypeParametersDeclaration(CharacterIterator signature, Function<? super String, String> mapping) throws ClsFormatException {
    if (signature.current() != '<') {
      return TypeParametersDeclaration.EMPTY;
    }

    List<TypeParameterDeclaration> typeParameters = new ArrayList<>();
    signature.next();
    while (signature.current() != '>') {
      typeParameters.add(parseTypeParameter(signature, mapping));
    }
    signature.next();
    return new TypeParametersDeclaration(typeParameters);
  }

  private static TypeParameterDeclaration parseTypeParameter(CharacterIterator signature, Function<? super String, String> mapping) throws ClsFormatException {
    StringBuilder name = new StringBuilder();
    while (signature.current() != ':' && signature.current() != CharacterIterator.DONE) {
      name.append(signature.current());
      signature.next();
    }
    if (signature.current() == CharacterIterator.DONE) {
      throw new ClsFormatException();
    }
    String parameterName = mapping.fun(name.toString());

    List<TypeInfo> bounds = new SmartList<>();
    while (signature.current() == ':') {
      signature.next();
      String bound = parseTopLevelClassRefSignature(signature, mapping);
      if (!bounds.isEmpty() && bound == null) continue;
      bounds.add(new TypeInfo(bound));
    }

    return new TypeParameterDeclaration(parameterName, bounds.toArray(TypeInfo.EMPTY_ARRAY));
  }

  @Nullable
  public static String parseTopLevelClassRefSignature(CharacterIterator signature, Function<? super String, String> mapping) throws ClsFormatException {
    switch (signature.current()) {
      case 'L':
        return parseParameterizedClassRefSignature(signature, mapping);
      case 'T':
        return parseTypeVariableRefSignature(signature);
      default:
        return null;
    }
  }

  private static String parseTypeVariableRefSignature(CharacterIterator signature) throws ClsFormatException {
    StringBuilder id = new StringBuilder();

    signature.next();
    while (signature.current() != ';' && signature.current() != '>' && signature.current() != CharacterIterator.DONE) {
      id.append(signature.current());
      signature.next();
    }

    if (signature.current() == CharacterIterator.DONE) {
      throw new ClsFormatException();
    }
    if (signature.current() == ';') {
      signature.next();
    }

    return id.toString();
  }

  private static String parseParameterizedClassRefSignature(CharacterIterator signature, Function<? super String, String> mapping) throws ClsFormatException {
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

  private static String parseClassOrTypeVariableElement(CharacterIterator signature, Function<? super String, String> mapping) throws ClsFormatException {
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
  public static String parseTypeString(CharacterIterator signature, Function<? super String, String> mapping) throws ClsFormatException {
    int dimensions = parseDimensions(signature);

    String text = parseTypeWithoutVariance(signature, mapping);
    if (text == null) throw new ClsFormatException();

    if (dimensions > 0) {
      text += StringUtil.repeat("[]", dimensions);
    }

    return text;
  }

  @Nullable
  private static String parseTypeWithoutVariance(CharacterIterator signature, Function<? super String, String> mapping) throws ClsFormatException {
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