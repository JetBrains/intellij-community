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
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.util.containers.MostlySingularMultiMap;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.util.ArrayList;

public class ClassFileProcessor extends VirtualFileVisitor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.bytecodeAnalysis.ClassProcessor");

  final static ELattice<Value> valueLattice = new ELattice<Value>(Value.Bot, Value.Top);
  final IntIdSolver myIntIdSolver;
  final BytecodeAnalysisConverter myLowering;

  @NotNull
  final ProgressIndicator myProgressIndicator;
  private final long totalClassFiles;
  long processed = 0;

  public ClassFileProcessor(@NotNull ProgressIndicator indicator, long totalClassFiles) {
    this.myProgressIndicator = indicator;
    this.totalClassFiles = totalClassFiles;
    myLowering = BytecodeAnalysisConverter.getInstance();
    myIntIdSolver = new IntIdSolver(valueLattice);
  }

  @Override
  public boolean visitFile(@NotNull VirtualFile file) {
    if (!file.isDirectory() && "class".equals(file.getExtension())) {
      try {
        ArrayList<Equation<Key, Value>> equations = ClassProcessing.processClass(new ClassReader(file.contentsToByteArray()));
        for (Equation<Key, Value> equation : equations) {
          addEquation(equation);
        }
      }
      catch (IOException e) {
        LOG.debug("Error when processing " + file.getPresentableUrl(), e);
      }
      myProgressIndicator.setText2(file.getPresentableUrl());
      myProgressIndicator.setFraction(((double) processed++) / totalClassFiles);
    }
    return true;
  }

  void addEquation(Equation<Key, Value> equation) {
    try {
      myIntIdSolver.addEquation(myLowering.enumerate(equation));
    }
    catch (IOException e) {
      // TODO
    }
  }

  // nullity, contracts
  MostlySingularMultiMap<String, AnnotationData> annotations() {
    TIntObjectHashMap<Value> internalIdSolutions = myIntIdSolver.solve();
    return Util.makeAnnotations(internalIdSolutions);
  }
}
