// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.bytecodeAnalysis;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.tree.MethodInsnNode;

public final class Member implements MemberDescriptor {
  final @NonNls String internalClassName;
  final @NonNls String methodName;
  final @NonNls String methodDesc;

  /**
   * Primary constructor
   *
   * @param internalClassName class name in asm format
   * @param methodName method name
   * @param methodDesc method descriptor in asm format
   */
  public Member(@NotNull @NonNls String internalClassName, @NotNull @NonNls String methodName, @NotNull @NonNls String methodDesc) {
    this.internalClassName = internalClassName;
    this.methodName = methodName;
    this.methodDesc = methodDesc;
  }

  /**
   * Convenient constructor to convert asm instruction into method key
   *
   * @param mNode asm node from which method key is extracted
   */
  public Member(MethodInsnNode mNode) {
    this.internalClassName = mNode.owner;
    this.methodName = mNode.name;
    this.methodDesc = mNode.desc;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Member method = (Member) o;
    return internalClassName.equals(method.internalClassName) && methodDesc.equals(method.methodDesc) && methodName.equals(method.methodName);
  }

  @Override
  public int hashCode() {
    int result = internalClassName.hashCode();
    result = 31 * result + methodName.hashCode();
    result = 31 * result + methodDesc.hashCode();
    return result;
  }

  @Override
  public @NotNull HMember hashed() {
    return new HMember(this);
  }

  @Override
  public String toString() {
    return internalClassName + ' ' + methodName + ' ' + methodDesc;
  }
}