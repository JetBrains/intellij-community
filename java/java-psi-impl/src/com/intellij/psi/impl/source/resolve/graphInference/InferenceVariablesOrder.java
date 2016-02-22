/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.graphInference;

import com.intellij.psi.PsiType;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;

import java.util.*;

/**
 * User: anna
 * Date: 7/4/13
 */

public class InferenceVariablesOrder {
  public static List<InferenceVariable> resolveOrder(Collection<InferenceVariable> vars, InferenceSession session) {
    return resolveOrderIterator(vars, session).next();
  }
  
  public static Iterator<List<InferenceVariable>> resolveOrderIterator(Collection<InferenceVariable> vars, InferenceSession session) {
    Map<InferenceVariable, InferenceGraphNode<InferenceVariable>> nodes =
      new LinkedHashMap<InferenceVariable, InferenceGraphNode<InferenceVariable>>();
    for (InferenceVariable var : vars) {
      nodes.put(var, new InferenceGraphNode<InferenceVariable>(var));
    }

    for (InferenceVariable var : vars) {
      if (var.getInstantiation() != PsiType.NULL) continue;
      final InferenceGraphNode<InferenceVariable> node = nodes.get(var);
      final Set<InferenceVariable> dependencies = var.getDependencies(session);
      for (InferenceVariable dependentVariable : dependencies) {
        final InferenceGraphNode<InferenceVariable> dependency = nodes.get(dependentVariable);
        if (dependency != null) {
          node.addDependency(dependency);
        }
      }
    }
    final ArrayList<InferenceGraphNode<InferenceVariable>> acyclicNodes = initNodes(nodes.values());
    return ContainerUtil.map(acyclicNodes, new Function<InferenceGraphNode<InferenceVariable>, List<InferenceVariable>>() {
      @Override
      public List<InferenceVariable> fun(InferenceGraphNode<InferenceVariable> node) {
        return node.getValue();
      }
    }).iterator();
  }

  public static <T> List<List<InferenceGraphNode<T>>> tarjan(Collection<InferenceGraphNode<T>> nodes) {
    final ArrayList<List<InferenceGraphNode<T>>> result = new ArrayList<List<InferenceGraphNode<T>>>();
    final Stack<InferenceGraphNode<T>> currentStack = new Stack<InferenceGraphNode<T>>();
    int index = 0;
    for (InferenceGraphNode<T> node : nodes) {
      if (node.index == -1) {
        index += InferenceGraphNode.strongConnect(node, index, currentStack, result);
      }
    }
    return result;
  }

  public static <T> ArrayList<InferenceGraphNode<T>> initNodes(Collection<InferenceGraphNode<T>> allNodes) {
    final List<List<InferenceGraphNode<T>>> nodes = tarjan(allNodes);
    final ArrayList<InferenceGraphNode<T>> acyclicNodes = new ArrayList<InferenceGraphNode<T>>();
    for (List<InferenceGraphNode<T>> cycle : nodes) {
      acyclicNodes.add(InferenceGraphNode.merge(cycle, allNodes));
    }
    return acyclicNodes;
  }

  public static class InferenceGraphNode<T> {
    private final List<T> myValue = new ArrayList<T>();
    private final Set<InferenceGraphNode<T>> myDependencies = new LinkedHashSet<InferenceGraphNode<T>>();

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


    private static <T> InferenceGraphNode<T> merge(final List<InferenceGraphNode<T>> cycle,
                                                   final Collection<InferenceGraphNode<T>> allNodes) {
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
                                         ArrayList<List<InferenceGraphNode<T>>> result) {
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
        final ArrayList<InferenceGraphNode<T>> arrayList = new ArrayList<InferenceGraphNode<T>>();
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
