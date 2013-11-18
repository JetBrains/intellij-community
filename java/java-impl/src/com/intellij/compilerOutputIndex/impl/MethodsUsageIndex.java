package com.intellij.compilerOutputIndex.impl;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.intellij.compilerOutputIndex.api.fs.AsmUtil;
import com.intellij.compilerOutputIndex.api.indexer.CompilerOutputBaseIndex;
import com.intellij.compilerOutputIndex.api.indexer.CompilerOutputIndexer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.psi.*;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.ValueContainer;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.asm4.ClassVisitor;
import org.jetbrains.asm4.MethodVisitor;
import org.jetbrains.asm4.Opcodes;
import org.jetbrains.asm4.Type;
import org.jetbrains.asm4.tree.ClassNode;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;


/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class MethodsUsageIndex extends CompilerOutputBaseIndex<String, Multiset<MethodIncompleteSignature>> {

  public static MethodsUsageIndex getInstance(final Project project) {
    return CompilerOutputIndexer.getInstance(project).getIndex(MethodsUsageIndex.class);
  }

  public MethodsUsageIndex(final Project project) {
    super(new EnumeratorStringDescriptor(),
          new GuavaHashMultiSetExternalizer<MethodIncompleteSignature>(MethodIncompleteSignature.createKeyDescriptor()), project);
  }

  @Override
  protected DataIndexer<String, Multiset<MethodIncompleteSignature>, ClassNode> getIndexer() {
    return new DataIndexer<String, Multiset<MethodIncompleteSignature>, ClassNode>() {
      @NotNull
      @Override
      public Map<String, Multiset<MethodIncompleteSignature>> map(final ClassNode inputData) {
        final Map<String, Multiset<MethodIncompleteSignature>> map = new HashMap<String, Multiset<MethodIncompleteSignature>>();
        final MethodVisitor methodVisitor = new MethodVisitor(Opcodes.ASM4) {
          @Override
          public void visitMethodInsn(final int opcode, final String owner, final String name, final String desc) {
            final Type returnType = Type.getReturnType(desc);
            if (MethodIncompleteSignature.CONSTRUCTOR_METHOD_NAME.equals(name) ||
                AsmUtil.isPrimitiveOrArray(returnType.getDescriptor())) {
              return;
            }
            final String returnClassName = returnType.getInternalName();
            final boolean isStatic = opcode == Opcodes.INVOKESTATIC;
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
        });
        return map;
      }
    };
  }

  @Override
  protected ID<String, Multiset<MethodIncompleteSignature>> getIndexId() {
    return generateIndexId("MethodsUsage");
  }

  @Override
  protected int getVersion() {
    return 1;
  }

  public TreeSet<UsageIndexValue> getValues(final String key) {
    try {
      final ValueContainer<Multiset<MethodIncompleteSignature>> valueContainer = myIndex.getData(key);
      final Multiset<MethodIncompleteSignature> rawValues = HashMultiset.create();
      valueContainer.forEach(new ValueContainer.ContainerAction<Multiset<MethodIncompleteSignature>>() {
        @Override
        public boolean perform(final int id, final Multiset<MethodIncompleteSignature> values) {
          for (final Multiset.Entry<MethodIncompleteSignature> entry : values.entrySet()) {
            rawValues.add(entry.getElement(), entry.getCount());
          }
          return true;
        }
      });
      return rawValuesToValues(rawValues);
    }
    catch (final StorageException e) {
      throw new RuntimeException();
    }
  }

  private static void addToIndex(final Map<String, Multiset<MethodIncompleteSignature>> map,
                                 final String internalClassName,
                                 final MethodIncompleteSignature mi) {
    final String className = AsmUtil.getQualifiedClassName(internalClassName);
    Multiset<MethodIncompleteSignature> occurrences = map.get(className);
    if (occurrences == null) {
      occurrences = HashMultiset.create();
      map.put(className, occurrences);
    }
    occurrences.add(mi);
  }

  private static TreeSet<UsageIndexValue> rawValuesToValues(final Multiset<MethodIncompleteSignature> rawValues) {
    final TreeSet<UsageIndexValue> values = new TreeSet<UsageIndexValue>();
    for (final Multiset.Entry<MethodIncompleteSignature> entry : rawValues.entrySet()) {
      values.add(new UsageIndexValue(entry.getElement().toExternalRepresentation(), entry.getCount()));
    }
    return values;
  }
}
