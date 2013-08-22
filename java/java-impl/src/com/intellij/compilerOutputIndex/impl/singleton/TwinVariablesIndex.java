package com.intellij.compilerOutputIndex.impl.singleton;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.intellij.codeInsight.completion.methodChains.ChainCompletionStringUtil;
import com.intellij.compilerOutputIndex.api.descriptor.ArrayListKeyDescriptor;
import com.intellij.compilerOutputIndex.api.fs.AsmUtil;
import com.intellij.compilerOutputIndex.api.indexer.CompilerOutputBaseIndex;
import com.intellij.compilerOutputIndex.api.indexer.CompilerOutputIndexUtil;
import com.intellij.compilerOutputIndex.api.indexer.CompilerOutputIndexer;
import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.ValueContainer;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.EnumeratorStringDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.asm4.*;

import java.util.*;

/**
 * @author Dmitry Batkovich
 */
public class TwinVariablesIndex extends CompilerOutputBaseIndex<String, List<Integer>> {
  public static TwinVariablesIndex getInstance(final Project project) {
    return CompilerOutputIndexer.getInstance(project).getIndex(TwinVariablesIndex.class);
  }

  public TwinVariablesIndex() {
    super(new EnumeratorStringDescriptor(), new ArrayListKeyDescriptor<Integer>(EnumeratorIntegerDescriptor.INSTANCE));
  }

  @Override
  protected ID<String, List<Integer>> getIndexId() {
    return generateIndexId(TwinVariablesIndex.class);
  }

  @Override
  protected int getVersion() {
    return 0;
  }

  @NotNull
  public List<Integer> getTwinInfo(final String typeQName) {
    try {
      final ValueContainer<List<Integer>> valueContainer = myIndex.getData(typeQName);
      final List<Integer> result = new ArrayList<Integer>(valueContainer.size());
      valueContainer.forEach(new ValueContainer.ContainerAction<List<Integer>>() {
        @Override
        public boolean perform(final int id, final List<Integer> value) {
          result.addAll(value);
          return true;
        }
      });
      return result;
    }
    catch (StorageException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected DataIndexer<String, List<Integer>, ClassReader> getIndexer() {
    return new DataIndexer<String, List<Integer>, ClassReader>() {
      @NotNull
      @Override
      public Map<String, List<Integer>> map(final ClassReader inputData) {
        final Map<String, List<Integer>> map = new HashMap<String, List<Integer>>();
        inputData.accept(new ClassVisitor(Opcodes.ASM4) {

          @Nullable
          @Override
          public MethodVisitor visitMethod(final int access,
                                           final String name,
                                           final String desc,
                                           final String signature,
                                           final String[] exceptions) {
            if (CompilerOutputIndexUtil.isSetterOrConstructorMethodName(name)) {
              return null;
            }
            final Multiset<String> myTypesOccurrences = HashMultiset.create();
            final String[] paramsTypes = AsmUtil.getParamsTypes(desc);
            Collections.addAll(myTypesOccurrences, paramsTypes);
            return new MethodVisitor(Opcodes.ASM4) {
              private final Set<String> myLocalVarNames = new HashSet<String>();

              @SuppressWarnings("unchecked")
              @Override
              public void visitEnd() {
                for (final Multiset.Entry<String> e: myTypesOccurrences.entrySet()) {
                  final String key = e.getElement();
                  if (!ChainCompletionStringUtil.isPrimitiveOrArrayOfPrimitives(key)) {
                    List<Integer> values = map.get(key);
                    if (values == null) {
                      values = new ArrayList<Integer>();
                      map.put(key, values);
                    }
                    values.add(e.getCount());
                  }
                }
              }

              private final Set<String> myUsedReadFieldsIndex = new HashSet<String>();

              @Override
              public void visitFieldInsn(final int opcode, final String owner, final String name, final String desc) {
                final String fieldTypeQName = AsmUtil.getReturnType(desc);
                if ((opcode == Opcodes.GETSTATIC || opcode == Opcodes.GETFIELD)) {
                  if (myUsedReadFieldsIndex.add(owner + name)) {
                    myTypesOccurrences.add(fieldTypeQName);
                  }
                }
              }

              @Override
              public void visitLocalVariable(final String name,
                                             final String desc,
                                             final String signature,
                                             final Label start,
                                             final Label end,
                                             final int index) {
                if (index > paramsTypes.length && myLocalVarNames.add(name)) {
                  final String type = AsmUtil.getReturnType(desc);
                  myTypesOccurrences.add(type);
                }
              }
            };
          }
        }, Opcodes.ASM4);
        return map;
      }
    };
  }
}
