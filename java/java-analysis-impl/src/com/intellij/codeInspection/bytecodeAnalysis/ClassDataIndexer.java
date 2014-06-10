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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * @author lambdamix
 */
public class ClassDataIndexer implements DataIndexer<Integer, Collection<IntIdEquation>, FileContent> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.bytecodeAnalysis.ClassDataIndexer");
  final BytecodeAnalysisConverter myConverter;

  public ClassDataIndexer(BytecodeAnalysisConverter converter) {
    myConverter = converter;
  }

  @NotNull
  @Override
  public Map<Integer, Collection<IntIdEquation>> map(@NotNull FileContent inputData) {
    ArrayList<Equation<Key, Value>> rawEquations = ClassProcessing.processClass(new ClassReader(inputData.getContent()));
    Collection<IntIdEquation> idEquations = new ArrayList<IntIdEquation>(rawEquations.size());
    for (Equation<Key, Value> rawEquation : rawEquations) {
      try {
        IntIdEquation idEquation = myConverter.convert(rawEquation);
        idEquations.add(idEquation);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    return Collections.singletonMap(BytecodeAnalysisIndex.KEY, idEquations);
  }
}
