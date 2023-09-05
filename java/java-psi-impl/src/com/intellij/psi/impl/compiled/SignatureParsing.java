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
  
  public static final class CharIterator {
    static final int DONE = '\uFFFF';
    private final String myData;
    private final int myEnd;
    private int myPos = 0;

    public CharIterator(String data) {
      myData = data;
      myEnd = data.length();
    }

    char current() {
      if (myPos == myEnd) return DONE;
      return myData.charAt(myPos);
    }
    
    void next() {
      if (myPos < myEnd - 1) {
        ++myPos;
      } else {
        myPos = myEnd;
      }
    }
    
    int pos() {
      return myPos;
    }
    
    String substring(int fromIndex) {
      return myData.substring(fromIndex, myPos);
    }

    @Override
    public String toString() {
      return myData;
    }
  }
  
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
      List<TypeParameterDeclaration> declarations = this.myDeclarations;
      if (declarations.isEmpty()) return;
      PsiTypeParameterListStub listStub = parent.findChildStubByType(JavaStubElementTypes.TYPE_PARAMETER_LIST);
      if (listStub == null) return;
      for (TypeParameterDeclaration parameter : declarations) {
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
  static TypeParametersDeclaration parseTypeParametersDeclaration(CharIterator signature, Function<? super String, String> mapping) throws ClsFormatException {
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

  private static TypeParameterDeclaration parseTypeParameter(CharIterator signature, Function<? super String, String> mapping) throws ClsFormatException {
    int from = signature.pos();
    while (signature.current() != ':' && signature.current() != CharIterator.DONE) {
      signature.next();
    }
    String name = signature.substring(from);
    if (signature.current() == CharIterator.DONE) {
      throw new ClsFormatException();
    }
    String parameterName = mapping.fun(name);

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
  static String parseTopLevelClassRefSignature(CharIterator signature, Function<? super String, String> mapping) throws ClsFormatException {
    switch (signature.current()) {
      case 'L':
        return parseParameterizedClassRefSignature(signature, mapping);
      case 'T':
        return parseTypeVariableRefSignature(signature);
      default:
        return null;
    }
  }

  private static String parseTypeVariableRefSignature(CharIterator signature) throws ClsFormatException {
    signature.next();
    int from = signature.pos();
    while (signature.current() != ';' && signature.current() != '>' && signature.current() != CharIterator.DONE) {
      signature.next();
    }
    String id = signature.substring(from);

    if (signature.current() == CharIterator.DONE) {
      throw new ClsFormatException();
    }
    if (signature.current() == ';') {
      signature.next();
    }

    return id;
  }

  private static String parseParameterizedClassRefSignature(CharIterator signature, Function<? super String, String> mapping) throws ClsFormatException {
    StringBuilder canonicalText = null;

    signature.next();
    int start = signature.pos();
    boolean hasSpace = false;
    while (true) {
      char c = signature.current();
      switch (c) {
        case ';': {
          String javaName;
          if (canonicalText == null) {
            String jvmName = signature.substring(start);
            if (hasSpace) {
              jvmName = jvmName.replace(" ", "");
            }
            javaName = mapping.fun(jvmName);
          }
          else {
            javaName = canonicalText.toString();
          }
          signature.next();
          return javaName;
        }
        case CharIterator.DONE:
          throw new ClsFormatException();
        case '<': {
          if (canonicalText == null) {
            String jvmName = signature.substring(start);
            if (hasSpace) {
              jvmName = jvmName.replace(" ", "");
            }
            String javaName = mapping.fun(jvmName);
            canonicalText = new StringBuilder(javaName);
          }
          boolean firstArg = true;
          signature.next();
          do {
            canonicalText.append(firstArg ? '<' : ',').append(parseClassOrTypeVariableElement(signature, mapping));
            firstArg = false;
          }
          while (signature.current() != '>');
          canonicalText.append('>');
          break;
        }
        case ' ':
          hasSpace = true;
          break;
        default:
          if (canonicalText != null) {
            canonicalText.append(c);
          }
      }
      signature.next();
    }
  }

  private static String parseClassOrTypeVariableElement(CharIterator signature, Function<? super String, String> mapping) throws ClsFormatException {
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

  private static char parseVariance(CharIterator signature) {
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

  private static int parseDimensions(CharIterator signature) {
    int dimensions = 0;
    while (signature.current() == '[') {
      dimensions++;
      signature.next();
    }
    return dimensions;
  }

  /**
   * @deprecated use {@link #parseTypeString(CharIterator, Function)}, it's more optimal
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  @NotNull
  public static String parseTypeString(CharacterIterator signature, Function<? super String, String> mapping) throws ClsFormatException {
    StringBuilder sb = new StringBuilder();
    int pos = signature.getIndex();
    while(true) {
      char ch = signature.current();
      if (ch == CharacterIterator.DONE) {
        break;
      }
      sb.append(ch);
      signature.next();
    }
    CharIterator iterator = new CharIterator(sb.toString());
    String result = parseTypeString(iterator, mapping);
    signature.setIndex(iterator.pos() + pos);
    return result;
  }

  @NotNull
  public static String parseTypeString(CharIterator signature, Function<? super String, String> mapping) throws ClsFormatException {
    int dimensions = parseDimensions(signature);

    String text = parseTypeWithoutVariance(signature, mapping);
    if (text == null) throw new ClsFormatException();

    if (dimensions > 0) {
      text += StringUtil.repeat("[]", dimensions);
    }

    return text;
  }

  @Nullable
  private static String parseTypeWithoutVariance(CharIterator signature, Function<? super String, String> mapping) throws ClsFormatException {
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