package org.jetbrains.jps.dag

/**
 * @author nik
 */
class DagNode<T> {
  Set<T> elements = new LinkedHashSet<T>()
  Set<DagNode<T>> outs = new LinkedHashSet<DagNode<T>>()
  Set<DagNode<T>> ins = new LinkedHashSet<DagNode<T>>()

  public static <T> DagNode<T> join(List<DagNode<T>> nodes, Closure nodeFactory) {
    DagNode<T> answer = nodeFactory();

    nodes.each { chunk ->
      answer.elements.addAll(chunk.elements)

      chunk.ins.each {
        it.outs.add(answer)
        it.outs.remove(chunk)
        answer.ins.add(it)
      }

      chunk.outs.each {
        it.ins.add(answer)
        it.ins.remove(chunk)
        answer.outs.add(it)
      }
    }

    answer.ins.removeAll(nodes)
    answer.outs.removeAll(nodes)

    answer.ins.remove(answer)
    answer.outs.remove(answer)

    return answer
  }

  def addOut(DagNode<T> node) {
    node.ins << this
    outs << node
  }
}
