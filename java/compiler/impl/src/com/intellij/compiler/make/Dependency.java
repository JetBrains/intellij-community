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
 * created at Jan 4, 2002
 * @author Jeka
 */
package com.intellij.compiler.make;

import com.intellij.compiler.SymbolTable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;

import java.util.*;

public class Dependency {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.make.Dependency");
  public static final Dependency[] EMPTY_ARRAY = new Dependency[0];
  private final int myClassQualifiedName;
  private Set<FieldRef> myUsedFields;
  private Set<MethodRef> myUsedMethods;

  public static class FieldRef {
    public final int name;

    public FieldRef(int name) {
      this.name = name;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      FieldRef fieldRef = (FieldRef)o;

      if (name != fieldRef.name) return false;

      return true;
    }

    public int hashCode() {
      return name;
    }
  }

  public static class MethodRef {
    public final int name;
    public final int descriptor;
    private String[] myParameterDescriptors;

    public MethodRef(int name, int descriptor) {
      this.name = name;
      this.descriptor = descriptor;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      MethodRef methodRef = (MethodRef)o;

      if (descriptor != methodRef.descriptor) return false;
      if (name != methodRef.name) return false;

      return true;
    }

    public int hashCode() {
      int result = name;
      result = 31 * result + descriptor;
      return result;
    }

    public String getDescriptor(SymbolTable symbolTable) throws CacheCorruptedException {
      final String descriptorStr = symbolTable.getSymbol(descriptor);
      final String nameStr = symbolTable.getSymbol(name);
      return CacheUtils.getMethodSignature(nameStr, descriptorStr);
    }


    public String[] getParameterDescriptors(SymbolTable symbolTable) throws CacheCorruptedException {
      if (myParameterDescriptors == null) {
        String descriptorStr = symbolTable.getSymbol(descriptor);
        int endIndex = descriptorStr.indexOf(')');
        if (endIndex <= 0) {
          LOG.error("Corrupted method descriptor: " + descriptorStr);
        }
        myParameterDescriptors = parseParameterDescriptors(descriptorStr.substring(1, endIndex));
      }
      return myParameterDescriptors;
    }
  }

  public Dependency(int classQualifiedName) {
    myClassQualifiedName = classQualifiedName;
  }

  public int getClassQualifiedName() {
    return myClassQualifiedName;
  }

  public void addMethod(int name, int descriptor) {
    if (myUsedMethods == null) {
      myUsedMethods = new HashSet<MethodRef>();
    }
    myUsedMethods.add(new MethodRef(name, descriptor));
  }

  public void addField(int name) {
    if (myUsedFields == null) {
      myUsedFields = new HashSet<FieldRef>();
    }
    myUsedFields.add(new FieldRef(name));
  }

  public Collection<FieldRef> getFieldRefs() {
    return myUsedFields != null? Collections.unmodifiableSet(myUsedFields) : Collections.<FieldRef>emptySet();
  }

  public Collection<MethodRef> getMethodRefs() {
    return myUsedMethods != null? Collections.unmodifiableSet(myUsedMethods) : Collections.<MethodRef>emptySet();
  }

  private static String[] parseParameterDescriptors(String signature) {
    ArrayList<String> list = new ArrayList<String>();
    String paramSignature = parseFieldType(signature);
    while (paramSignature != null && !"".equals(paramSignature)) {
      list.add(paramSignature);
      signature = signature.substring(paramSignature.length());
      paramSignature = parseFieldType(signature);
    }
    return ArrayUtil.toStringArray(list);
  }

  private static String parseFieldType(@NonNls String signature) {
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

}