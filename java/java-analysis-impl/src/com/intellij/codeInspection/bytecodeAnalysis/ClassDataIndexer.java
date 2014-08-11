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
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.*;
import org.jetbrains.org.objectweb.asm.tree.MethodNode;
import org.jetbrains.org.objectweb.asm.tree.analysis.*;

import java.security.MessageDigest;
import java.util.*;

import static com.intellij.codeInspection.bytecodeAnalysis.ProjectBytecodeAnalysis.LOG;

/**
 * @author lambdamix
 */
public class ClassDataIndexer implements DataIndexer<HKey, HResult, FileContent> {

  @NotNull
  @Override
  public Map<HKey, HResult> map(@NotNull FileContent inputData) {
    HashMap<HKey, HResult> map = new HashMap<HKey, HResult>();
    try {
      MessageDigest md = BytecodeAnalysisConverter.getMessageDigest();
      ClassEquations rawEquations = processClass(new ClassReader(inputData.getContent()), inputData.getFile().getPresentableUrl());
      List<Equation<Key, Value>> rawParameterEquations = rawEquations.parameterEquations;
      List<Equation<Key, Value>> rawContractEquations = rawEquations.contractEquations;

      for (Equation<Key, Value> rawParameterEquation: rawParameterEquations) {
        HEquation equation = BytecodeAnalysisConverter.convert(rawParameterEquation, md);
        map.put(equation.key, equation.result);
      }
      for (Equation<Key, Value> rawContractEquation: rawContractEquations) {
        HEquation equation = BytecodeAnalysisConverter.convert(rawContractEquation, md);
        map.put(equation.key, equation.result);
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

  public static ClassEquations processClass(final ClassReader classReader, final String presentableUrl) {
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

        final Method method = new Method(className, methodNode.name, methodNode.desc);
        int access = methodNode.access;
        final boolean stable =
          stableClass ||
          (access & Opcodes.ACC_FINAL) != 0 ||
          (access & Opcodes.ACC_PRIVATE) != 0 ||
          (access & Opcodes.ACC_STATIC) != 0 ||
          "<init>".equals(methodNode.name);
        try {
          boolean added = false;
          final ControlFlowGraph graph = cfg.buildControlFlowGraph(className, methodNode);

          boolean maybeLeakingParameter = isInterestingResult;
          for (Type argType : argumentTypes) {
            int argSort = argType.getSort();
            if (argSort == Type.OBJECT || argSort == Type.ARRAY) {
              maybeLeakingParameter = true;
              break;
            }
          }

          if (graph.transitions.length > 0) {
            final DFSTree dfs = cfg.buildDFSTree(graph.transitions);

            boolean complex = !dfs.back.isEmpty();
            if (!complex) {
              for (int[] transition : graph.transitions) {
                if (transition != null && transition.length > 1) {
                  complex = true;
                  break;
                }
              }
            }


            boolean reducible = dfs.back.isEmpty() || cfg.reducible(graph, dfs);
            // TODO - switch to complex/simple when ready
            if (true) {
              if (reducible) {

                final Pair<boolean[], Frame<org.jetbrains.org.objectweb.asm.tree.analysis.Value>[]> pair =
                  maybeLeakingParameter ?
                  (argumentTypes.length < 32 ? cfg.fastLeakingParameters(className, methodNode) : cfg.leakingParameters(className, methodNode)) :
                  null;
                boolean[] leakingParameters =  pair != null ? pair.first : null;
                final NullableLazyValue<boolean[]> resultOrigins = new NullableLazyValue<boolean[]>() {
                  @Override
                  protected boolean[] compute() {
                    try {
                      return OriginsAnalysis.resultOrigins(pair.second, methodNode.instructions, graph);
                    }
                    catch (AnalyzerException e) {
                      LOG.debug("when processing " + method + " in " + presentableUrl, e);
                      return null;
                    }
                  }
                };

                NotNullLazyValue<Equation<Key, Value>> resultEquation = new NotNullLazyValue<Equation<Key, Value>>() {
                  @NotNull
                  @Override
                  protected Equation<Key, Value> compute() {
                    boolean[] origins = resultOrigins.getValue();
                    if (origins != null) {
                      try {
                        return new InOutAnalysis(new RichControlFlow(graph, dfs), new Out(), origins, stable).analyze();
                      }
                      catch (AnalyzerException ignored) {
                      }
                    }
                    return new Equation<Key, Value>(new Key(method, new Out(), stable), new Final<Key, Value>(Value.Top));
                  }
                };

                if (isReferenceResult) {
                  contractEquations.add(resultEquation.getValue());
                }

                for (int i = 0; i < argumentTypes.length; i++) {
                  Type argType = argumentTypes[i];
                  int argSort = argType.getSort();
                  boolean isReferenceArg = argSort == Type.OBJECT || argSort == Type.ARRAY;
                  boolean isBooleanArg = Type.BOOLEAN_TYPE.equals(argType);
                  boolean notNullParam = false;
                  if (isReferenceArg) {
                    if (leakingParameters[i]) {
                      Equation<Key, Value> notNullParamEquation = new NonNullInAnalysis(new RichControlFlow(graph, dfs), new In(i), stable).analyze();
                      notNullParam = notNullParamEquation.rhs.equals(new Final<Key, Value>(Value.NotNull));
                      parameterEquations.add(notNullParamEquation);
                    }
                    else {
                      // parameter is not leaking, so it is definitely NOT @NotNull
                      parameterEquations.add(new Equation<Key, Value>(new Key(method, new In(i), stable), new Final<Key, Value>(Value.Top)));
                    }
                  }
                  if (isReferenceArg && isInterestingResult) {
                    if (leakingParameters[i]) {
                      if (resultOrigins.getValue() != null) {
                        // result origins analysis was ok
                        if (!notNullParam) {
                          // may be null on some branch
                          contractEquations.add(
                            new InOutAnalysis(new RichControlFlow(graph, dfs), new InOut(i, Value.Null), resultOrigins.getValue(), stable)
                              .analyze());
                        }
                        else {
                          // definitely NPE -> bottom
                          contractEquations.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.Null), stable), new Final<Key, Value>(Value.Bot)));
                        }
                        contractEquations.add(
                          new InOutAnalysis(new RichControlFlow(graph, dfs), new InOut(i, Value.NotNull), resultOrigins.getValue(), stable)
                            .analyze());
                      }
                      else {
                        // result origins  analysis failed, approximating to Top
                        contractEquations.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.Null), stable), new Final<Key, Value>(Value.Top)));
                        contractEquations.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.NotNull), stable), new Final<Key, Value>(Value.Top)));
                      }
                    }
                    else {
                      // parameter is not leaking, so a contract is the same as for the whole method
                      contractEquations
                        .add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.Null), stable), resultEquation.getValue().rhs));
                      contractEquations
                        .add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.NotNull), stable), resultEquation.getValue().rhs));
                    }
                  }
                  if (isBooleanArg && isInterestingResult) {
                    if (leakingParameters[i]) {
                      if (resultOrigins.getValue() != null) {
                        // result origins analysis was ok
                        contractEquations.add(
                          new InOutAnalysis(new RichControlFlow(graph, dfs), new InOut(i, Value.False), resultOrigins.getValue(), stable)
                            .analyze());
                        contractEquations.add(
                          new InOutAnalysis(new RichControlFlow(graph, dfs), new InOut(i, Value.True), resultOrigins.getValue(), stable)
                            .analyze());
                      }
                      else {
                        // result origins  analysis failed, approximating to Top
                        contractEquations.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.False), stable), new Final<Key, Value>(Value.Top)));
                        contractEquations.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.True), stable), new Final<Key, Value>(Value.Top)));
                      }
                    }
                    else {
                      // parameter is not leaking, so a contract is the same as for the whole method
                      contractEquations
                        .add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.False), stable), resultEquation.getValue().rhs));
                      contractEquations
                        .add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.True), stable), resultEquation.getValue().rhs));
                    }
                  }
                }
                added = true;
              }
              else {
                LOG.debug("CFG for " + method + " is not reducible");
              }
            }
            // simple
            else {
              CombinedSingleAnalysis analyzer = new CombinedSingleAnalysis(method, graph);
              analyzer.analyze();
              added = true;
            }
          }

          if (!added) {
            if (isReferenceResult) {
              contractEquations.add(new Equation<Key, Value>(new Key(method, new Out(), stable), new Final<Key, Value>(Value.Top)));
            }
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
          }
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Throwable e) {
          // incorrect bytecode may result in Runtime exceptions during analysis
          // so here we suppose that exception is due to incorrect bytecode
          LOG.debug("Unexpected Error during processing of " + method + " in " + presentableUrl, e);
        }
      }
    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

    return new ClassEquations(parameterEquations, contractEquations);
  }
}
