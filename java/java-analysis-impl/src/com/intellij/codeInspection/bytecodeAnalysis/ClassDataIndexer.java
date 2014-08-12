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

  private static final int STABLE_FLAGS = Opcodes.ACC_FINAL | Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC;
  public static final Final<Key, Value> FINAL_TOP = new Final<Key, Value>(Value.Top);
  public static final Final<Key, Value> FINAL_BOT = new Final<Key, Value>(Value.Bot);
  public static final Final<Key, Value> FINAL_NOT_NULL = new Final<Key, Value>(Value.NotNull);

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

    final List<Equation<Key, Value>> parameterEqs = new ArrayList<Equation<Key, Value>>();
    final List<Equation<Key, Value>> contractEqs = new ArrayList<Equation<Key, Value>>();

    classReader.accept(new ClassVisitor(Opcodes.ASM5) {
      private String className;
      private boolean stableClass;

      @Override
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        className = name;
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
            processMethod(node);
          }
        };
      }

      private void processMethod(final MethodNode methodNode) {
        ProgressManager.checkCanceled();
        final Type[] argumentTypes = Type.getArgumentTypes(methodNode.desc);
        final Type resultType = Type.getReturnType(methodNode.desc);
        final boolean isReferenceResult = ASMUtils.isReferenceType(resultType);
        final boolean isBooleanResult = ASMUtils.isBooleanType(resultType);
        final boolean isInterestingResult = isReferenceResult || isBooleanResult;

        if (argumentTypes.length == 0 && !isInterestingResult) {
          return;
        }

        final Method method = new Method(className, methodNode.name, methodNode.desc);
        final boolean stable = stableClass || (methodNode.access & STABLE_FLAGS) != 0 || "<init>".equals(methodNode.name);

        try {
          final ControlFlowGraph graph = cfg.buildControlFlowGraph(className, methodNode);
          if (graph.transitions.length > 0) {
            final DFSTree dfs = cfg.buildDFSTree(graph.transitions, graph.edgeCount);
            boolean complex = !dfs.back.isEmpty();
            if (!complex) {
              for (int[] transition : graph.transitions) {
                if (transition != null && transition.length > 1) {
                  complex = true;
                  break;
                }
              }
            }

            if (complex) {
              // reducible?
              if (dfs.back.isEmpty() || cfg.reducible(graph, dfs)) {
                processBranchingMethod(method, methodNode, graph, dfs, argumentTypes, isReferenceResult, isInterestingResult, stable);
                return;
              }
              LOG.debug(method + ": CFG is not reducible");
            }
            // simple
            else {
              processNonBranchingMethod(method, argumentTypes, graph, isReferenceResult, isBooleanResult, stable);
              return;
            }
          }

          // default top equations
          if (isReferenceResult) {
            contractEqs.add(new Equation<Key, Value>(new Key(method, new Out(), stable), FINAL_TOP));
          }
          for (int i = 0; i < argumentTypes.length; i++) {
            Type argType = argumentTypes[i];
            boolean isReferenceArg = ASMUtils.isReferenceType(argType);
            boolean isBooleanArg = ASMUtils.isBooleanType(argType);

            if (isReferenceArg) {
              parameterEqs.add(new Equation<Key, Value>(new Key(method, new In(i), stable), FINAL_TOP));
            }
            if (isReferenceArg && isInterestingResult) {
              contractEqs.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.Null), stable), FINAL_TOP));
              contractEqs.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.NotNull), stable), FINAL_TOP));
            }
            if (isBooleanArg && isInterestingResult) {
              contractEqs.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.False), stable), FINAL_TOP));
              contractEqs.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.True), stable), FINAL_TOP));
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

      private void processBranchingMethod(final Method method,
                                          final MethodNode methodNode,
                                          final ControlFlowGraph graph,
                                          final DFSTree dfs,
                                          Type[] argumentTypes,
                                          boolean isReferenceResult,
                                          boolean isInterestingResult,
                                          final boolean stable) throws AnalyzerException {

        boolean maybeLeakingParameter = isInterestingResult;
        for (Type argType : argumentTypes) {
          if (ASMUtils.isReferenceType(argType) || (isReferenceResult && ASMUtils.isBooleanType(argType))) {
            maybeLeakingParameter = true;
            break;
          }
        }

        final Pair<boolean[], Frame<org.jetbrains.org.objectweb.asm.tree.analysis.Value>[]> leakingParametersAndFrames =
          maybeLeakingParameter ? leakingParametersAndFrames(method, methodNode, argumentTypes) : null;
        boolean[] leakingParameters =
          leakingParametersAndFrames != null ? leakingParametersAndFrames.first : null;

        final RichControlFlow richControlFlow = new RichControlFlow(graph, dfs);

        final NullableLazyValue<boolean[]> origins = new NullableLazyValue<boolean[]>() {
          @Override
          protected boolean[] compute() {
            try {
              return OriginsAnalysis.resultOrigins(leakingParametersAndFrames.second, methodNode.instructions, graph);
            }
            catch (AnalyzerException e) {
              LOG.debug("when processing " + method + " in " + presentableUrl, e);
              return null;
            }
          }
        };

        NotNullLazyValue<Equation<Key, Value>> outEquation = new NotNullLazyValue<Equation<Key, Value>>() {
          @NotNull
          @Override
          protected Equation<Key, Value> compute() {
            if (origins.getValue() != null) {
              try {
                return new InOutAnalysis(richControlFlow, new Out(), origins.getValue(), stable).analyze();
              }
              catch (AnalyzerException ignored) {
              }
            }
            return new Equation<Key, Value>(new Key(method, new Out(), stable), FINAL_TOP);
          }
        };

        if (isReferenceResult) {
          contractEqs.add(outEquation.getValue());
        }

        for (int i = 0; i < argumentTypes.length; i++) {
          boolean isReferenceArg = ASMUtils.isReferenceType(argumentTypes[i]);
          boolean notNullParam = false;

          if (isReferenceArg) {
            if (leakingParameters[i]) {
              Equation<Key, Value> notNullParamEquation = new NonNullInAnalysis(richControlFlow, new In(i), stable).analyze();
              notNullParam = notNullParamEquation.rhs.equals(FINAL_NOT_NULL);
              parameterEqs.add(notNullParamEquation);
            }
            else {
              // parameter is not leaking, so it is definitely NOT @NotNull
              parameterEqs.add(new Equation<Key, Value>(new Key(method, new In(i), stable), FINAL_TOP));
            }
          }
          if (isReferenceArg && isInterestingResult) {
            if (leakingParameters[i]) {
              if (origins.getValue() != null) {
                // result origins analysis was ok
                if (!notNullParam) {
                  // may be null on some branch, running "null->..." analysis
                  contractEqs.add(new InOutAnalysis(richControlFlow, new InOut(i, Value.Null), origins.getValue(), stable).analyze());
                }
                else {
                  // @NotNull, so "null->fail"
                  contractEqs.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.Null), stable), FINAL_BOT));
                }
                contractEqs.add(new InOutAnalysis(richControlFlow, new InOut(i, Value.NotNull), origins.getValue(), stable).analyze());
              }
              else {
                // result origins  analysis failed, approximating to Top
                contractEqs.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.Null), stable), FINAL_TOP));
                contractEqs.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.NotNull), stable), FINAL_TOP));
              }
            }
            else {
              // parameter is not leaking, so a contract is the same as for the whole method
              contractEqs.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.Null), stable), outEquation.getValue().rhs));
              contractEqs.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.NotNull), stable), outEquation.getValue().rhs));
            }
          }
          if (ASMUtils.isBooleanType(argumentTypes[i]) && isInterestingResult) {
            if (leakingParameters[i]) {
              if (origins.getValue() != null) {
                // result origins analysis was ok
                contractEqs.add(new InOutAnalysis(richControlFlow, new InOut(i, Value.False), origins.getValue(), stable).analyze());
                contractEqs.add(new InOutAnalysis(richControlFlow, new InOut(i, Value.True), origins.getValue(), stable).analyze());
              }
              else {
                // result origins  analysis failed, approximating to Top
                contractEqs.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.False), stable), FINAL_TOP));
                contractEqs.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.True), stable), FINAL_TOP));
              }
            }
            else {
              // parameter is not leaking, so a contract is the same as for the whole method
              contractEqs.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.False), stable), outEquation.getValue().rhs));
              contractEqs.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.True), stable), outEquation.getValue().rhs));
            }
          }
        }
      }

      private void processNonBranchingMethod(Method method,
                                             Type[] argumentTypes,
                                             ControlFlowGraph graph,
                                             boolean isReferenceResult,
                                             boolean isBooleanResult,
                                             boolean stable) throws AnalyzerException {
        CombinedSingleAnalysis analyzer = new CombinedSingleAnalysis(method, graph);
        analyzer.analyze();
        if (isReferenceResult) {
          contractEqs.add(analyzer.outContractEquation(stable));
        }
        for (int i = 0; i < argumentTypes.length; i++) {
          Type argType = argumentTypes[i];
          boolean isRefArg = ASMUtils.isReferenceType(argType);
          if (isRefArg) {
            parameterEqs.add(analyzer.notNullParamEquation(i, stable));
          }
          if (isRefArg && (isReferenceResult || isBooleanResult)) {
            contractEqs.add(analyzer.nullContractEquation(i, stable));
            contractEqs.add(analyzer.notNullContractEquation(i, stable));
          }
          if (ASMUtils.isBooleanType(argType) && (isReferenceResult || isBooleanResult)) {
            contractEqs.add(analyzer.trueContractEquation(i, stable));
            contractEqs.add(analyzer.falseContractEquation(i, stable));
          }
        }
      }

      private Pair<boolean[], Frame<org.jetbrains.org.objectweb.asm.tree.analysis.Value>[]> leakingParametersAndFrames(Method method,
                                                                                                                       MethodNode methodNode,
                                                                                                                       Type[] argumentTypes)
        throws AnalyzerException {
        return (argumentTypes.length < 32 ? cfg.fastLeakingParameters(method.internalClassName, methodNode) : cfg.leakingParameters(method.internalClassName, methodNode));
      }
    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

    return new ClassEquations(parameterEqs, contractEqs);
  }
}
