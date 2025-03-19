// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.compiled;

import com.intellij.psi.impl.cache.ExplicitTypeAnnotationContainer;
import com.intellij.psi.impl.cache.TypeAnnotationContainer;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.cache.TypeInfo.TypeKind;
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

  /**
   * A function to map JVM class names to {@link TypeInfo.RefTypeInfo}.
   * Normally, this function should take into account probable inner classes. This is done by {@link FirstPassData} implementation.
   * If inner classes information is unavailable, use {@link StubBuildingVisitor#GUESSING_PROVIDER} for heuristic-based mapping
   */
  @FunctionalInterface
  public interface TypeInfoProvider {
    @NotNull TypeInfo.RefTypeInfo toTypeInfo(@NotNull String jvmClassName);

    /**
     * @param jvmClassName jvmClassName to check
     * @return true if a given inner class is known to be static
     */
    default boolean isKnownStatic(@NotNull String jvmClassName) {
      return false;
    }

    /**
     * @param fn function that returns Java-style FQN by JVM class name
     * @return a provider that delegates to the supplied function. Note that the returned provider never has the inner classes' information.
     */
    static TypeInfoProvider from(Function<? super String, String> fn) {
      return internalName -> new TypeInfo.RefTypeInfo(fn.apply(internalName));
    }
  }
  
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
      if (myPos < myEnd) {
        ++myPos;
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
      myTypeParameter = new TypeInfo.RefTypeInfo(parameter);
      myBounds = bounds;
    }

    private void createTypeParameter(PsiTypeParameterListStub listStub) {
      PsiTypeParameterStub stub = new PsiTypeParameterStubImpl(listStub, this.myTypeParameter.text());
      TypeAnnotationContainer annotations = myTypeParameter.getTypeAnnotations();
      if (annotations instanceof ExplicitTypeAnnotationContainer) {
        ((ExplicitTypeAnnotationContainer)annotations).createAnnotationStubs(stub);
      }
      TypeInfo[] info = this.myBounds;
      if (info.length > 0 && info[0] == null) {
        info = Arrays.copyOfRange(info, 1, info.length);
      }
      new PsiClassReferenceListStubImpl(JavaStubElementTypes.EXTENDS_BOUND_LIST, stub, info);
    }
  }

  static @NotNull TypeParametersDeclaration parseTypeParametersDeclaration(CharIterator signature, TypeInfoProvider mapping) throws ClsFormatException {
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

  private static TypeParameterDeclaration parseTypeParameter(CharIterator signature, TypeInfoProvider mapping) throws ClsFormatException {
    int from = signature.pos();
    while (signature.current() != ':' && signature.current() != CharIterator.DONE) {
      signature.next();
    }
    String name = signature.substring(from);
    if (signature.current() == CharIterator.DONE) {
      throw new ClsFormatException();
    }
    String parameterName = mapping.toTypeInfo(name).text();

    List<TypeInfo> bounds = new SmartList<>();
    while (signature.current() == ':') {
      signature.next();
      TypeInfo bound = parseTopLevelClassRefSignatureToTypeInfo(signature, mapping);
      if (!bounds.isEmpty() && bound == null) continue;
      bounds.add(bound);
    }

    return new TypeParameterDeclaration(parameterName, bounds.toArray(TypeInfo.EMPTY_ARRAY));
  }

  static @Nullable TypeInfo parseTopLevelClassRefSignatureToTypeInfo(CharIterator signature, TypeInfoProvider mapping) throws ClsFormatException {
    switch (signature.current()) {
      case 'L':
        return parseParameterizedClassRefSignatureToTypeInfo(signature, mapping);
      case 'T':
        return new TypeInfo.RefTypeInfo(parseTypeVariableRefSignature(signature));
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

  private static @NotNull TypeInfo parseParameterizedClassRefSignatureToTypeInfo(CharIterator signature, TypeInfoProvider mapping)
    throws ClsFormatException {
    signature.next();
    int start = signature.pos();
    boolean hasSpace = false;
    while (true) {
      switch (signature.current()) {
        case ';': {
          String jvmName = signature.substring(start);
          if (hasSpace) {
            jvmName = jvmName.replace(" ", "");
          }
          TypeInfo type = mapping.toTypeInfo(jvmName);
          signature.next();
          return type;
        }
        case CharIterator.DONE:
          throw new ClsFormatException("Malformed signature: " + signature);
        case '<': {
          String jvmName = signature.substring(start);
          if (hasSpace) {
            jvmName = jvmName.replace(" ", "");
          }
          TypeInfo.RefTypeInfo type = mapping.toTypeInfo(jvmName);
          signature.next();
          List<TypeInfo> components = new ArrayList<>();
          do {
            components.add(parseClassOrTypeVariableElementToTypeInfo(signature, mapping));
          }
          while (signature.current() != '>');
          type = type.withComponents(components);
          signature.next();
          switch (signature.current()) {
            case ';':
              signature.next();
              return type;
            case '.':
              TypeInfo inner = parseParameterizedClassRefSignatureToTypeInfo(signature, mapping);
              if (!(inner instanceof TypeInfo.RefTypeInfo)) {
                throw new ClsFormatException("Malformed signature: " + signature);
              }
              return ((TypeInfo.RefTypeInfo)inner).withOuter(type);
            default:
              throw new ClsFormatException("Malformed signature: " + signature);
          }
        }
        case ' ':
          hasSpace = true;
          break;
        default:
      }
      signature.next();
    }
  }

  private static @NotNull TypeInfo parseClassOrTypeVariableElementToTypeInfo(@NotNull CharIterator signature, TypeInfoProvider mapping)
    throws ClsFormatException {
    char variance = parseVariance(signature);
    if (variance == '*') {
      return new TypeInfo.SimpleTypeInfo(TypeKind.WILDCARD);
    }

    int dimensions = parseDimensions(signature);

    TypeInfo info = parseTypeWithoutVarianceToTypeInfo(signature, mapping);
    if (info == null) {
      throw new ClsFormatException("Unable to parse signature: " + signature);
    }

    while (dimensions > 0) {
      dimensions--;
      info = info.arrayOf();
    }

    switch (variance) {
      case VARIANCE_NONE:
        return info;
      case VARIANCE_EXTENDS:
        return new TypeInfo.DerivedTypeInfo(TypeKind.EXTENDS, info);
      case VARIANCE_SUPER:
        return new TypeInfo.DerivedTypeInfo(TypeKind.SUPER, info);
      default:
        throw new ClsFormatException("Unable to parse signature: " + signature);
    }
  }

  private static final char VARIANCE_NONE = '\0';
  private static final char VARIANCE_EXTENDS = '+';
  private static final char VARIANCE_SUPER = '-';

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
   * @deprecated use {@link #parseTypeStringToTypeInfo(CharIterator, TypeInfoProvider)}, it's more optimal and produces structured
   * object instead of simple string
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public static @NotNull String parseTypeString(CharacterIterator signature, Function<? super String, String> mapping) throws ClsFormatException {
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
    String result = parseTypeStringToTypeInfo(iterator, TypeInfoProvider.from(mapping)).text();
    signature.setIndex(iterator.pos() + pos);
    return result;
  }

  /**
   * @param signature iterator for signature. It will be moved to the end of parsed signature
   * @param mapping provider to map JVM types to {@link TypeInfo} objects
   * @return a parsed {@link TypeInfo} object
   * @throws ClsFormatException if signature cannot be parsed
   */
  public static @NotNull TypeInfo parseTypeStringToTypeInfo(@NotNull CharIterator signature, @NotNull TypeInfoProvider mapping) throws ClsFormatException {
    int dimensions = parseDimensions(signature);

    TypeInfo type = parseTypeWithoutVarianceToTypeInfo(signature, mapping);
    if (type == null) throw new ClsFormatException();

    while (dimensions > 0) {
      dimensions--;
      type = type.arrayOf();
    }
    return type;
  }

  private static @Nullable TypeInfo parseTypeWithoutVarianceToTypeInfo(CharIterator signature, TypeInfoProvider mapping) throws ClsFormatException {
    switch (signature.current()) {
      case 'L':
        return parseParameterizedClassRefSignatureToTypeInfo(signature, mapping);
      case 'T':
        return new TypeInfo.RefTypeInfo(parseTypeVariableRefSignature(signature));
      case 'B':
        signature.next();
        return new TypeInfo.SimpleTypeInfo(TypeKind.BYTE);
      case 'C':
        signature.next();
        return new TypeInfo.SimpleTypeInfo(TypeKind.CHAR);
      case 'D':
        signature.next();
        return new TypeInfo.SimpleTypeInfo(TypeKind.DOUBLE);
      case 'F':
        signature.next();
        return new TypeInfo.SimpleTypeInfo(TypeKind.FLOAT);
      case 'I':
        signature.next();
        return new TypeInfo.SimpleTypeInfo(TypeKind.INT);
      case 'J':
        signature.next();
        return new TypeInfo.SimpleTypeInfo(TypeKind.LONG);
      case 'S':
        signature.next();
        return new TypeInfo.SimpleTypeInfo(TypeKind.SHORT);
      case 'Z':
        signature.next();
        return new TypeInfo.SimpleTypeInfo(TypeKind.BOOLEAN);
      case 'V':
        signature.next();
        return new TypeInfo.SimpleTypeInfo(TypeKind.VOID);
    }
    return null;
  }
}