package com.intellij.compilerOutputIndex.impl.bigram;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.intellij.compilerOutputIndex.api.fs.AsmUtil;
import com.intellij.compilerOutputIndex.api.indexer.CompilerOutputIndexer;
import com.intellij.compilerOutputIndex.impl.ClassFileData;
import com.intellij.compilerOutputIndex.impl.CompilerOutputBaseGramsIndex;
import com.intellij.compilerOutputIndex.impl.MethodIncompleteSignature;
import com.intellij.compilerOutputIndex.impl.MethodIncompleteSignatureChain;
import com.intellij.openapi.project.Project;
import com.intellij.util.SmartList;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.ID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.asm4.ClassReader;
import org.jetbrains.asm4.Opcodes;
import org.jetbrains.asm4.tree.ClassNode;

import java.util.*;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class BigramMethodsUsageIndex extends CompilerOutputBaseGramsIndex<MethodIncompleteSignature> {
  public static BigramMethodsUsageIndex getInstance(final Project project) {
    return CompilerOutputIndexer.getInstance(project).getIndex(BigramMethodsUsageIndex.class);
  }

  public BigramMethodsUsageIndex( final Project project) {
    super(MethodIncompleteSignature.createKeyDescriptor(), project);
  }

  @Override
  protected ID<MethodIncompleteSignature, Multiset<MethodIncompleteSignature>> getIndexId() {
    return generateIndexId("BigramMethodsUsage");
  }

  @Override
  protected int getVersion() {
    return 0;
  }

  @Override
  protected DataIndexer<MethodIncompleteSignature, Multiset<MethodIncompleteSignature>,ClassNode> getIndexer() {
    //
    // not fair way, but works fast
    //
    return new DataIndexer<MethodIncompleteSignature, Multiset<MethodIncompleteSignature>, ClassNode>() {
      @NotNull
      @Override
      public Map<MethodIncompleteSignature, Multiset<MethodIncompleteSignature>> map(final ClassNode inputData) {
        final Map<MethodIncompleteSignature, Multiset<MethodIncompleteSignature>> map =
          new HashMap<MethodIncompleteSignature, Multiset<MethodIncompleteSignature>>();
        for (final ClassFileData.MethodData data : new ClassFileData(inputData).getMethodDatas()) {
          final SimpleBigramsExtractor extractor = new SimpleBigramsExtractor(new SimpleBigramsExtractor.BigramMethodIncompleteSignatureProcessor() {
            @Override
            public void process(final Bigram<MethodIncompleteSignature> bigram) {
              final MethodIncompleteSignature secondGram = bigram.getSecond();
              Multiset<MethodIncompleteSignature> occurrences = map.get(secondGram);
              if (occurrences == null) {
                occurrences = HashMultiset.create();
                map.put(secondGram, occurrences);
              }
              occurrences.add(bigram.getFirst());
            }
          });
          for (final ClassFileData.MethodInsnSignature ms : data.getMethodInsnSignatures()) {
            final List<MethodIncompleteSignature> methodInvocations = new SmartList<MethodIncompleteSignature>();
            final String ownerClassName = AsmUtil.getQualifiedClassName(ms.getOwner());
            final String returnType = AsmUtil.getReturnType(ms.getDesc());

            if (ms.getName().equals(MethodIncompleteSignature.CONSTRUCTOR_METHOD_NAME)) {
              methodInvocations.add(MethodIncompleteSignature.constructor(ownerClassName));
            }
            else {
              methodInvocations.add(new MethodIncompleteSignature(ownerClassName, returnType, ms.getName(), ms.getOpcode() == Opcodes.INVOKESTATIC));
            }
            extractor.addChain(new MethodIncompleteSignatureChain(methodInvocations));
          }
        }
        return map;
      }
    };
  }

}
