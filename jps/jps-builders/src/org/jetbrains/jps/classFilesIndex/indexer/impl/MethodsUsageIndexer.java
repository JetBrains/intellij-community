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
package org.jetbrains.jps.classFilesIndex.indexer.impl;

import com.intellij.util.containers.FactoryMap;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.asm4.*;
import org.jetbrains.jps.classFilesIndex.AsmUtil;
import org.jetbrains.jps.classFilesIndex.TObjectIntHashMapExternalizer;
import org.jetbrains.jps.classFilesIndex.indexer.api.ClassFileIndexer;
import org.jetbrains.jps.classFilesIndex.indexer.api.ClassFilesIndicesBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Dmitry Batkovich
 */
public class MethodsUsageIndexer extends ClassFileIndexer<String, TObjectIntHashMap<MethodIncompleteSignature>> {
  public static final String METHODS_USAGE_INDEX_CANONICAL_NAME = "MethodsUsageIndex";

  public MethodsUsageIndexer() {
    super(METHODS_USAGE_INDEX_CANONICAL_NAME);
  }

  @NotNull
  @Override
  public Map<String, TObjectIntHashMap<MethodIncompleteSignature>> map(final ClassReader inputData) {
    final Map<String, TObjectIntHashMap<MethodIncompleteSignature>> map = new HashMap<String, TObjectIntHashMap<MethodIncompleteSignature>>();
    final MethodVisitor methodVisitor = new MethodVisitor(Opcodes.ASM4) {
      @Override
      public void visitMethodInsn(final int opcode, final String owner, final String name, final String desc) {
        final Type returnType = Type.getReturnType(desc);
        if (MethodIncompleteSignature.CONSTRUCTOR_METHOD_NAME.equals(name) || AsmUtil.isPrimitiveOrArray(returnType.getDescriptor())) {
          return;
        }
        final boolean isStatic = opcode == Opcodes.INVOKESTATIC;
        final String returnClassName = returnType.getInternalName();
        if (!owner.equals(returnClassName) || isStatic) {
          addToIndex(map, returnClassName, new MethodIncompleteSignature(owner, returnClassName, name, isStatic));
        }
      }
    };
    inputData.accept(new ClassVisitor(Opcodes.ASM4) {
      @Override
      public MethodVisitor visitMethod(final int access,
                                       final String name,
                                       final String desc,
                                       final String signature,
                                       final String[] exceptions) {
        return methodVisitor;
      }
    }, ClassReader.EXPAND_FRAMES);
    return map;
  }

  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return new EnumeratorStringDescriptor();
  }

  @Override
  public DataExternalizer<TObjectIntHashMap<MethodIncompleteSignature>> getDataExternalizer() {
    return new TObjectIntHashMapExternalizer<MethodIncompleteSignature>(MethodIncompleteSignature.createDataExternalizer());
  }

  private static void addToIndex(final Map<String, TObjectIntHashMap<MethodIncompleteSignature>> map,
                                 final String internalClassName,
                                 final MethodIncompleteSignature mi) {
    final String className = AsmUtil.getQualifiedClassName(internalClassName);
    TObjectIntHashMap<MethodIncompleteSignature> occurrences = map.get(className);
    if (occurrences == null) {
      occurrences = new TObjectIntHashMap<MethodIncompleteSignature>();
      map.put(className, occurrences);
    }
    if (!occurrences.increment(mi)) {
      occurrences.put(mi, 1);
    }
  }
}
