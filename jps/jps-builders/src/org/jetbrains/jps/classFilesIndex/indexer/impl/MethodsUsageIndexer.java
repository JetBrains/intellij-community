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
package org.jetbrains.jps.classFilesIndex.indexer.impl;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.KeyDescriptor;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.java.dependencyView.Mappings;
import org.jetbrains.jps.classFilesIndex.AsmUtil;
import org.jetbrains.jps.classFilesIndex.TObjectIntHashMapExternalizer;
import org.jetbrains.jps.classFilesIndex.indexer.api.ClassFileIndexer;
import org.jetbrains.org.objectweb.asm.*;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Dmitry Batkovich
 */
public class MethodsUsageIndexer extends ClassFileIndexer<Integer, TObjectIntHashMap<EnumeratedMethodIncompleteSignature>> {
  public static final String METHODS_USAGE_INDEX_CANONICAL_NAME = "MethodsUsageIndex";

  public MethodsUsageIndexer() {
    super(METHODS_USAGE_INDEX_CANONICAL_NAME);
  }

  @NotNull
  @Override
  public Map<Integer, TObjectIntHashMap<EnumeratedMethodIncompleteSignature>> map(final ClassReader inputData, final Mappings mappings) {
    final Map<Integer, TObjectIntHashMap<EnumeratedMethodIncompleteSignature>> map =
      new HashMap<Integer, TObjectIntHashMap<EnumeratedMethodIncompleteSignature>>();
    final MethodVisitor methodVisitor = new MethodVisitor(Opcodes.API_VERSION) {
      @Override
      public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
        final Type returnType = Type.getReturnType(desc);
        if (AsmUtil.isPrimitiveOrArrayOfPrimitives(returnType.getDescriptor()) || "<init>".equals(name)) {
          return;
        }
        final boolean isStatic = opcode == Opcodes.INVOKESTATIC;
        final String returnClassName = returnType.getInternalName();
        if (!owner.equals(returnClassName) || isStatic) {
          final EnumeratedMethodIncompleteSignature mi =
            new EnumeratedMethodIncompleteSignature(mappings.getName(owner), mappings.getName(name), isStatic);
          final int enumeratedClassName = mappings.getName(returnClassName);
          TObjectIntHashMap<EnumeratedMethodIncompleteSignature> occurrences = map.get(enumeratedClassName);
          if (occurrences == null) {
            occurrences = new TObjectIntHashMap<EnumeratedMethodIncompleteSignature>();
            map.put(enumeratedClassName, occurrences);
          }
          if (!occurrences.increment(mi)) {
            occurrences.put(mi, 1);
          }
        }
      }
    };
    inputData.accept(new ClassVisitor(Opcodes.API_VERSION) {
      @Override
      public MethodVisitor visitMethod(final int access,
                                       final String name,
                                       final String desc,
                                       final String signature,
                                       final String[] exceptions) {
        return methodVisitor;
      }
    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    return map;
  }

  @Override
  public KeyDescriptor<Integer> getKeyDescriptor() {
    return EnumeratorIntegerDescriptor.INSTANCE;
  }

  @Override
  public DataExternalizer<TObjectIntHashMap<EnumeratedMethodIncompleteSignature>> getDataExternalizer() {
    return new TObjectIntHashMapExternalizer<EnumeratedMethodIncompleteSignature>(EnumeratedMethodIncompleteSignature.createDataExternalizer());
  }
}