package com.intellij.compilerOutputIndex.impl.callingLocation;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.intellij.compilerOutputIndex.api.descriptor.ArrayListKeyDescriptor;
import com.intellij.compilerOutputIndex.api.indexer.CompilerOutputBaseIndex;
import com.intellij.compilerOutputIndex.api.indexer.CompilerOutputIndexer;
import com.intellij.compilerOutputIndex.impl.MethodIncompleteSignature;
import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.ValueContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.asm4.ClassReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Batkovich
 */
public class MethodCallingLocationIndex extends CompilerOutputBaseIndex<MethodNameAndQualifier, List<CallingLocation>> {

  public static MethodCallingLocationIndex getInstance(final Project project) {
    return CompilerOutputIndexer.getInstance(project).getIndex(MethodCallingLocationIndex.class);
  }

  public MethodCallingLocationIndex() {
    super(MethodNameAndQualifier.createKeyDescriptor(),
          new ArrayListKeyDescriptor<CallingLocation>(CallingLocation.createDataExternalizer()));
  }

  @Override
  protected ID<MethodNameAndQualifier, List<CallingLocation>> getIndexId() {
    return generateIndexId(MethodCallingLocationIndex.class);
  }

  @Override
  protected int getVersion() {
    return 0;
  }

  public List<CallingLocation> getAllLocations(final MethodNameAndQualifier methodNameAndQualifier) {
    try {
      final List<CallingLocation> result = new ArrayList<CallingLocation>();
      myIndex.getData(methodNameAndQualifier).forEach(new ValueContainer.ContainerAction<List<CallingLocation>>() {
        @Override
        public boolean perform(final int id, final List<CallingLocation> values) {
          result.addAll(values);
          return true;
        }
      });
      return result;
    }
    catch (StorageException e) {
      throw new RuntimeException(e);
    }
  }

  public Multiset<MethodIncompleteSignature> getLocationsAsParam(final MethodNameAndQualifier methodNameAndQualifier) {
    final Multiset<MethodIncompleteSignature> result = HashMultiset.create();
    try {
      myIndex.getData(methodNameAndQualifier).forEach(new ValueContainer.ContainerAction<List<CallingLocation>>() {
        @Override
        public boolean perform(final int id, final List<CallingLocation> values) {
          for (final CallingLocation value : values) {
            if (value.getVariableType().equals(VariableType.METHOD_PARAMETER)) {
              result.add(value.getMethodIncompleteSignature());
            }
          }
          return true;
        }
      });
    }
    catch (StorageException e) {
      throw new RuntimeException(e);
    }
    return result;
  }


  @Override
  protected DataIndexer<MethodNameAndQualifier, List<CallingLocation>, ClassReader> getIndexer() {
    return new DataIndexer<MethodNameAndQualifier, List<CallingLocation>, ClassReader>() {
      @NotNull
      @Override
      public Map<MethodNameAndQualifier, List<CallingLocation>> map(final ClassReader inputData) {
        return MethodCallingLocationExtractor.extract(inputData);
      }
    };
  }
}
