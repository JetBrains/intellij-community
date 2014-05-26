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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.*;
import org.jetbrains.org.objectweb.asm.tree.MethodNode;
import org.jetbrains.org.objectweb.asm.tree.analysis.AnalyzerException;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ClassProcessor extends VirtualFileVisitor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.bytecodeAnalysis.ClassProcessor");

  final static ELattice<Value> valueLattice = new ELattice<Value>(Value.Bot, Value.Top);
  final Solver<Key, Value> solver = new Solver<Key, Value>(valueLattice);
  final Map<Method, MethodExtra> extras = new HashMap<Method, MethodExtra>();
  final @NotNull ProgressIndicator myProgressIndicator;
  private final long totalClassFiles;
  long processed = 0;

  public ClassProcessor(@NotNull ProgressIndicator indicator, long totalClassFiles) {
    this.myProgressIndicator = indicator;
    this.totalClassFiles = totalClassFiles;
  }

  @Override
  public boolean visitFile(@NotNull VirtualFile file) {
    if (!file.isDirectory() && "class".equals(file.getExtension())) {
      try {
        processClass(new ClassReader(file.contentsToByteArray()));
      }
      catch (IOException e) {
        // TODO
      }
      myProgressIndicator.setFraction((double)processed++ / totalClassFiles);
    }
    return true;
  }

  public void processClass(final ClassReader classReader) {
    classReader.accept(new ClassVisitor(Opcodes.ASM5) {
      @Override
      public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        final MethodNode node = new MethodNode(Opcodes.ASM5, access, name, desc, signature, exceptions);
        return new MethodVisitor(Opcodes.ASM5, node) {
          @Override
          public void visitEnd() {
            super.visitEnd();
            processMethod(classReader.getClassName(), node);
          }
        };
      }
    }, 0);
  }

  void processMethod(String className, MethodNode methodNode) {
    Method method = new Method(className, methodNode.name, methodNode.desc);
    extras.put(method, new MethodExtra(methodNode.signature, methodNode.access));

    ControlFlowGraph graph = cfg.buildControlFlowGraph(className, methodNode);
    boolean added = false;
    Type[] argumentTypes = Type.getArgumentTypes(methodNode.desc);
    Type resultType = Type.getReturnType(methodNode.desc);
    int resultSort = resultType.getSort();

    boolean isReferenceResult = resultSort == Type.OBJECT || resultSort == Type.ARRAY;
    boolean isBooleanResult = Type.BOOLEAN_TYPE == resultType;

    if (graph.transitions.length > 0) {
      DFSTree dfs = cfg.buildDFSTree(graph.transitions);
      boolean reducible = dfs.back.isEmpty() || cfg.reducible(graph, dfs);
      if (reducible) {
        List<Equation<Key, Value>> toAdd = new LinkedList<Equation<Key, Value>>();
        try {
          for (int i = 0; i < argumentTypes.length; i++) {
            Type argType = argumentTypes[i];
            int argSort = argType.getSort();
            boolean isReferenceArg = argSort == Type.OBJECT || argSort == Type.ARRAY;
            boolean isBooleanArg = Type.BOOLEAN_TYPE.equals(argType);
            if (isReferenceArg) {
              toAdd.add(new NonNullInAnalysis(new RichControlFlow(graph, dfs), new In(i)).analyze());
            }
            if (isReferenceResult || isBooleanResult) {
              if (isReferenceArg) {
                toAdd.add(new InOutAnalysis(new RichControlFlow(graph, dfs), new InOut(i, Value.Null)).analyze());
                toAdd.add(new InOutAnalysis(new RichControlFlow(graph, dfs), new InOut(i, Value.NotNull)).analyze());
              }
              if (isBooleanArg) {
                toAdd.add(new InOutAnalysis(new RichControlFlow(graph, dfs), new InOut(i, Value.False)).analyze());
                toAdd.add(new InOutAnalysis(new RichControlFlow(graph, dfs), new InOut(i, Value.True)).analyze());
              }
            }
          }
          if (isReferenceResult) {
            toAdd.add(new InOutAnalysis(new RichControlFlow(graph, dfs), new Out()).analyze());
          }
          added = true;
          for (Equation<Key, Value> equation : toAdd) {
            solver.addEquation(equation);
          }
        } catch (AnalyzerException e) {
          throw new RuntimeException();
        }
      } else {
        LOG.debug("CFG for " +
                  className + " " +
                  methodNode.name +
                  methodNode.desc + " " +
                  "is not reducible");
      }
    }

    if (!added) {
      method = new Method(className, methodNode.name, methodNode.desc);
      for (int i = 0; i < argumentTypes.length; i++) {
        Type argType = argumentTypes[i];
        int argSort = argType.getSort();
        boolean isReferenceArg = argSort == Type.OBJECT || argSort == Type.ARRAY;

        if (isReferenceArg) {
          solver.addEquation(new Equation<Key, Value>(new Key(method, new In(i)), new Final<Key, Value>(Value.Top)));
          if (isReferenceResult || isBooleanResult) {
            solver.addEquation(new Equation<Key, Value>(new Key(method, new InOut(i, Value.Null)), new Final<Key, Value>(Value.Top)));
            solver.addEquation(new Equation<Key, Value>(new Key(method, new InOut(i, Value.NotNull)), new Final<Key, Value>(Value.Top)));
          }
        }
        if (Type.BOOLEAN_TYPE.equals(argType)) {
          if (isReferenceResult || isBooleanResult) {
            solver.addEquation(new Equation<Key, Value>(new Key(method, new InOut(i, Value.False)), new Final<Key, Value>(Value.Top)));
            solver.addEquation(new Equation<Key, Value>(new Key(method, new InOut(i, Value.True)), new Final<Key, Value>(Value.Top)));
          }
        }
      }
      if (isReferenceResult) {
        solver.addEquation(new Equation<Key, Value>(new Key(method, new Out()), new Final<Key, Value>(Value.Top)));
      }
    }
  }

  MostlySingularMultiMap<String, AnnotationData> annotations() {
    Map<Key, Value> solutions = solver.solve();
    return Util.makeAnnotations(solutions, extras);
  }
}
