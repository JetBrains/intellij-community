package org.jetbrains.jps.dag

import org.jetbrains.jps.Project

/**
 * @author nik
 */
class DagBuilder<T> {
  private final Closure nodeFactory
  private final Closure outsIterator

  def DagBuilder(Closure nodeFactory, Closure outsIterator) {
    this.nodeFactory = nodeFactory
    this.outsIterator = outsIterator
  }

  public List<DagNode<T>> build(Project project, Collection<T> modules) {
    Set<DagNode<T>> workingSet = convertToNodes(modules)
    reduceCycles(workingSet)
    sort(workingSet)
  }

  private Set<DagNode<T>> convertToNodes(Collection<T> elements) {
    Map<T, DagNode<T>> mapping = [:]

    Set<DagNode<T>> workingSet = new LinkedHashSet<DagNode<T>>()
    elements.each {
      def node = nodeFactory()
      node.elements << it
      workingSet << node
      mapping[it] = node
    }

    mapping.each {T element, DagNode<T> node ->
      outsIterator(element) {
        node.addOut(mapping[it])
      }
    }

    return workingSet
  }

  private List<DagNode<T>> sort(Set<DagNode<T>> set) {
    Set<DagNode<T>> sorted = new LinkedHashSet<DagNode<T>>()
    for (chunk in set) {
      traverse(chunk, sorted)
    }

    sorted.asList()
  }

  private def traverse(DagNode<T> chunk, Set<DagNode<T>> traversed) {
    if (traversed.contains(chunk)) return
    for (out in chunk.outs) {
      traverse(out, traversed)
    }
    traversed.add(chunk)
  }

  private def reduceCycles(Set<DagNode<T>> workingSet) {
    Set<DagNode<T>> cleanSet = new HashSet<DagNode<T>>()
    attempts:
    while (true) {
      Stack<DagNode<T>> stack = new Stack<DagNode<T>>()
      for (chunk in workingSet) {
        List<DagNode<T>> cycle = findCycle(chunk, stack, cleanSet)
        if (cycle != null) {
          workingSet << DagNode.join(cycle, nodeFactory)
          workingSet.removeAll(cycle)
          continue attempts
        }
      }

      break;
    }
  }

  private List<DagNode<T>> findCycle(DagNode<T> chunk, Stack<DagNode<T>> stack, Set<DagNode<T>> cleanSet) {
    if (cleanSet.contains(chunk)) return null

    stack.push(chunk)
    for (out in chunk.outs) {
      if (stack.contains(out)) {
        List<DagNode<T>> cycle = []
        while (true) {
          DagNode<T> ch = stack.pop()
          cycle << ch
          if (ch == out) {
            return cycle
          }
        }
      }

      List<DagNode<T>> cycle = findCycle(out, stack, cleanSet)
      if (cycle != null) return cycle
    }

    stack.pop()
    cleanSet << chunk

    return null
  }
}
