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
package com.intellij.codeInspection.bytecodeAnalysis.asm;

import org.jetbrains.org.objectweb.asm.tree.MethodNode;

import java.util.Set;

/**
 * @author lambdamix
 */
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
      if (!(o instanceof Edge)) {
        return false;
      }
      Edge edge = (Edge) o;
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

  public ControlFlowGraph(String className, MethodNode methodNode, int[][] transitions, int edgeCount, boolean[] errors, Set<Edge> errorTransitions) {
    this.className = className;
    this.methodNode = methodNode;
    this.transitions = transitions;
    this.edgeCount = edgeCount;
    this.errors = errors;
    this.errorTransitions = errorTransitions;
  }
}
