package andel.operation

import andel.rope.Metric
import andel.rope.Metrics
import andel.rope.Monoid
import andel.rope.Rope

typealias OpsRope = Rope<Array<Op>>

internal const val MAX_LEAF_SIZE = 16
internal const val DESIRED_LEAF_SIZE = MAX_LEAF_SIZE / 2 + MAX_LEAF_SIZE / 4

object OperationMonoid: Monoid<Array<Op>>(MAX_LEAF_SIZE) {

  val LenBefore: Metric = sumMetric()
  val LenAfter: Metric = sumMetric()
  val Count: Metric = sumMetric()

  override fun measure(data: Array<Op>): Metrics {
    return metrics(data).let { metrics ->
      Metrics(intArrayOf(metrics.lenBefore.toInt(), metrics.lenAfter.toInt(), metrics.count))
    }
  }

  override fun leafSize(leaf: Array<Op>): Int {
    return leaf.size
  }

  override fun merge(leafData1: Array<Op>, leafData2: Array<Op>): Array<Op> {
    return leafData1 + leafData2
  }

  override fun split(leaf: Array<Op>): List<Array<Op>> {
    val result = ArrayList<Array<Op>>((leaf.size + DESIRED_LEAF_SIZE - 1) / DESIRED_LEAF_SIZE)
    var index = 0
    while (index < leaf.size) {
      result.add(leaf.copyOfRange(index, (index + DESIRED_LEAF_SIZE).coerceAtMost(leaf.size)))
      index += DESIRED_LEAF_SIZE
    }
    return result
  }

  private fun metrics(ops: Array<Op>): OpsArrayMetrics {
    return ops.fold(OpsArrayMetrics(0, 0, 0)) { acc, op ->
      when (op) {
        is Op.Retain -> OpsArrayMetrics(acc.lenBefore + op.len, acc.lenAfter + op.len, acc.count + 1)
        is Op.Replace -> OpsArrayMetrics(acc.lenBefore + op.delete.length, acc.lenAfter + op.insert.length, acc.count + 1)
      }
    }
  }
}

private data class OpsArrayMetrics(val lenBefore: Long, val lenAfter: Long, val count: Int)
