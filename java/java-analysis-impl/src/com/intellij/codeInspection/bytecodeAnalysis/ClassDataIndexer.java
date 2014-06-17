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
import org.jetbrains.org.objectweb.asm.*;
import org.jetbrains.org.objectweb.asm.tree.MethodNode;
import org.jetbrains.org.objectweb.asm.tree.analysis.AnalyzerException;

import java.io.IOException;
import java.util.*;

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
    ClassEquations rawEquations = processClass(new ClassReader(inputData.getContent()));
    List<Equation<Key, Value>> rawParameterEquations = rawEquations.parameterEquations;
    List<Equation<Key, Value>> rawContractEquations = rawEquations.contractEquations;

    Collection<IntIdEquation> idParameterEquations = new ArrayList<IntIdEquation>(rawParameterEquations.size());
    Collection<IntIdEquation> idContractEquations = new ArrayList<IntIdEquation>(rawContractEquations.size());

    HashMap<Integer, Collection<IntIdEquation>> map = new HashMap<Integer, Collection<IntIdEquation>>(2);
    map.put(BytecodeAnalysisIndex.PARAMETERS, idParameterEquations);
    map.put(BytecodeAnalysisIndex.CONTRACTS, idContractEquations);

    try {
      for (Equation<Key, Value> rawParameterEquation: rawParameterEquations) {
        idParameterEquations.add(myConverter.convert(rawParameterEquation));
      }
      for (Equation<Key, Value> rawContractEquation: rawContractEquations) {
        idContractEquations.add(myConverter.convert(rawContractEquation));
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return map;
  }

  private static class ClassEquations {
    final List<Equation<Key, Value>> parameterEquations;
    final List<Equation<Key, Value>> contractEquations;

    private ClassEquations(List<Equation<Key, Value>> parameterEquations, List<Equation<Key, Value>> contractEquations) {
      this.parameterEquations = parameterEquations;
      this.contractEquations = contractEquations;
    }
  }

  public static ClassEquations processClass(final ClassReader classReader) {
    final List<Equation<Key, Value>> parameterEquations = new ArrayList<Equation<Key, Value>>();
    final List<Equation<Key, Value>> contractEquations = new ArrayList<Equation<Key, Value>>();

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

      void processMethod(String className, MethodNode methodNode) {
        Method method = new Method(className, methodNode.name, methodNode.desc);

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
            try {
              for (int i = 0; i < argumentTypes.length; i++) {
                Type argType = argumentTypes[i];
                int argSort = argType.getSort();
                boolean isReferenceArg = argSort == Type.OBJECT || argSort == Type.ARRAY;
                boolean isBooleanArg = Type.BOOLEAN_TYPE.equals(argType);
                if (isReferenceArg) {
                  parameterEquations.add(new NonNullInAnalysis(new RichControlFlow(graph, dfs), new In(i)).analyze());
                }
                if (isReferenceResult || isBooleanResult) {
                  if (isReferenceArg) {
                    contractEquations.add(new InOutAnalysis(new RichControlFlow(graph, dfs), new InOut(i, Value.Null)).analyze());
                    contractEquations.add(new InOutAnalysis(new RichControlFlow(graph, dfs), new InOut(i, Value.NotNull)).analyze());
                  }
                  if (isBooleanArg) {
                    contractEquations.add(new InOutAnalysis(new RichControlFlow(graph, dfs), new InOut(i, Value.False)).analyze());
                    contractEquations.add(new InOutAnalysis(new RichControlFlow(graph, dfs), new InOut(i, Value.True)).analyze());
                  }
                }
              }
              if (isReferenceResult) {
                contractEquations.add(new InOutAnalysis(new RichControlFlow(graph, dfs), new Out()).analyze());
              }
              added = true;
            } catch (AnalyzerException e) {
              LOG.error(e);
            }
          } else {
            LOG.debug("CFG for " + method + " is not reducible");
          }
        }

        if (!added) {
          method = new Method(className, methodNode.name, methodNode.desc);
          for (int i = 0; i < argumentTypes.length; i++) {
            Type argType = argumentTypes[i];
            int argSort = argType.getSort();
            boolean isReferenceArg = argSort == Type.OBJECT || argSort == Type.ARRAY;

            if (isReferenceArg) {
              contractEquations.add(new Equation<Key, Value>(new Key(method, new In(i)), new Final<Key, Value>(Value.Top)));
              if (isReferenceResult || isBooleanResult) {
                contractEquations.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.Null)), new Final<Key, Value>(Value.Top)));
                contractEquations.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.NotNull)), new Final<Key, Value>(Value.Top)));
              }
            }
            if (Type.BOOLEAN_TYPE.equals(argType)) {
              if (isReferenceResult || isBooleanResult) {
                contractEquations.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.False)), new Final<Key, Value>(Value.Top)));
                contractEquations.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.True)), new Final<Key, Value>(Value.Top)));
              }
            }
          }
          if (isReferenceResult) {
            parameterEquations.add(new Equation<Key, Value>(new Key(method, new Out()), new Final<Key, Value>(Value.Top)));
          }
        }
      }
    }, 0);

    return new ClassEquations(parameterEquations, contractEquations);
  }
}
