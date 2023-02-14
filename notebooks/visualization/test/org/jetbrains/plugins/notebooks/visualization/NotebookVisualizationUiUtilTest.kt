package org.jetbrains.plugins.notebooks.visualization

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class NotebookVisualizationUiUtilTest {
  @Test
  fun `test mergeAndJoinIntersections`() {
    assertThat(mutableListOf<IntRange>().apply { mergeAndJoinIntersections(listOf()) }).isEmpty()
    assertThat(mutableListOf(1..2).apply { mergeAndJoinIntersections(listOf(5..6)) }).containsExactly(1..2, 5..6)
    assertThat(mutableListOf(1..2).apply { mergeAndJoinIntersections(listOf(3..4)) }).containsExactly(1..4)
    assertThat(mutableListOf(1..3).apply { mergeAndJoinIntersections(listOf(3..4)) }).containsExactly(1..4)
    assertThat(mutableListOf(1..3).apply { mergeAndJoinIntersections(listOf(2..4)) }).containsExactly(1..4)
    assertThat(mutableListOf(2..4).apply { mergeAndJoinIntersections(listOf(1..3)) }).containsExactly(1..4)
    assertThat(mutableListOf(1..2, 3..4, 7..8).apply { mergeAndJoinIntersections(listOf(5..6)) }).containsExactly(1..8)

    val cases: List<Triple<List<IntRange>, List<IntRange>, List<IntRange>>> = listOf(
      Triple(listOf(), listOf(), listOf()),
      Triple(listOf(1..2), listOf(5..6), listOf(1..2, 5..6)),
      Triple(listOf(1..2), listOf(3..4), listOf(1..4)),
      Triple(listOf(1..3), listOf(3..4), listOf(1..4)),
      Triple(listOf(1..3), listOf(2..4), listOf(1..4)),
      Triple(listOf(1..3), listOf(0..4), listOf(0..4)),
      Triple(listOf(1..2, 3..4, 7..8), listOf(5..6), listOf(1..8)),
    )

    for ((case1, case2, expected) in cases) {
      for ((left, right) in listOf(case1 to case2, case2 to case1)) {
        assertThat(left.toMutableList().apply { mergeAndJoinIntersections(right) })
          .describedAs("$left merge $right")
          .isEqualTo(expected)
      }
    }
  }
}