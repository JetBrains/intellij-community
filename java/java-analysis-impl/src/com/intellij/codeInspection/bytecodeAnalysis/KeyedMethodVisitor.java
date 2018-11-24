/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.tree.MethodNode;

import static com.intellij.codeInspection.bytecodeAnalysis.Direction.Out;

/**
 * @author peter
 */
abstract class KeyedMethodVisitor extends ClassVisitor {
  private static final int STABLE_FLAGS = Opcodes.ACC_FINAL | Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC;

  KeyedMethodVisitor() {
    super(Opcodes.API_VERSION);
  }

  String className;
  private boolean stableClass;

  @Override
  public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
    className = name;
    stableClass = (access & Opcodes.ACC_FINAL) != 0;
    super.visit(version, access, name, signature, superName, interfaces);
  }

  @Override
  public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
    MethodNode node = new MethodNode(Opcodes.API_VERSION, access, name, desc, signature, exceptions);
    Method method = new Method(className, node.name, node.desc);
    boolean stable = stableClass || (node.access & STABLE_FLAGS) != 0 || "<init>".equals(node.name);

    return visitMethod(node, method, new EKey(method, Out, stable));
  }

  @Nullable
  abstract MethodVisitor visitMethod(final MethodNode node, Method method, final EKey key);
}
