/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode;
import org.jetbrains.org.objectweb.asm.tree.InsnList;
import org.jetbrains.org.objectweb.asm.tree.MethodInsnNode;
import org.jetbrains.org.objectweb.asm.tree.MethodNode;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Produces equations for inference of @Contract(pure=true) annotations.
 *
 * This simple analysis infers @Contract(pure=true) only if the method doesn't have following instructions.
 * <ul>
 * <li>PUTFIELD</li>
 * <li>PUTSTATIC</li>
 * <li>IASTORE</li>
 * <li>LASTORE</li>
 * <li>FASTORE</li>
 * <li>DASTORE</li>
 * <li>AASTORE</li>
 * <li>BASTORE</li>
 * <li>CASTORE</li>
 * <li>SASTORE</li>
 * <li>INVOKEDYNAMIC</li>
 * <li>INVOKEINTERFACE</li>
 * </ul>
 *
 * - Nested calls (via INVOKESPECIAL, INVOKESTATIC, INVOKEVIRTUAL) are processed by transitivity.
 * @author lambdamix
 */
public class PurityAnalysis {
  private static final Result<Key, Value> FINAL_TOP = new Final<Key, Value>(Value.Top);
  private static final Result<Key, Value> FINAL_PURE = new Final<Key, Value>(Value.Pure);

  static final int UN_ANALYZABLE_FLAG = Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_INTERFACE;

  @NotNull
  public static Equation<Key, Value> analyze(Method method, MethodNode methodNode, boolean stable) {
    Key key = new Key(method, Direction.Pure, stable);

    if ((methodNode.access & UN_ANALYZABLE_FLAG) != 0) {
      return new Equation<Key, Value>(key, FINAL_TOP);
    }

    InsnList insns = methodNode.instructions;
    // primary keys of invoked methods
    Set<Key> invokedKeys = new HashSet<Key>();
    for (int i = 0; i < insns.size(); i++) {
      AbstractInsnNode insn = insns.get(i);
      switch (insn.getOpcode()) {
        case Opcodes.PUTFIELD:
        case Opcodes.PUTSTATIC:
        case Opcodes.IASTORE:
        case Opcodes.LASTORE:
        case Opcodes.FASTORE:
        case Opcodes.DASTORE:
        case Opcodes.AASTORE:
        case Opcodes.BASTORE:
        case Opcodes.CASTORE:
        case Opcodes.SASTORE:
        case Opcodes.INVOKEDYNAMIC:
        case Opcodes.INVOKEINTERFACE:
          return new Equation<Key, Value>(key, FINAL_TOP);
        case Opcodes.INVOKESPECIAL:
        case Opcodes.INVOKESTATIC:
          invokedKeys.add(new Key(new Method((MethodInsnNode)insn), Direction.Pure, true));
          break;
        case Opcodes.INVOKEVIRTUAL:
          invokedKeys.add(new Key(new Method((MethodInsnNode)insn), Direction.Pure, false));
          break;
        default:
          break;
      }
    }

    if (invokedKeys.isEmpty()) {
      return new Equation<Key, Value>(key, FINAL_PURE);
    }
    else {
      HashSet<Product<Key, Value>> sumOfProducts = new HashSet<Product<Key, Value>>();
      for (Key call : invokedKeys) {
        sumOfProducts.add(new Product<Key, Value>(Value.Top, Collections.singleton(call)));
      }
      return new Equation<Key, Value>(key, new Pending<Key, Value>(sumOfProducts));
    }
  }
}
