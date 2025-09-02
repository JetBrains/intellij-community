// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.org.objectweb.asm.Opcodes;

public final class JVMFlags {
  public static final JVMFlags EMPTY = new JVMFlags(0);

  // using the highes 4th byte
  private static final int LOCAL_MASK = 0x1000000;
  private static final int ANON_MASK = 0x2000000;
  private static final int GENERATED_MASK = 0x4000000;
  private static final int SEALED_MASK = 0x8000000;
  private static final int LIBRARY_MASK = 0x10000000;

  private final int myFlags;

  public JVMFlags(int access) {
    myFlags = access;
  }

  public JVMFlags deriveIsLocal() {
    return new JVMFlags(myFlags | LOCAL_MASK);
  }

  public JVMFlags deriveIsAnonymous() {
    return new JVMFlags(myFlags | ANON_MASK);
  }

  public JVMFlags deriveIsGenerated() {
    return new JVMFlags(myFlags | GENERATED_MASK);
  }

  public JVMFlags deriveIsSealed() {
    return new JVMFlags(myFlags | SEALED_MASK);
  }

  public JVMFlags deriveIsLibrary() {
    return new JVMFlags(myFlags | LIBRARY_MASK);
  }

  public JVMFlags deriveAdded(JVMFlags past) {
    return new JVMFlags(~past.myFlags & myFlags);
  }

  public JVMFlags deriveRemoved(JVMFlags past) {
    return new JVMFlags(~myFlags & past.myFlags);
  }

  public boolean isWeakerAccess(JVMFlags than) {
    return (isPrivate() && !than.isPrivate()) || (isProtected() && than.isPublic()) || (isPackageLocal() && (than.myFlags & (Opcodes.ACC_PROTECTED | Opcodes.ACC_PUBLIC)) != 0);
  }

  // standard access flags
  public boolean isPublic() {
    return isSet(Opcodes.ACC_PUBLIC);
  }

  public boolean isPrivate() {
    return isSet(Opcodes.ACC_PRIVATE);
  }

  public boolean isProtected() {
    return isSet(Opcodes.ACC_PROTECTED);
  }

  public boolean isPackageLocal() {
    return (myFlags & (Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_PUBLIC)) == 0;
  }

  public boolean isStatic() {
    return isSet(Opcodes.ACC_STATIC);
  }

  public boolean isFinal() {
    return isSet(Opcodes.ACC_FINAL);
  }

  public boolean isSuper() {
    return isSet(Opcodes.ACC_SUPER);
  }

  public boolean isSynchronized() {
    return isSet(Opcodes.ACC_SYNCHRONIZED);
  }

  public boolean isOpen() {
    return isSet(Opcodes.ACC_OPEN);
  }

  public boolean isTransitive() {
    return isSet(Opcodes.ACC_TRANSITIVE);
  }

  public boolean isVolatile() {
    return isSet(Opcodes.ACC_VOLATILE);
  }

  public boolean isBridge() {
    return isSet(Opcodes.ACC_BRIDGE);
  }

  public boolean isStaticPhase() {
    return isSet(Opcodes.ACC_STATIC_PHASE);
  }

  public boolean isVarargs() {
    return isSet(Opcodes.ACC_VARARGS);
  }

  public boolean isTransient() {
    return isSet(Opcodes.ACC_TRANSIENT);
  }

  public boolean isNative() {
    return isSet(Opcodes.ACC_NATIVE);
  }

  public boolean isInterface() {
    return isSet(Opcodes.ACC_INTERFACE);
  }

  public boolean isAbstract() {
    return isSet(Opcodes.ACC_ABSTRACT);
  }

  public boolean isStrict() {
    return isSet(Opcodes.ACC_STRICT);
  }

  public boolean isSynthetic() {
    return isSet(Opcodes.ACC_SYNTHETIC);
  }

  public boolean isAnnotation() {
    return isSet(Opcodes.ACC_ANNOTATION);
  }

  boolean isEnum() {
    return isSet(Opcodes.ACC_ENUM);
  }

  public boolean isMandated() {
    return isSet(Opcodes.ACC_MANDATED);
  }

  public boolean isModule() {
    return isSet(Opcodes.ACC_MODULE);
  }

  // ASM access flags
  public boolean isRecord() {
    return isSet(Opcodes.ACC_RECORD);
  }

  public boolean isDeprecated() {
    return isSet(Opcodes.ACC_DEPRECATED);
  }

  // JPS access flags

  public boolean isLocal() {
    return isSet(LOCAL_MASK);
  }

  public boolean isAnonymous() {
    return isSet(ANON_MASK);
  }

  public boolean isGenerated() {
    return isSet(GENERATED_MASK);
  }

  public boolean isSealed() {
    return isSet(SEALED_MASK);
  }

  public boolean isLibrary() {
    return isSet(LIBRARY_MASK);
  }

  public boolean isAllSet(JVMFlags flags) {
    return (myFlags & flags.myFlags) == flags.myFlags;
  }

  private boolean isSet(int mask) {
    return (myFlags & mask) != 0;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final JVMFlags jvmFlags = (JVMFlags)o;

    if (myFlags != jvmFlags.myFlags) {
      return false;
    }

    return true;
  }

  public int getValue() {
    return myFlags;
  }

  @Override
  public int hashCode() {
    return myFlags;
  }
}
