package com.intellij.compilerOutputIndex.impl.singleton;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.intellij.compilerOutputIndex.api.fs.AsmUtil;
import com.intellij.compilerOutputIndex.api.indexer.CompilerOutputBaseIndex;
import com.intellij.compilerOutputIndex.api.indexer.CompilerOutputIndexUtil;
import com.intellij.compilerOutputIndex.api.indexer.CompilerOutputIndexer;
import com.intellij.compilerOutputIndex.impl.GuavaHashMultiSetExternalizer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.ValueContainer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.asm4.*;

import java.util.*;

/**
 * @author Dmitry Batkovich
 */
public class ParamsInMethodOccurrencesIndex extends CompilerOutputBaseIndex<String, Multiset<MethodShortSignature>> {

  public static ParamsInMethodOccurrencesIndex getInstance(final Project project) {
    return CompilerOutputIndexer.getInstance(project).getIndex(ParamsInMethodOccurrencesIndex.class);
  }

  public ParamsInMethodOccurrencesIndex() {
    super(new EnumeratorStringDescriptor(), new GuavaHashMultiSetExternalizer<MethodShortSignature>(MethodShortSignature.createDataExternalizer()));
  }

  @Override
  protected ID<String, Multiset<MethodShortSignature>> getIndexId() {
    return generateIndexId(ParamsInMethodOccurrencesIndex.class);
  }

  @Override
  protected int getVersion() {
    return 0;
  }

  public Pair<List<MethodShortSignatureWithWeight>, Integer> getParameterOccurrences(final String parameterTypeName) {
    try {
      final Multiset<MethodShortSignature> resultAsMultiset = HashMultiset.create();
      final ValueContainer<Multiset<MethodShortSignature>> valueContainer = myIndex.getData(parameterTypeName);
      valueContainer.forEach(new ValueContainer.ContainerAction<Multiset<MethodShortSignature>>() {
        @Override
        public boolean perform(final int id, final Multiset<MethodShortSignature> localMap) {
          for (final Multiset.Entry<MethodShortSignature> e : localMap.entrySet()) {
            resultAsMultiset.add(e.getElement(), e.getCount());
          }
          return true;
        }
      });

      final List<MethodShortSignatureWithWeight> result = new ArrayList<MethodShortSignatureWithWeight>(resultAsMultiset.elementSet().size());
      int sumWeight = 0;
      for (final Multiset.Entry<MethodShortSignature> e : resultAsMultiset.entrySet()) {
        sumWeight += e.getCount();
        result.add(new MethodShortSignatureWithWeight(e.getElement(), e.getCount()));
      }
      Collections.sort(result, MethodShortSignatureWithWeight.COMPARATOR);

      return Pair.create(result, sumWeight);
    } catch (StorageException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected DataIndexer<String, Multiset<MethodShortSignature>, ClassReader> getIndexer() {
    return new DataIndexer<String, Multiset<MethodShortSignature>, ClassReader>() {
      @NotNull
      @Override
      public Map<String, Multiset<MethodShortSignature>> map(final ClassReader inputData) {
        final Map<String, Multiset<MethodShortSignature>> result = new HashMap<String, Multiset<MethodShortSignature>>();
        inputData.accept(new ClassVisitor(Opcodes.ASM4) {
          @Nullable
          @Override
          public MethodVisitor visitMethod(final int i, final String name, final String desc, final String signature, final String[] exception) {
            if (CompilerOutputIndexUtil.isSetterOrConstructorMethodName(name))  {
              return null;
            }
            final String[] parameters = AsmUtil.getParamsTypes(desc);
            final MethodShortSignature thisMethodShortSignature = new MethodShortSignature(name, desc);
            for (final String parameter : parameters) {
              Multiset<MethodShortSignature> methods = result.get(parameter);
              if (methods == null) {
                methods = HashMultiset.create();
                result.put(parameter, methods);
              }
              methods.add(thisMethodShortSignature);
            }
            return new MethodVisitor(Opcodes.ASM4) {
              @Override
              public void visitLocalVariable(final String s, final String desc, final String signature, final Label label, final Label label2, final int i) {
                final String varType = AsmUtil.getQualifiedClassName(desc);
                Multiset<MethodShortSignature> methods = result.get(varType);
                if (methods == null) {
                  methods = HashMultiset.create();
                  result.put(varType, methods);
                }
                methods.add(thisMethodShortSignature);
              }
            };
          }
        }, Opcodes.ASM4);
        return result;
      }
    };
  }
}
