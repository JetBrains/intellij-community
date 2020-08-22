// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.graphInference;

import com.intellij.psi.PsiType;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


public final class InferenceVariablesOrder {
  public static List<InferenceVariable> resolveOrder(List<InferenceVariable> vars,
                                                     Map<InferenceVariable, Set<InferenceVariable>> depMap) {
    if (vars.size() < 2) return vars;
    InferenceVariable result = resolveOrderFast(vars, depMap);
    if (result != null) return Collections.singletonList(result);
    Collection<? extends InferenceGraphNode<InferenceVariable>> allNodes = buildInferenceGraph(vars, depMap).values();
    return InferenceGraphNode.merge(tarjan(allNodes, 1).get(0), allNodes).getValue();
  }

  @Nullable
  private static InferenceVariable resolveOrderFast(List<InferenceVariable> vars,
                                                          Map<InferenceVariable, Set<InferenceVariable>> depMap) {
    // Fast-path to find the first resolve group if it consists of single var
    InferenceVariable var = vars.get(0);
    if (var.getInstantiation() != PsiType.NULL || depMap.get(var).isEmpty()) {
      return var;
    }
    Set<InferenceVariable> visited = new HashSet<>();
    while (visited.add(var)) {
      if (var.getInstantiation() != PsiType.NULL) {
        return var;
      }
      Set<InferenceVariable> deps = depMap.get(var);
      if (deps.isEmpty()) {
        return var;
      }
      InferenceVariable nextVar = ContainerUtil.find(deps, v -> vars.contains(v));
      if (nextVar == null) {
        return var;
      }
      var = nextVar;
    }
    return null;
  }

  public static Iterator<List<InferenceVariable>> resolveOrderIterator(Collection<? extends InferenceVariable> vars, InferenceSession session) {
    Map<InferenceVariable, InferenceGraphNode<InferenceVariable>> nodes = buildInferenceGraph(vars, session);
    final ArrayList<InferenceGraphNode<InferenceVariable>> acyclicNodes = initNodes(nodes.values());
    return ContainerUtil.map(acyclicNodes, node -> node.getValue()).iterator();
  }

  public static Map<InferenceVariable, Set<InferenceVariable>> getDependencies(
    Collection<? extends InferenceVariable> vars, InferenceSession session) {

    Map<InferenceVariable, Set<InferenceVariable>> map = new THashMap<>();
    for (InferenceVariable var : vars) {
      map.put(var, var.getDependencies(session));
    }
    return map;
  }

  @NotNull
  private static Map<InferenceVariable, InferenceGraphNode<InferenceVariable>> buildInferenceGraph(
    Collection<? extends InferenceVariable> vars, InferenceSession session) {

    return buildInferenceGraph(vars, getDependencies(vars, session));
  }

  @NotNull
  private static Map<InferenceVariable, InferenceGraphNode<InferenceVariable>> buildInferenceGraph(
    Collection<? extends InferenceVariable> vars, Map<InferenceVariable, Set<InferenceVariable>> depMap) {

    Map<InferenceVariable, InferenceGraphNode<InferenceVariable>> nodes = new LinkedHashMap<>(vars.size()*4/3);
    for (InferenceVariable var : vars) {
      nodes.put(var, new InferenceGraphNode<>(var));
    }

    for (Map.Entry<InferenceVariable, InferenceGraphNode<InferenceVariable>> entry : nodes.entrySet()) {
      InferenceVariable var = entry.getKey();
      if (var.getInstantiation() != PsiType.NULL) continue;
      final InferenceGraphNode<InferenceVariable> node = entry.getValue();
      final Set<InferenceVariable> dependencies = depMap.get(var);
      for (InferenceVariable dependentVariable : dependencies) {
        final InferenceGraphNode<InferenceVariable> dependency = nodes.get(dependentVariable);
        if (dependency != null) {
          node.addDependency(dependency);
        }
      }
    }
    return nodes;
  }

  public static <T> List<List<InferenceGraphNode<T>>> tarjan(Collection<? extends InferenceGraphNode<T>> nodes) {
    return tarjan(nodes, Integer.MAX_VALUE);
  }

  public static <T> List<List<InferenceGraphNode<T>>> tarjan(Collection<? extends InferenceGraphNode<T>> nodes, int limit) {
    final ArrayList<List<InferenceGraphNode<T>>> result = new ArrayList<>();
    final Stack<InferenceGraphNode<T>> currentStack = new Stack<>();
    int index = 0;
    for (InferenceGraphNode<T> node : nodes) {
      if (node.index == -1) {
        index += InferenceGraphNode.strongConnect(node, index, currentStack, result);
        if (result.size() >= limit) break;
      }
    }
    return result;
  }

  public static <T> ArrayList<InferenceGraphNode<T>> initNodes(Collection<? extends InferenceGraphNode<T>> allNodes) {
    final List<List<InferenceGraphNode<T>>> nodes = tarjan(allNodes);
    final ArrayList<InferenceGraphNode<T>> acyclicNodes = new ArrayList<>();
    for (List<InferenceGraphNode<T>> cycle : nodes) {
      acyclicNodes.add(InferenceGraphNode.merge(cycle, allNodes));
    }
    return acyclicNodes;
  }

  public static class InferenceGraphNode<T> {
    private final List<T> myValue = new ArrayList<>();
    private final Set<InferenceGraphNode<T>> myDependencies = new LinkedHashSet<>();

    private int index = -1;
    private int lowlink;

    public InferenceGraphNode(T value) {
      myValue.add(value);
    }

    public List<T> getValue() {
      return myValue;
    }

    public Set<InferenceGraphNode<T>> getDependencies() {
      return myDependencies;
    }

    public void addDependency(InferenceGraphNode<T> node) {
      myDependencies.add(node);
    }


    private static <T> InferenceGraphNode<T> merge(final List<? extends InferenceGraphNode<T>> cycle,
                                                   final Collection<? extends InferenceGraphNode<T>> allNodes) {
      assert !cycle.isEmpty();
      final InferenceGraphNode<T> root = cycle.get(0);
      if (cycle.size() > 1) {
        for (int i = 1; i < cycle.size(); i++) {
          final InferenceGraphNode<T> cycleNode = cycle.get(i);

          root.copyFrom(cycleNode);
          root.filterInterCycleDependencies();

          for (InferenceGraphNode<T> node : allNodes) {
            if (node.myDependencies.remove(cycleNode)) {
              node.myDependencies.add(root);
            }
          }
        }
      }
      return root;
    }

    private void filterInterCycleDependencies() {
      boolean includeSelfDependency = false;
      for (Iterator<InferenceGraphNode<T>> iterator = myDependencies.iterator(); iterator.hasNext(); ) {
        InferenceGraphNode<T> d = iterator.next();
        assert d.myValue.size() >= 1;
        final T initialNodeValue = d.myValue.get(0);
        if (myValue.contains(initialNodeValue)) {
          includeSelfDependency = true;
          iterator.remove();
        }
      }

      if (includeSelfDependency) {
        myDependencies.add(this);
      }
    }

    private void copyFrom(final InferenceGraphNode<T> cycleNode) {
      myValue.addAll(cycleNode.myValue);
      myDependencies.addAll(cycleNode.myDependencies);
    }

    private static <T> int strongConnect(InferenceGraphNode<T> currentNode,
                                         int index,
                                         Stack<InferenceGraphNode<T>> currentStack,
                                         ArrayList<? super List<InferenceGraphNode<T>>> result) {
      currentNode.index = index;
      currentNode.lowlink = index;
      index++;

      currentStack.push(currentNode);

      for (InferenceGraphNode<T> dependantNode : currentNode.getDependencies()) {
        if (dependantNode.index == -1) {
          strongConnect(dependantNode, index, currentStack, result);
          currentNode.lowlink = Math.min(currentNode.lowlink, dependantNode.lowlink);
        }
        else if (currentStack.contains(dependantNode)) {
          currentNode.lowlink = Math.min(currentNode.lowlink, dependantNode.index);
        }
      }

      if (currentNode.lowlink == currentNode.index) {
        final ArrayList<InferenceGraphNode<T>> arrayList = new ArrayList<>();
        InferenceGraphNode<T> cyclicNode;
        do {
          cyclicNode = currentStack.pop();
          arrayList.add(cyclicNode);
        }
        while (cyclicNode != currentNode);
        result.add(arrayList);
      }
      return index;
    }
  }
}
