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
package com.intellij.compiler.classFilesIndex.impl;

import com.intellij.compiler.classFilesIndex.api.index.ClassFilesIndexFeaturesHolder;
import com.intellij.compiler.classFilesIndex.api.index.ClassFilesIndexReaderBase;
import com.intellij.openapi.project.Project;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.EnumeratorStringDescriptor;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.classFilesIndex.TObjectIntHashMapExternalizer;
import org.jetbrains.jps.classFilesIndex.indexer.impl.EnumeratedMethodIncompleteSignature;

import java.util.Collection;
import java.util.TreeSet;


/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class MethodsUsageIndexReader extends ClassFilesIndexReaderBase<Integer, TObjectIntHashMap<EnumeratedMethodIncompleteSignature>> {

  public static MethodsUsageIndexReader getInstance(final Project project) {
    final MethodsUsageIndexReader instance =
      ClassFilesIndexFeaturesHolder.getInstance(project).getAvailableIndexReader(MethodsUsageIndexReader.class);
    if (instance == null) {
      throw new RuntimeException("couldn't get instance");
    }
    return instance;
  }

  public MethodsUsageIndexReader(final Project project, final String canonicalIndexName, final int version) {
    //noinspection ConstantConditions
    super(EnumeratorIntegerDescriptor.INSTANCE,
          new TObjectIntHashMapExternalizer<>(EnumeratedMethodIncompleteSignature.createDataExternalizer()),
          canonicalIndexName, version, project);
  }

  @NotNull
  public TreeSet<UsageIndexValue> getMethods(final String key) {
    assert myIndex != null;
    assert myMappings != null;
    final Collection<TObjectIntHashMap<EnumeratedMethodIncompleteSignature>> unReducedValues = myIndex.getData(myMappings.getName(key.replace('.', '/')));

    final TObjectIntHashMap<MethodIncompleteSignature> rawValues = new TObjectIntHashMap<>();
    for (final TObjectIntHashMap<EnumeratedMethodIncompleteSignature> unReducedValue : unReducedValues) {
      unReducedValue.forEachEntry(new TObjectIntProcedure<EnumeratedMethodIncompleteSignature>() {
        @Override
        public boolean execute(final EnumeratedMethodIncompleteSignature sign, final int occurrences) {
          final MethodIncompleteSignature denumerated = MethodIncompleteSignature.denumerated(sign, key, myMappings);
          if (!rawValues.adjustValue(denumerated, occurrences)) {
            rawValues.put(denumerated, occurrences);
          }
          return true;
        }
      });
    }

    final TreeSet<UsageIndexValue> values = new TreeSet<>();
    rawValues.forEachEntry(new TObjectIntProcedure<MethodIncompleteSignature>() {
      @Override
      public boolean execute(final MethodIncompleteSignature sign, final int occurrences) {
        values.add(new UsageIndexValue(sign, occurrences));
        return true;
      }
    });
    return values;
  }
}
