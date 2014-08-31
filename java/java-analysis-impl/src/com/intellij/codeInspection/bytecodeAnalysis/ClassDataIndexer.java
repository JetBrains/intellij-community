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

import com.intellij.codeInspection.bytecodeAnalysis.asm.*;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.*;
import org.jetbrains.org.objectweb.asm.tree.MethodNode;
import org.jetbrains.org.objectweb.asm.tree.analysis.AnalyzerException;

import java.security.MessageDigest;
import java.util.*;

import static com.intellij.codeInspection.bytecodeAnalysis.Direction.*;
import static com.intellij.codeInspection.bytecodeAnalysis.ProjectBytecodeAnalysis.LOG;

/**
 * @author lambdamix
 */
public class ClassDataIndexer implements DataIndexer<Bytes, HEquations, FileContent> {

  private static final int STABLE_FLAGS = Opcodes.ACC_FINAL | Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC;
  public static final Final<Key, Value> FINAL_TOP = new Final<Key, Value>(Value.Top);
  public static final Final<Key, Value> FINAL_BOT = new Final<Key, Value>(Value.Bot);
  public static final Final<Key, Value> FINAL_NOT_NULL = new Final<Key, Value>(Value.NotNull);
  public static final Final<Key, Value> FINAL_NULL = new Final<Key, Value>(Value.Null);
  private static final List<Equation<Key, Value>> EMPTY_EQUATIONS = Collections.EMPTY_LIST;

  @NotNull
  @Override
  public Map<Bytes, HEquations> map(@NotNull FileContent inputData) {
    HashMap<Bytes, HEquations> map = new HashMap<Bytes, HEquations>();
    try {
      MessageDigest md = BytecodeAnalysisConverter.getMessageDigest();
      Map<Key, List<Equation<Key, Value>>> rawEquations = processClass(new ClassReader(inputData.getContent()), inputData.getFile().getPresentableUrl());
      for (Map.Entry<Key, List<Equation<Key, Value>>> entry: rawEquations.entrySet()) {
        Key primaryKey = entry.getKey();
        Key serKey = new Key(primaryKey.method, primaryKey.direction, true);

        List<Equation<Key, Value>> equations = entry.getValue();
        List<DirectionResultPair> result = new ArrayList<DirectionResultPair>(equations.size());
        for (Equation<Key, Value> equation : equations) {
          result.add(BytecodeAnalysisConverter.convert(equation, md));
        }
        map.put(new Bytes(BytecodeAnalysisConverter.asmKey(serKey, md).key), new HEquations(result, primaryKey.stable));
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
    //return map;
    return map;
  }

  public static Map<Key, List<Equation<Key, Value>>> processClass(final ClassReader classReader, final String presentableUrl) {

    final Map<Key, List<Equation<Key, Value>>> equations = new HashMap<Key, List<Equation<Key, Value>>>();

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
          private boolean jsr;

          @Override
          public void visitJumpInsn(int opcode, Label label) {
            if (opcode == Opcodes.JSR) {
              jsr = true;
            }
            super.visitJumpInsn(opcode, label);
          }

          @Override
          public void visitEnd() {
            super.visitEnd();
            Pair<Key, List<Equation<Key, Value>>> methodEquations = processMethod(node, jsr);
            equations.put(methodEquations.first, methodEquations.second);
          }
        };
      }

      private Pair<Key, List<Equation<Key, Value>>> processMethod(final MethodNode methodNode, boolean jsr) {
        ProgressManager.checkCanceled();
        final Type[] argumentTypes = Type.getArgumentTypes(methodNode.desc);
        final Type resultType = Type.getReturnType(methodNode.desc);
        final boolean isReferenceResult = ASMUtils.isReferenceType(resultType);
        final boolean isBooleanResult = ASMUtils.isBooleanType(resultType);
        final boolean isInterestingResult = isReferenceResult || isBooleanResult;

        final Method method = new Method(className, methodNode.name, methodNode.desc);
        final boolean stable = stableClass || (methodNode.access & STABLE_FLAGS) != 0 || "<init>".equals(methodNode.name);

        Key primaryKey = new Key(method, Out, stable);
        if (argumentTypes.length == 0 && !isInterestingResult) {
          return Pair.create(primaryKey, EMPTY_EQUATIONS);
        }

        try {
          final ControlFlowGraph graph = ControlFlowGraph.build(className, methodNode, jsr);
          if (graph.transitions.length > 0) {
            final DFSTree dfs = DFSTree.build(graph.transitions, graph.edgeCount);
            boolean branching = !dfs.back.isEmpty();
            if (!branching) {
              for (int[] transition : graph.transitions) {
                if (transition != null && transition.length > 1) {
                  branching = true;
                  break;
                }
              }
            }

            if (branching) {
              RichControlFlow richControlFlow = new RichControlFlow(graph, dfs);
              if (richControlFlow.reducible()) {
                return Pair.create(primaryKey,
                                   processBranchingMethod(method, methodNode, richControlFlow, argumentTypes, isReferenceResult, isInterestingResult, stable, jsr));
              }
              LOG.debug(method + ": CFG is not reducible");
            }
            // simple
            else {
              return Pair.create(primaryKey,
                                 processNonBranchingMethod(method, argumentTypes, graph, isReferenceResult, isBooleanResult, stable));
            }
          }
          return Pair.create(primaryKey, topEquations(method, argumentTypes, isReferenceResult, isInterestingResult, stable));
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Throwable e) {
          // incorrect bytecode may result in Runtime exceptions during analysis
          // so here we suppose that exception is due to incorrect bytecode
          LOG.debug("Unexpected Error during processing of " + method + " in " + presentableUrl, e);
          return Pair.create(primaryKey, topEquations(method, argumentTypes, isReferenceResult, isInterestingResult, stable));
        }
      }

      private List<Equation<Key, Value>> processBranchingMethod(final Method method,
                                                                final MethodNode methodNode,
                                                                final RichControlFlow richControlFlow,
                                                                Type[] argumentTypes,
                                                                boolean isReferenceResult,
                                                                boolean isInterestingResult,
                                                                final boolean stable,
                                                                boolean jsr) throws AnalyzerException {

        List<Equation<Key, Value>> result = new ArrayList<Equation<Key, Value>>(argumentTypes.length * 4 + 2);
        boolean maybeLeakingParameter = isInterestingResult;
        for (Type argType : argumentTypes) {
          if (ASMUtils.isReferenceType(argType) || (isReferenceResult && ASMUtils.isBooleanType(argType))) {
            maybeLeakingParameter = true;
            break;
          }
        }

        final LeakingParameters leakingParametersAndFrames =
          maybeLeakingParameter ? leakingParametersAndFrames(method, methodNode, argumentTypes, jsr) : null;

        boolean[] leakingParameters =
          leakingParametersAndFrames != null ? leakingParametersAndFrames.parameters : null;
        boolean[] leakingNullableParameters =
          leakingParametersAndFrames != null ? leakingParametersAndFrames.nullableParameters : null;

        final boolean[] origins =
          isInterestingResult ?
          OriginsAnalysis.resultOrigins(leakingParametersAndFrames.frames, methodNode.instructions, richControlFlow.controlFlow) :
          null;

        Equation<Key, Value> outEquation =
          isInterestingResult ?
          new InOutAnalysis(richControlFlow, Out, origins, stable).analyze() :
          null;

        if (isReferenceResult) {
          result.add(outEquation);
          result.add(new Equation<Key, Value>(new Key(method, NullableOut, stable), NullableMethodAnalysis.analyze(methodNode, origins, jsr)));
        }

        for (int i = 0; i < argumentTypes.length; i++) {
          boolean isReferenceArg = ASMUtils.isReferenceType(argumentTypes[i]);
          boolean notNullParam = false;

          if (isReferenceArg) {
            boolean possibleNPE = false;
            if (leakingParameters[i]) {
              NonNullInAnalysis notNullInAnalysis = new NonNullInAnalysis(richControlFlow, new In(i, In.NOT_NULL), stable);
              Equation<Key, Value> notNullParamEquation = notNullInAnalysis.analyze();
              possibleNPE = notNullInAnalysis.possibleNPE;
              notNullParam = notNullParamEquation.rhs.equals(FINAL_NOT_NULL);
              result.add(notNullParamEquation);
            }
            else {
              // parameter is not leaking, so it is definitely NOT @NotNull
              result.add(new Equation<Key, Value>(new Key(method, new In(i, In.NOT_NULL), stable), FINAL_TOP));
            }
            if (leakingNullableParameters[i]) {
              if (notNullParam || possibleNPE) {
                result.add(new Equation<Key, Value>(new Key(method, new In(i, In.NULLABLE), stable), FINAL_TOP));
              }
              else {
                result.add(new NullableInAnalysis(richControlFlow, new In(i, In.NULLABLE), stable).analyze());
              }
            }
            else {
              result.add(new Equation<Key, Value>(new Key(method, new In(i, In.NULLABLE), stable), FINAL_NULL));
            }
          }
          if (isReferenceArg && isInterestingResult) {
            if (leakingParameters[i]) {
              if (!notNullParam) {
                // may be null on some branch, running "null->..." analysis
                result.add(new InOutAnalysis(richControlFlow, new InOut(i, Value.Null), origins, stable).analyze());
              }
              else {
                // @NotNull, so "null->fail"
                result.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.Null), stable), FINAL_BOT));
              }
              result.add(new InOutAnalysis(richControlFlow, new InOut(i, Value.NotNull), origins, stable).analyze());
            }
            else {
              // parameter is not leaking, so a contract is the same as for the whole method
              result.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.Null), stable), outEquation.rhs));
              result.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.NotNull), stable), outEquation.rhs));
            }
          }
          if (ASMUtils.isBooleanType(argumentTypes[i]) && isInterestingResult) {
            if (leakingParameters[i]) {
              result.add(new InOutAnalysis(richControlFlow, new InOut(i, Value.False), origins, stable).analyze());
              result.add(new InOutAnalysis(richControlFlow, new InOut(i, Value.True), origins, stable).analyze());
            }
            else {
              // parameter is not leaking, so a contract is the same as for the whole method
              result.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.False), stable), outEquation.rhs));
              result.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.True), stable), outEquation.rhs));
            }
          }
        }
        return result;
      }

      private List<Equation<Key, Value>> processNonBranchingMethod(Method method,
                                                                   Type[] argumentTypes,
                                                                   ControlFlowGraph graph,
                                                                   boolean isReferenceResult,
                                                                   boolean isBooleanResult,
                                                                   boolean stable) throws AnalyzerException {
        List<Equation<Key, Value>> result = new ArrayList<Equation<Key, Value>>(argumentTypes.length * 4 + 2);
        CombinedAnalysis analyzer = new CombinedAnalysis(method, graph);
        analyzer.analyze();
        if (isReferenceResult) {
          result.add(analyzer.outContractEquation(stable));
          result.add(analyzer.nullableResultEquation(stable));
        }
        for (int i = 0; i < argumentTypes.length; i++) {
          Type argType = argumentTypes[i];
          boolean isRefArg = ASMUtils.isReferenceType(argType);
          if (isRefArg) {
            result.add(analyzer.notNullParamEquation(i, stable));
            result.add(analyzer.nullableParamEquation(i, stable));
          }
          if (isRefArg && (isReferenceResult || isBooleanResult)) {
            result.add(analyzer.contractEquation(i, Value.Null, stable));
            result.add(analyzer.contractEquation(i, Value.NotNull, stable));
          }
          if (ASMUtils.isBooleanType(argType) && (isReferenceResult || isBooleanResult)) {
            result.add(analyzer.contractEquation(i, Value.True, stable));
            result.add(analyzer.contractEquation(i, Value.False, stable));
          }
        }
        return result;
      }

      private List<Equation<Key, Value>> topEquations(Method method,
                                                      Type[] argumentTypes,
                                                      boolean isReferenceResult,
                                                      boolean isInterestingResult,
                                                      boolean stable) {
        List<Equation<Key, Value>> result = new ArrayList<Equation<Key, Value>>(argumentTypes.length * 4 + 2);
        if (isReferenceResult) {
          result.add(new Equation<Key, Value>(new Key(method, Out, stable), FINAL_TOP));
          result.add(new Equation<Key, Value>(new Key(method, NullableOut, stable), FINAL_BOT));
        }
        for (int i = 0; i < argumentTypes.length; i++) {
          Type argType = argumentTypes[i];
          boolean isReferenceArg = ASMUtils.isReferenceType(argType);
          boolean isBooleanArg = ASMUtils.isBooleanType(argType);

          if (isReferenceArg) {
            result.add(new Equation<Key, Value>(new Key(method, new In(i, In.NOT_NULL), stable), FINAL_TOP));
            result.add(new Equation<Key, Value>(new Key(method, new In(i, In.NULLABLE), stable), FINAL_TOP));
          }
          if (isReferenceArg && isInterestingResult) {
            result.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.Null), stable), FINAL_TOP));
            result.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.NotNull), stable), FINAL_TOP));
          }
          if (isBooleanArg && isInterestingResult) {
            result.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.False), stable), FINAL_TOP));
            result.add(new Equation<Key, Value>(new Key(method, new InOut(i, Value.True), stable), FINAL_TOP));
          }
        }
        return result;
      }

      private LeakingParameters leakingParametersAndFrames(Method method, MethodNode methodNode, Type[] argumentTypes, boolean jsr)
        throws AnalyzerException {
        return argumentTypes.length < 32 ?
                LeakingParameters.buildFast(method.internalClassName, methodNode, jsr) :
                LeakingParameters.build(method.internalClassName, methodNode, jsr);
      }
    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

    return equations;
  }
}
