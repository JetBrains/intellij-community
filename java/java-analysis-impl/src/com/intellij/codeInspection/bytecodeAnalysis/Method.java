/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.bytecodeAnalysis;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.tree.MethodInsnNode;

import java.security.MessageDigest;

public final class Method implements MethodDescriptor {
  final String internalClassName;
  final String methodName;
  final String methodDesc;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Method method = (Method) o;
    return internalClassName.equals(method.internalClassName) && methodDesc.equals(method.methodDesc) && methodName.equals(method.methodName);
  }

  @Override
  public int hashCode() {
    int result = internalClassName.hashCode();
    result = 31 * result + methodName.hashCode();
    result = 31 * result + methodDesc.hashCode();
    return result;
  }

  /**
   * Primary constructor
   *
   * @param internalClassName class name in asm format
   * @param methodName method name
   * @param methodDesc method descriptor in asm format
   */
  public Method(String internalClassName, String methodName, String methodDesc) {
    this.internalClassName = internalClassName;
    this.methodName = methodName;
    this.methodDesc = methodDesc;
  }

  /**
   * Convenient constructor to convert asm instruction into method key
   *
   * @param mNode asm node from which method key is extracted
   */
  public Method(MethodInsnNode mNode) {
    this.internalClassName = mNode.owner;
    this.methodName = mNode.name;
    this.methodDesc = mNode.desc;
  }

  @NotNull
  @Override
  public HMethod hashed(@Nullable MessageDigest md) {
    return new HMethod(this, md);
  }

  @Override
  public String toString() {
    return internalClassName + ' ' + methodName + ' ' + methodDesc;
  }
}
