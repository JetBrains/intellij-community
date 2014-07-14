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

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileContent;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.*;
import org.jetbrains.org.objectweb.asm.tree.MethodNode;
import org.jetbrains.org.objectweb.asm.tree.analysis.AnalyzerException;

import java.util.*;

import static com.intellij.codeInspection.bytecodeAnalysis.ProjectBytecodeAnalysis.LOG;

/**
 * @author lambdamix
 */
public class ClassDataIndexer implements DataIndexer<Integer, Collection<IntIdEquation>, FileContent> {
  final BytecodeAnalysisConverter myConverter;

  public ClassDataIndexer(BytecodeAnalysisConverter converter) {
    myConverter = converter;
  }

  @NotNull
  @Override
  public Map<Integer, Collection<IntIdEquation>> map(@NotNull FileContent inputData) {
    HashMap<Integer, Collection<IntIdEquation>> map = new HashMap<Integer, Collection<IntIdEquation>>(2);
    try {
      ClassEquations rawEquations = processClass(new ClassReader(inputData.getContent()));
      List<Equation<Key, Value>> rawParameterEquations = rawEquations.parameterEquations;
      List<Equation<Key, Value>> rawContractEquations = rawEquations.contractEquations;

      Collection<IntIdEquation> idParameterEquations = new ArrayList<IntIdEquation>(rawParameterEquations.size());
      Collection<IntIdEquation> idContractEquations = new ArrayList<IntIdEquation>(rawContractEquations.size());

      map.put(BytecodeAnalysisIndex.indexKey(inputData.getFile(), true), idParameterEquations);
      map.put(BytecodeAnalysisIndex.indexKey(inputData.getFile(), false), idContractEquations);


      for (Equation<Key, Value> rawParameterEquation: rawParameterEquations) {
        idParameterEquations.add(myConverter.convert(rawParameterEquation));
      }
      for (Equation<Key, Value> rawContractEquation: rawContractEquations) {
        idContractEquations.add(myConverter.convert(rawContractEquation));
      }
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      // incorrect bytecode may result in Runtime exceptions during analysis
      // so here we suppose that exception is due to incorrect bytecode
      LOG.debug("Unexpected Error during indexing of bytecode", e);
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
      private boolean stableClass;

      @Override
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        stableClass = (access & Opcodes.ACC_FINAL) != 0;
        super.visit(version, access, name, signature, superName, interfaces);
      }

      @Override
      public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        final MethodNode node = new MethodNode(Opcodes.ASM5, access, name, desc, signature, exceptions);
        return new MethodVisitor(Opcodes.ASM5, node) {
          @Override
          public void visitEnd() {
            super.visitEnd();
            processMethod(classReader.getClassName(), node, stableClass);
          }
        };
      }

      void processMethod(final String className, final MethodNode methodNode, boolean stableClass) {
        ProgressManager.checkCanceled();
        Type[] argumentTypes = Type.getArgumentTypes(methodNode.desc);
        Type resultType = Type.getReturnType(methodNode.desc);
        int resultSort = resultType.getSort();
        boolean isReferenceResult = resultSort == Type.OBJECT || resultSort == Type.ARRAY;
        boolean isBooleanResult = Type.BOOLEAN_TYPE == resultType;
        boolean isInterestingResult = isReferenceResult || isBooleanResult;

        if (argumentTypes.length == 0 && !isInterestingResult) {
          return;
        }

        Method method = new Method(className, methodNode.name, methodNode.desc);
        int access = methodNode.access;
        boolean stable =
          stableClass ||
          (access & Opcodes.ACC_FINAL) != 0 ||
          (access & Opcodes.ACC_PRIVATE) != 0 ||
          (access & Opcodes.ACC_STATIC) != 0 ||
          "<init>".equals(methodNode.name);
        try {
          boolean added = false;
          ControlFlowGraph graph = cfg.buildControlFlowGraph(className, methodNode);

          boolean maybeLeakingParameter = false;
          for (Type argType : argumentTypes) {
            int argSort = argType.getSort();
            if (argSort == Type.OBJECT || argSort == Type.ARRAY || (isInterestingResult && Type.BOOLEAN_TYPE.equals(argType))) {
              maybeLeakingParameter = true;
              break;
            }
          }

          if (graph.transitions.length > 0) {
            DFSTree dfs = cfg.buildDFSTree(graph.transitions);
            boolean reducible = dfs.back.isEmpty() || cfg.reducible(graph, dfs);
            if (reducible) {
              NotNullLazyValue<TIntHashSet> resultOrigins = new NotNullLazyValue<TIntHashSet>() {
                @NotNull
                @Override
                protected TIntHashSet compute() {
                  try {
                    return cfg.resultOrigins(className, methodNode);
                  }
                  catch (AnalyzerException e) {
                    throw new RuntimeException(e);
                  }
                }
              };
              boolean[] leakingParameters = maybeLeakingParameter ? cfg.leakingParameters(className, methodNode) : null;
              boolean shouldComputeResult = isReferenceResult;

              if (!shouldComputeResult && isInterestingResult && maybeLeakingParameter) {
                loop: for (int i = 0; i < argumentTypes.length; i++) {
                  Type argType = argumentTypes[i];
                  int argSort = argType.getSort();
                  boolean isReferenceArg = argSort == Type.OBJECT || argSort == Type.ARRAY;
                  boolean isBooleanArg = Type.BOOLEAN_TYPE.equals(argType);
                  if ((isReferenceArg || isBooleanArg) && !leakingParameters[i]) {
                    shouldComputeResult = true;
                    break loop;
                  }
                }
              }

              Equation<Key, Value> resultEquation =
                shouldComputeResult ? new InOutAnalysis(new RichControlFlow(graph, dfs), new Out(), resultOrigins.getValue(), stable).analyze() : null;

              for (int i = 0; i < argumentTypes.length; i++) {
                Type argType = argumentTypes[i];
                int argSort = argType.getSort();
                boolean isReferenceArg = argSort == Type.OBJECT || argSort == Type.ARRAY;
                boolean isBooleanArg = Type.BOOLEAN_TYPE.equals(argType);
                if (isReferenceArg) {
                  if (leakingParameters[i]) {
                    parameterEquations.add(new NonNullInAnalysis(new RichControlFlow(graph, dfs), new In(i), stable).analyze());
                  } else {
                    parameterEquations.add(new Equation<Key, Value>(new Key(method, new In(i), stable), new Final<Key, Value>(Value.Top)));
                  }
                }
                if (isReferenceArg && isInterestingResult) {
                  if (leakingParameters[i]) {
                    contractEquations.add(new InOutAnalysis(new RichControlFlow(graph, dfs), new InOut(i, Value.Null), resultOrigins.getValue(), stable).analyze());
                    contractEquations.add(new InOutAnalysis(new RichControlFlow(graph, dfs), new InOut(i, Value.NotNull), resultOrigins.getValue(), stable).analyze());
                  } else {
                    contractEquations.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.Null), stable), resultEquation.rhs));
                    contractEquations.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.NotNull), stable), resultEquation.rhs));
                  }
                }
                if (isBooleanArg && isInterestingResult) {
                  if (leakingParameters[i]) {
                    contractEquations.add(new InOutAnalysis(new RichControlFlow(graph, dfs), new InOut(i, Value.False), resultOrigins.getValue(), stable).analyze());
                    contractEquations.add(new InOutAnalysis(new RichControlFlow(graph, dfs), new InOut(i, Value.True), resultOrigins.getValue(), stable).analyze());
                  } else {
                    contractEquations.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.False), stable), resultEquation.rhs));
                    contractEquations.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.True), stable), resultEquation.rhs));
                  }
                }
              }
              if (isReferenceResult) {
                if (resultEquation != null) {
                  contractEquations.add(resultEquation);
                } else {
                  contractEquations.add(new InOutAnalysis(new RichControlFlow(graph, dfs), new Out(), resultOrigins.getValue(), stable).analyze());
                }
              }
              added = true;
            }
            else {
              LOG.debug("CFG for " + method + " is not reducible");
            }
          }

          if (!added) {
            method = new Method(className, methodNode.name, methodNode.desc);
            for (int i = 0; i < argumentTypes.length; i++) {
              Type argType = argumentTypes[i];
              int argSort = argType.getSort();
              boolean isReferenceArg = argSort == Type.OBJECT || argSort == Type.ARRAY;
              boolean isBooleanArg = Type.BOOLEAN_TYPE.equals(argType);

              if (isReferenceArg) {
                parameterEquations.add(new Equation<Key, Value>(new Key(method, new In(i), stable), new Final<Key, Value>(Value.Top)));
              }
              if (isReferenceArg && isInterestingResult) {
                contractEquations.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.Null), stable), new Final<Key, Value>(Value.Top)));
                contractEquations.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.NotNull), stable), new Final<Key, Value>(Value.Top)));
              }
              if (isBooleanArg && isInterestingResult) {
                contractEquations.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.False), stable), new Final<Key, Value>(Value.Top)));
                contractEquations.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.True), stable), new Final<Key, Value>(Value.Top)));
              }
            }
            if (isReferenceResult) {
              contractEquations.add(new Equation<Key, Value>(new Key(method, new Out(), stable), new Final<Key, Value>(Value.Top)));
            }
          }
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Throwable e) {
          // incorrect bytecode may result in Runtime exceptions during analysis
          // so here we suppose that exception is due to incorrect bytecode
          LOG.debug("Unexpected Error during processing of " + method, e);
        }
      }
    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

    return new ClassEquations(parameterEquations, contractEquations);
  }
}
