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

/**
 * created at Jan 10, 2002
 * @author Jeka
 */
package com.intellij.compiler.classParsing;

import com.intellij.compiler.SymbolTable;
import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.compiler.make.CacheUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import com.intellij.util.cls.ClsUtil;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NonNls;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;

public class MethodInfo extends MemberInfo {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.classParsing.MethodInfo");

  private static final int[] EXCEPTION_INFO_UNAVAILABLE = ArrayUtil.EMPTY_INT_ARRAY;
  public static final MethodInfo[] EMPTY_ARRAY = new MethodInfo[0];

  private final int[] myThrownExceptions;
  // cached (lazy initialized) data
  private String mySignature = null;
  private String[] myParameterDescriptors = null;
  private String myReturnTypeSignature = null;
  private final boolean myIsConstructor;
  private final AnnotationConstantValue[][] myRuntimeVisibleParameterAnnotations;
  private final AnnotationConstantValue[][] myRuntimeInvisibleParameterAnnotations;
  private final ConstantValue myAnnotationDefault;

  public MethodInfo(int name, int descriptor, boolean isConstructor) {
    super(name, descriptor);
    myIsConstructor = isConstructor;
    myThrownExceptions = EXCEPTION_INFO_UNAVAILABLE;
    myRuntimeVisibleParameterAnnotations = AnnotationConstantValue.EMPTY_ARRAY_ARRAY;
    myRuntimeInvisibleParameterAnnotations = AnnotationConstantValue.EMPTY_ARRAY_ARRAY;
    myAnnotationDefault = ConstantValue.EMPTY_CONSTANT_VALUE;
  }

  public MethodInfo(int name,
                    int descriptor,
                    final int genericSignature,
                    int flags,
                    int[] exceptions,
                    boolean isConstructor,
                    final AnnotationConstantValue[] runtimeVisibleAnnotations,
                    final AnnotationConstantValue[] runtimeInvisibleAnnotations,
                    final AnnotationConstantValue[][] runtimeVisibleParameterAnnotations,
                    final AnnotationConstantValue[][] runtimeInvisibleParameterAnnotations, ConstantValue annotationDefault) {

    super(name, descriptor, genericSignature, flags, runtimeVisibleAnnotations, runtimeInvisibleAnnotations);
    myThrownExceptions = exceptions != null? exceptions : ArrayUtil.EMPTY_INT_ARRAY;
    myIsConstructor = isConstructor;
    myRuntimeVisibleParameterAnnotations = runtimeVisibleParameterAnnotations == null? AnnotationConstantValue.EMPTY_ARRAY_ARRAY : runtimeVisibleParameterAnnotations; 
    myRuntimeInvisibleParameterAnnotations = runtimeInvisibleParameterAnnotations == null? AnnotationConstantValue.EMPTY_ARRAY_ARRAY : runtimeInvisibleParameterAnnotations;
    myAnnotationDefault = annotationDefault;
  }

  public MethodInfo(DataInput in) throws IOException {
    super(in);
    myIsConstructor = in.readBoolean();
    int count = in.readInt();
    if (count == -1) {
      myThrownExceptions = EXCEPTION_INFO_UNAVAILABLE;
    }
    else {
      myThrownExceptions = ArrayUtil.newIntArray(count);
      for (int idx = 0; idx < count; idx++) {
        myThrownExceptions[idx] = in.readInt();
      }
    }
    myRuntimeVisibleParameterAnnotations = loadParameterAnnotations(in);
    myRuntimeInvisibleParameterAnnotations = loadParameterAnnotations(in);
    myAnnotationDefault = MemberInfoExternalizer.loadConstantValue(in);
  }

  public void save(DataOutput out) throws IOException {
    super.save(out);
    out.writeBoolean(myIsConstructor);
    if (isExceptionInfoAvailable()) {
      out.writeInt(myThrownExceptions.length);
    }
    else {
      out.writeInt(-1);
    }
    for (int thrownException : myThrownExceptions) {
      out.writeInt(thrownException);
    }
    saveParameterAnnotations(out, myRuntimeVisibleParameterAnnotations);
    saveParameterAnnotations(out, myRuntimeInvisibleParameterAnnotations);
    MemberInfoExternalizer.saveConstantValue(out, myAnnotationDefault);
  }

  private boolean isExceptionInfoAvailable() {
    return myThrownExceptions != EXCEPTION_INFO_UNAVAILABLE;
  }

  public boolean areExceptionsEqual(MethodInfo info) {
    if (myThrownExceptions.length != info.myThrownExceptions.length) {
      return false;
    }
    if (myThrownExceptions.length != 0) { // optimization
      TIntHashSet exceptionsSet = new TIntHashSet();
      for (int thrownException : myThrownExceptions) {
        exceptionsSet.add(thrownException);
      }
      for (int exception : info.myThrownExceptions) {
        if (!exceptionsSet.contains(exception)) {
          return false;
        }
      }
    }
    return true;
  }

  public int[] getThrownExceptions() {
    return myThrownExceptions;
  }

  public String getDescriptor(SymbolTable symbolTable) throws CacheCorruptedException {
    if (mySignature == null) {
      final String descriptor = symbolTable.getSymbol(getDescriptor());
      final String name = symbolTable.getSymbol(getName());
      mySignature = CacheUtils.getMethodSignature(name, descriptor);
    }
    return mySignature;
  }

  public String getReturnTypeDescriptor(SymbolTable symbolTable) throws CacheCorruptedException {
    if (myReturnTypeSignature == null) {
      String descriptor = symbolTable.getSymbol(getDescriptor());
      myReturnTypeSignature = descriptor.substring(descriptor.indexOf(')') + 1, descriptor.length());
    }
    return myReturnTypeSignature;
  }

  public String[] getParameterDescriptors(SymbolTable symbolTable) throws CacheCorruptedException {
    if (myParameterDescriptors == null) {
      String descriptor = symbolTable.getSymbol(getDescriptor());
      int endIndex = descriptor.indexOf(')');
      if (endIndex <= 0) {
        LOG.error("Corrupted method descriptor: " + descriptor);
      }
      myParameterDescriptors = parseParameterDescriptors(descriptor.substring(1, endIndex));
    }
    return myParameterDescriptors;
  }

  public boolean isAbstract() {
    return ClsUtil.isAbstract(getFlags());
  }

  public boolean isConstructor() {
    return myIsConstructor;
  }

  private String[] parseParameterDescriptors(String signature) {
    ArrayList<String> list = new ArrayList<String>();
    String paramSignature = parseFieldType(signature);
    while (paramSignature != null && !"".equals(paramSignature)) {
      list.add(paramSignature);
      signature = signature.substring(paramSignature.length());
      paramSignature = parseFieldType(signature);
    }
    return ArrayUtil.toStringArray(list);
  }

  private @NonNls String parseFieldType(@NonNls String signature) {
    if (signature.length() == 0) {
      return null;
    }
    if (signature.charAt(0) == 'B') {
      return "B";
    }
    if (signature.charAt(0) == 'C') {
      return "C";
    }
    if (signature.charAt(0) == 'D') {
      return "D";
    }
    if (signature.charAt(0) == 'F') {
      return "F";
    }
    if (signature.charAt(0) == 'I') {
      return "I";
    }
    if (signature.charAt(0) == 'J') {
      return "J";
    }
    if (signature.charAt(0) == 'S') {
      return "S";
    }
    if (signature.charAt(0) == 'Z') {
      return "Z";
    }
    if (signature.charAt(0) == 'L') {
      return signature.substring(0, signature.indexOf(";") + 1);
    }
    if (signature.charAt(0) == '[') {
      String s = parseFieldType(signature.substring(1));
      return (s != null)? ("[" + s) : null;
    }
    return null;
  }

  public AnnotationConstantValue[][] getRuntimeVisibleParameterAnnotations() {
    return myRuntimeVisibleParameterAnnotations;
  }

  public AnnotationConstantValue[][] getRuntimeInvisibleParameterAnnotations() {
    return myRuntimeInvisibleParameterAnnotations;
  }

  public String toString() {
    return mySignature;
  }

  private AnnotationConstantValue[][] loadParameterAnnotations(DataInput in) throws IOException {
    final int size = in.readInt();
    if (size == 0) {
      return AnnotationConstantValue.EMPTY_ARRAY_ARRAY;
    }
    final AnnotationConstantValue[][] paramAnnotations = new AnnotationConstantValue[size][];
    for (int idx = 0; idx < size; idx++) {
      paramAnnotations[idx] = loadAnnotations(in);
    }
    return paramAnnotations;
  }

  private void saveParameterAnnotations(DataOutput out, AnnotationConstantValue[][] parameterAnnotations) throws IOException {
    out.writeInt(parameterAnnotations.length);
    for (AnnotationConstantValue[] parameterAnnotation : parameterAnnotations) {
      saveAnnotations(out, parameterAnnotation);
    }
  }

  public ConstantValue getAnnotationDefault() {
    return myAnnotationDefault;
  }

}
