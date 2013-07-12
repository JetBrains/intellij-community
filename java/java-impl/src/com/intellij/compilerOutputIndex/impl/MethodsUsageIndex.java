package com.intellij.compilerOutputIndex.impl;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.intellij.compilerOutputIndex.api.fs.AsmUtil;
import com.intellij.compilerOutputIndex.api.indexer.CompilerOutputIndexer;
import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.io.EnumeratorStringDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.asm4.ClassReader;
import org.jetbrains.asm4.Opcodes;

import java.util.HashMap;
import java.util.Map;


/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class MethodsUsageIndex extends CompilerOutputBaseGramsIndex<String> {

  public static MethodsUsageIndex getInstance(final Project project) {
    return CompilerOutputIndexer.getInstance(project).getIndex(MethodsUsageIndex.class);
  }

  public MethodsUsageIndex() {
    super(new EnumeratorStringDescriptor());
  }

  @Override
  protected DataIndexer<String, Multiset<MethodIncompleteSignature>, ClassReader> getIndexer() {
    return new DataIndexer<String, Multiset<MethodIncompleteSignature>, ClassReader>() {
      @NotNull
      @Override
      public Map<String, Multiset<MethodIncompleteSignature>> map(final ClassReader inputData) {
        final Map<String, Multiset<MethodIncompleteSignature>> map = new HashMap<String, Multiset<MethodIncompleteSignature>>();
        for (final ClassFileData.MethodData data : new ClassFileData(inputData).getMethodDatas()) {
          for (final ClassFileData.MethodInsnSignature ms : data.getMethodInsnSignatures()) {
            final String ownerClassName = AsmUtil.getQualifiedClassName(ms.getOwner());
            final String returnType = AsmUtil.getReturnType(ms.getDesc());
            if (MethodIncompleteSignature.CONSTRUCTOR_METHOD_NAME.equals(ms.getName())) {
              addToIndex(map, ownerClassName, MethodIncompleteSignature.constructor(ownerClassName));
            }
            else {
              final boolean isStatic = ms.getOpcode() == Opcodes.INVOKESTATIC;
              if (!ownerClassName.equals(returnType) || isStatic) {
                addToIndex(map, returnType, new MethodIncompleteSignature(ownerClassName, returnType, ms.getName(), isStatic));
              }
            }
          }
        }
        return map;
      }


    };
  }

  public void clear() {
    try {
      myIndex.clear();
    }
    catch (StorageException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected ID<String, Multiset<MethodIncompleteSignature>> getIndexId() {
    return generateIndexId(MethodsUsageIndex.class);
  }

  @Override
  protected int getVersion() {
    return 0;
  }

  private static void addToIndex(final Map<String, Multiset<MethodIncompleteSignature>> map,
                                 final String key,
                                 final MethodIncompleteSignature mi) {
    Multiset<MethodIncompleteSignature> occurrences = map.get(key);
    if (occurrences == null) {
      occurrences = HashMultiset.create();
      map.put(key, occurrences);
    }
    occurrences.add(mi);
  }
}
