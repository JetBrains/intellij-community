/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.compiler.classFilesIndex.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.java.dependencyView.Mappings;
import org.jetbrains.jps.classFilesIndex.AsmUtil;
import org.jetbrains.jps.classFilesIndex.indexer.impl.EnumeratedMethodIncompleteSignature;

import java.util.Comparator;

/**
 * @author Dmitry Batkovich
 */
public class MethodIncompleteSignature {
  public static final String CONSTRUCTOR_METHOD_NAME = "<init>";

  @NotNull
  private final String myOwner;
  @NotNull
  private final String myReturnType;
  @NotNull
  private final String myName;
  private final boolean myStatic;

  private MethodIncompleteSignature(@NotNull final String owner, @NotNull final String returnType, @NotNull final String name, final boolean aStatic) {
    myOwner = owner;
    myReturnType = returnType;
    myName = name;
    myStatic = aStatic;
  }

  public static MethodIncompleteSignature denumerated(final EnumeratedMethodIncompleteSignature sign, final  String returnType, final Mappings mappings) {
    return new MethodIncompleteSignature(AsmUtil.getQualifiedClassName(mappings.valueOf(sign.getOwner())), returnType, mappings.valueOf(sign.getName()), sign.isStatic());
  }

  @NotNull
  public String getOwner() {
    return myOwner;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getReturnType() {
    return myReturnType;
  }

  public boolean isStatic() {
    return myStatic;
  }

  @Override
  public String toString() {
    return "MethodIncompleteSignature{" +
           "myOwner='" + myOwner + '\'' +
           ", myReturnType='" + myReturnType + '\'' +
           ", myName='" + myName + '\'' +
           ", myStatic=" + myStatic +
           '}';
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || !(o instanceof MethodIncompleteSignature)) return false;

    final MethodIncompleteSignature that = (MethodIncompleteSignature)o;

    if (myStatic != that.myStatic) return false;
    if (!myName.equals(that.myName)) return false;
    if (!myOwner.equals(that.myOwner)) return false;
    if (!myReturnType.equals(that.myReturnType)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myOwner.hashCode();
    result = 31 * result + myReturnType.hashCode();
    result = 31 * result + myName.hashCode();
    result = 31 * result + (myStatic ? 1 : 0);
    return result;
  }
}