// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.bytecodeAnalysis.asm;

import com.intellij.codeInspection.bytecodeAnalysis.asm.ControlFlowGraph.Edge;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.org.objectweb.asm.tree.MethodNode;
import org.jetbrains.org.objectweb.asm.tree.analysis.AnalyzerException;

import java.util.HashSet;
import java.util.Set;

import static org.jetbrains.org.objectweb.asm.Opcodes.ACC_ABSTRACT;
import static org.jetbrains.org.objectweb.asm.Opcodes.ACC_NATIVE;

public final class ControlFlowGraph {
  public static final class Edge {
    public final int from, to;

    public Edge(int from, int to) {
      this.from = from;
      this.to = to;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Edge edge)) {
        return false;
      }
      return from == edge.from && to == edge.to;
    }

    @Override
    public int hashCode() {
      return 31 * from + to;
    }
  }

  public final String className;
  public final MethodNode methodNode;
  public final int[][] transitions;
  public final int edgeCount;
  public final boolean[] errors;
  public final Set<Edge> errorTransitions;
  /**
   * Where execution goes if NPE occurs at given instruction
   */
  public final Int2IntMap npeTransitions;

  ControlFlowGraph(String className,
                   MethodNode methodNode,
                   int[][] transitions,
                   int edgeCount,
                   boolean[] errors,
                   Set<Edge> errorTransitions,
                   Int2IntMap npeTransitions) {
    this.className = className;
    this.methodNode = methodNode;
    this.transitions = transitions;
    this.edgeCount = edgeCount;
    this.errors = errors;
    this.errorTransitions = errorTransitions;
    this.npeTransitions = npeTransitions;
  }

  public static ControlFlowGraph build(String className, MethodNode methodNode, boolean jsr) throws AnalyzerException {
    return new ControlFlowBuilder(className, methodNode, jsr).buildCFG();
  }
}

final class ControlFlowBuilder implements FramelessAnalyzer.EdgeCreator {
  final String className;
  final MethodNode methodNode;
  final IntArrayList[] transitions;
  final Set<Edge> errorTransitions;
  final Int2IntMap npeTransitions;
  final FramelessAnalyzer myAnalyzer;
  private final boolean[] errors;
  private int edgeCount;

  ControlFlowBuilder(String className, MethodNode methodNode, boolean jsr) {
    myAnalyzer = jsr ? new FramelessAnalyzer(this) : new LiteFramelessAnalyzer(this);
    this.className = className;
    this.methodNode = methodNode;
    transitions = new IntArrayList[methodNode.instructions.size()];
    errors = new boolean[methodNode.instructions.size()];
    for (int i = 0; i < transitions.length; i++) {
      transitions[i] = new IntArrayList();
    }
    errorTransitions = new HashSet<>();
    npeTransitions = new Int2IntOpenHashMap();
  }

  ControlFlowGraph buildCFG() throws AnalyzerException {
    if ((methodNode.access & (ACC_ABSTRACT | ACC_NATIVE)) == 0) {
      myAnalyzer.analyze(methodNode);
    }
    int[][] resultTransitions = new int[transitions.length][];
    for (int i = 0; i < resultTransitions.length; i++) {
      resultTransitions[i] = transitions[i].toIntArray();
    }
    return new ControlFlowGraph(className, methodNode, resultTransitions, edgeCount, errors, errorTransitions, npeTransitions);
  }

  @Override
  public void newControlFlowEdge(int insn, int successor) {
    if (!transitions[insn].contains(successor)) {
      transitions[insn].add(successor);
      edgeCount++;
    }
  }

  @Override
  public void newControlFlowExceptionEdge(int insn, int successor, boolean npe) {
    if (!transitions[insn].contains(successor)) {
      transitions[insn].add(successor);
      edgeCount++;
      Edge edge = new Edge(insn, successor);
      errorTransitions.add(edge);
      if (npe && !npeTransitions.containsKey(insn)) {
        npeTransitions.put(insn, successor);
      }
      errors[successor] = true;
    }
  }
}